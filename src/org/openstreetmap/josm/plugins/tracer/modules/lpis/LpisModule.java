/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import org.openstreetmap.josm.plugins.tracer.connectways.AngPolygonClipper;
import java.awt.Cursor;
import java.util.*;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationTask;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.*;

// import org.openstreetmap.josm.plugins.tracer.modules.lpis.LpisRecord;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;
import org.xml.sax.SAXException;

// #### TODO: zjistit proc diference s relaci 24399 vzdy vede k modifikaci multipolygonu, i kdyz trasovana cesta je od nej 
// nekolik kilometru daleko :-) Patrne chyby v geometrii vstupniho multipolygonu, s kterymi clip provadi buhvico...

public class LpisModule implements TracerModule  {

    private boolean moduleEnabled;

    private static final String source = "lpis";
    private static final String lpisUrl = "http://eagri.cz/public/app/wms/plpis_wfs.fcgi";
    private static final String reuseExistingLanduseNodePattern =
        "((landuse=* -landuse=no) | natural=scrub | natural=wood | natural=grassland | natural=wood | leisure=garden)";

    private static volatile Match m_reuseExistingLanduseNodeMatch;
    private static volatile Match m_clipLanduseWayMatch;
    private static volatile Match m_mergeLanduseWayMatch;

    static {
        try {
            m_reuseExistingLanduseNodeMatch = SearchCompiler.compile(reuseExistingLanduseNodePattern, false, false);
            m_clipLanduseWayMatch = m_reuseExistingLanduseNodeMatch; // use the same 
            m_mergeLanduseWayMatch = m_clipLanduseWayMatch; // use the same
        } 
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile pattern"));            
        }
    }

    public LpisModule(boolean enabled) {
        moduleEnabled = enabled;
    }

    @Override
    public void init() {
    }

    @Override
    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-lpis-sml");
    }

    @Override
    public String getName() {
        return tr("LPIS");
    }

    @Override
    public boolean moduleIsEnabled() {
        return moduleEnabled;
    }

    @Override
    public void setModuleIsEnabled(boolean enabled) {
        moduleEnabled = enabled;
    }

    @Override
    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new LpisTracerTask (pos, ctrl, alt, shift);
    }

    class LpisTracerTask extends PleaseWaitRunnable {

        private final LatLon m_pos;
        private final boolean m_ctrl;
        private final boolean m_alt;
        private final boolean m_shift;
        private final boolean m_performClipping;
        private final boolean m_performWayMerging;

        private LpisRecord m_record;
        private boolean m_cancelled;
        
        private final List<String> m_postTraceNotifications = new ArrayList<> ();

        LpisTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
            this.m_record = null;
            this.m_cancelled = false;

            this.m_performClipping = !m_ctrl;
            this.m_performWayMerging = !m_ctrl;
        }

        @Override
        protected void realRun() throws SAXException {

            System.out.println("");
            System.out.println("-----------------");
            System.out.println("----- Trace -----");
            System.out.println("-----------------");
            System.out.println("");

            progressMonitor.indeterminateSubTask(tr("Downloading LPIS data..."));
            try {
                LpisServer server = new LpisServer();
                m_record = server.getElementData(m_pos, lpisUrl);
            }
            catch (final Exception e) {
                e.printStackTrace();
                TracerUtils.showNotification(tr("LPIS download failed.") + "\n(" + m_pos.toDisplayString() + ")", "error");
                return;
            }

            if (m_cancelled)
                return;

            // No data available?
            if (m_record.getLpisID() == -1) {
                TracerUtils.showNotification(tr("Data not available.")+ "\n(" + m_pos.toDisplayString() + ")", "warning");
                return;
            }

            // Look for incomplete multipolygons that might participate in landuse clipping
            List<Relation> incomplete_multipolygons = null;
            if (m_performClipping)
                incomplete_multipolygons = getIncompleteMultipolygonsForDownload ();
        
            // No multipolygons to download, create traced polygon immediately within this task
            if (incomplete_multipolygons == null || incomplete_multipolygons.isEmpty()) {
                progressMonitor.subTask(tr("Creating LPIS polygon..."));
                createTracedPolygon();
            }
            else {
                // Schedule task to download incomplete multipolygons
                Main.worker.submit(new DownloadRelationTask(incomplete_multipolygons, Main.main.getEditLayer())); 

                // Schedule task to create traced polygon
                Main.worker.submit(new Runnable() {
                    @Override
                    public void run() {
                        createTracedPolygon();
                    }
                });
            }
        }
        
        /**
         * Returns list of all existing incomplete multipolygons that might participate in
         * LPIS polygon clipping. These relations must be downloaded first, clipping
         * doesn't support incomplete multipolygons.
         * @return List of incomplete multipolygon relations
         */
        private List<Relation> getIncompleteMultipolygonsForDownload() {
            DataSet ds = Main.main.getCurrentDataSet();
            ds.getReadLock().lock();
            try {
                List<Relation> list = new ArrayList<>();
                for (Relation rel : ds.searchRelations(m_record.getBBox())) {
                    if (!MultipolygonMatch.match(rel))
                        continue;
                    if (rel.isIncomplete() || rel.hasIncompleteMembers())
                        list.add(rel);
                }
                return list;
            } finally {
                ds.getReadLock().unlock();
            }
        }
        
        private void wayIsOutsideDownloadedAreaDialog() {
            ExtendedDialog ed = new ExtendedDialog(
                Main.parent, tr("Way is outside downloaded area"),
                new String[] {tr("Ok")});
            ed.setButtonIcons(new String[] {"ok"});
            ed.setIcon(JOptionPane.ERROR_MESSAGE);
            ed.setContent(tr("Sorry.\nThe traced way (or part of the way) is outside of the downloaded area.\nPlease download area around the way and try again."));
            ed.showDialog();
        }

        private void addPostTraceNotification(String s) {
            System.out.println("Notify: " + s);
            synchronized(m_postTraceNotifications) {
                m_postTraceNotifications.add(s);
            }
        }
        
        private void showPostTraceNotifications () {
            StringBuilder sb = new StringBuilder();
            synchronized(m_postTraceNotifications) {
                if (m_postTraceNotifications.isEmpty())
                    return;
                for (String s: m_postTraceNotifications) {
                    if (sb.length() != 0)
                        sb.append("\n");
                    sb.append(s);
                }
                m_postTraceNotifications.clear();
            }
            TracerUtils.showNotification(sb.toString(), "warning");
        }
        
        private void createTracedPolygon() {
            GuiHelper.runInEDT(new Runnable() {
                @Override
                public void run() {
                    long start_time = System.nanoTime();
                    DataSet data_set = Main.main.getCurrentDataSet();
                    data_set.beginUpdate();
                    try {
                        createTracedPolygonImpl (data_set);
                        showPostTraceNotifications();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                    finally {
                        data_set.endUpdate();
                        long end_time = System.nanoTime();
                        long time_msecs = (end_time - start_time) / (1000*1000);
                        System.out.println("Polygon time (ms): " + Long.toString(time_msecs));
                    }
                }
            });
        }
        
        private void createTracedPolygonImpl(DataSet data_set) {

            System.out.println("  LPIS ID: " + m_record.getLpisID());
            System.out.println("  LPIS usage: " + m_record.getUsage());

            GeomUtils geom = new GeomUtils();
            WayEditor editor = new WayEditor (data_set, geom);

            // Create outer way
            List<EdNode> outer_nodes = new ArrayList<> ();
            LatLon prev_coor = null;
            // m_record.getCoorCount() - 1 - omit last node
            for (int i = 0; i < m_record.getOuter().size() - 1; i++) {
                EdNode node = editor.newNode(m_record.getOuter().get(i));

                if (!editor.insideDataSourceBounds(node)) {
                    wayIsOutsideDownloadedAreaDialog();
                    return;
                }

                if (!geom.duplicateNodes(node.getCoor(), prev_coor)) {
                    outer_nodes.add(node);
                    prev_coor = node.getCoor();
                }
            }

            // close way
            if (outer_nodes.size() > 0)
                outer_nodes.add(outer_nodes.get(0));
            EdWay outer_way = editor.newWay(outer_nodes);

            IEdNodePredicate reuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            outer_way.reuseExistingNodes(reuse_filter);

            if (!m_record.hasInners())
                tagOuterWay(outer_way);

            // Create multipolygon?
            EdMultipolygon multipolygon = null;
            if (m_record.hasInners()) {
                multipolygon = editor.newMultipolygon();
                tagMultipolygon(multipolygon);
                multipolygon.addOuterWay(outer_way);

                for (List<LatLon> in: m_record.getInners()) {
                    List<EdNode> inner_nodes = new ArrayList<>(in.size());
                    prev_coor = null;
                    for (int j = 0; j < in.size() - 1; j++) {
                        EdNode node = editor.newNode(in.get(j));
                        if (!geom.duplicateNodes(node.getCoor(), prev_coor)) {
                            inner_nodes.add(node);
                            prev_coor = node.getCoor();
                        }
                    }

                    // close way
                    if (!inner_nodes.isEmpty())
                        inner_nodes.add(inner_nodes.get(0));
                    EdWay way = editor.newWay(inner_nodes);

                    way.reuseExistingNodes(reuse_filter);

                    multipolygon.addInnerWay(way);
                }
            }

            // Connect to touching nodes of near landuse polygons            
            if (multipolygon == null)
                connectExistingTouchingNodesSimple(editor, outer_way);
            else
                connectExistingTouchingNodesMulti(editor, multipolygon);

            // Clip other ways
            if (m_performClipping) {
                // #### Now, it clips using only the outer way. Consider if multipolygon clip is necessary/useful.
                clipLanduseAreasSimpleClip(editor, outer_way);
            }
            
            // Merge duplicate ways
            // (Note that outer way can be merged to another way too, so we must watch it.
            // Otherwise, outer_way variable would refer to an unused way.)
            if (m_performWayMerging) {
                AreaPredicate merge_filter = new AreaPredicate (m_mergeLanduseWayMatch);
                MergeIdenticalWays merger = new MergeIdenticalWays(editor, merge_filter);
                outer_way = merger.mergeWays(editor.getModifiedWays(), true, outer_way);
            }

            List<Command> commands = editor.finalizeEdit();

            if (!commands.isEmpty()) {

                long start_time = System.nanoTime();                
                
                Main.main.undoRedo.add(new SequenceCommand(tr("Trace object"), commands));

                OsmPrimitive sel = (multipolygon != null ?
                    multipolygon.finalMultipolygon() : outer_way.finalWay());

                if (m_shift) {
                    editor.getDataSet().addSelected(sel);
                } else {
                    editor.getDataSet().setSelected(sel);
                }
                long end_time = System.nanoTime();
                long time_msecs = (end_time - start_time) / (1000*1000);
                System.out.println("undoRedo time (ms): " + Long.toString(time_msecs));                
                
            } else {
                addPostTraceNotification(tr("Nothing changed."));
            }
        }

        @Override
        protected void finish() {
        }        
        
        @Override
        protected void cancel() {
            m_cancelled = true;
            // #### TODO: break the connection to remote LPIS server
        }

        private void tagMultipolygon (EdMultipolygon multipolygon) {
            Map <String, String> map = new HashMap <> (m_record.getUsageOsm());
            map.put("source", source);
            map.put("ref", Long.toString(m_record.getLpisID()));
            multipolygon.setKeys(map);
        }

        private void tagOuterWay (EdWay way) {
            Map <String, String> map = new HashMap <> (m_record.getUsageOsm());
            map.put("source", source);
            map.put("ref", Long.toString(m_record.getLpisID()));
            way.setKeys(map);
        }

        private void clipLanduseAreasSimpleClip(WayEditor editor, EdWay clip_way) {

            AreaPredicate filter = new AreaPredicate (m_clipLanduseWayMatch);

            Set<EdObject> areas = editor.useAllAreasInBBox(clip_way.getBBox(), filter);
            for (EdObject obj: areas) {
                if (obj instanceof EdMultipolygon) {
                    EdMultipolygon subject_mp = (EdMultipolygon)obj;
                    if (subject_mp.containsWay(clip_way))
                        continue;
                    
                    // Perform clipping
                    clipLanduseAreaSimpleMulti(editor, clip_way, subject_mp);
                }
                else if (obj instanceof EdWay) {
                    EdWay subject_way = (EdWay)obj;
                    if (subject_way == clip_way)
                        continue;

                    // Perform clipping
                    clipLanduseAreaSimpleSimple(editor, clip_way, subject_way);
                }
            }
        }
        
        private void clipLanduseAreaSimpleSimple(WayEditor editor, EdWay clip_way, EdWay subject_way) {            
            // First, connect touching nodes of subject_way to clip_way. This is necessary because
            // LPIS polygons series contain very small gaps that need to be elliminated before
            // clipping is performed. Also, there are false joint points on LPIS polygons' edges
            // where nodes must be added too.
            subject_way.connectNonIncludedTouchingNodes(clip_way);

            System.out.println("Computing difference: clip_way=" + Long.toString(clip_way.getUniqueId()) + ", subject_way=" + Long.toString(subject_way.getUniqueId()));
            
            AngPolygonClipper clipper = new AngPolygonClipper(editor);
            clipper.polygonDifference(clip_way, subject_way);
            List<List<EdNode>> outers = clipper.outerPolygons();
            List<List<EdNode>> inners = clipper.innerPolygons();

            System.out.println("- result: outers=" + Long.toString(outers.size()) + ", inners=" + Long.toString(inners.size()));

            if (outers.isEmpty() && inners.isEmpty()) {
                addPostTraceNotification(String.format(tr("Simple way %d would be completely removed, ignoring, please check."), subject_way.getUniqueId()));
            }
            else if (outers.size() == 1 && inners.isEmpty())
                clipLanduseHandleSimpleSimpleSimple(editor, clip_way, subject_way, outers.get(0));
            else if ((outers.size() + inners.size()) > 1)
                clipLanduseHandleSimpleSimpleMulti(editor, clip_way, subject_way, outers, inners);
            else 
                throw new AssertionError(tr("PolygonClipper.polygonDifference returned nonsense!"));
        }

        private void clipLanduseAreaSimpleMulti(WayEditor editor, EdWay clip_way, EdMultipolygon subject_mp) {
            
            boolean subject_has_nonclosed_ways = subject_mp.containsNonClosedWays();
            
            // #### add support for multipolygons with non-closed ways
            if (subject_has_nonclosed_ways) {
                addPostTraceNotification(String.format(tr("Ignoring multipolygon %d, it contains non-closed ways."), subject_mp.getUniqueId()));
                return;
            }
            
            // First, connect all touching nodes of all subject ways to clip_way. This is necessary because
            // LPIS polygons series contain very small gaps that need to be elliminated before
            // clipping is performed. Also, there are false joint points on LPIS polygons' edges
            // where nodes must be added too.
            for (EdWay way: subject_mp.allWays())
                way.connectNonIncludedTouchingNodes(clip_way);
            
            System.out.println("Computing difference: clip_way=" + Long.toString(clip_way.getUniqueId()) + ", subject_relation=" + Long.toString(subject_mp.getUniqueId()));

            AngPolygonClipper clipper = new AngPolygonClipper(editor);
            clipper.polygonDifference(clip_way, subject_mp);
            List<List<EdNode>> unmapped_new_outers = new ArrayList<>(clipper.outerPolygons());
            List<List<EdNode>> unmapped_new_inners = new ArrayList<>(clipper.innerPolygons());

            System.out.println("- result: outers=" + Long.toString(unmapped_new_outers.size()) + ", inners=" + Long.toString(unmapped_new_inners.size()));

            // Whole multipolygon disappeared
            if (unmapped_new_outers.isEmpty() && unmapped_new_inners.isEmpty()) {
                addPostTraceNotification(String.format(tr("Multipolygon %d would be completely removed, ignoring, please check."), subject_mp.getUniqueId()));
                return;
            }
            
            // Create bi-directional mapping of identical geometries
            List<EdWay> unmapped_old_outers = new ArrayList<>(subject_mp.outerWays());
            List<EdWay> unmapped_old_inners = new ArrayList<>(subject_mp.innerWays());
            Map<EdWay, List<EdNode>> mapped_old_new_outers = new HashMap<>();
            Map<List<EdNode>, EdWay> mapped_new_old_outers = new HashMap<>();
            Map<EdWay, List<EdNode>> mapped_old_new_inners = new HashMap<>();
            Map<List<EdNode>, EdWay> mapped_new_old_inners = new HashMap<>();
            mapIdenticalWays(unmapped_old_outers, unmapped_new_outers, mapped_old_new_outers, mapped_new_old_outers);
            mapIdenticalWays(unmapped_old_inners, unmapped_new_inners, mapped_old_new_inners, mapped_new_old_inners);

            // All new ways were successfully mapped to old ways?
            if (unmapped_old_outers.isEmpty() && unmapped_old_inners.isEmpty() && unmapped_new_outers.isEmpty() && unmapped_new_inners.isEmpty()) {
                System.out.println(tr(" o subject unchanged"));
                return;
            }

            System.out.println("- unmapped_outers: old=" + Long.toString(unmapped_old_outers.size()) + ", new=" + Long.toString(unmapped_new_outers.size()));
            System.out.println("- unmapped_inners: old=" + Long.toString(unmapped_old_inners.size()) + ", new=" + Long.toString(unmapped_new_inners.size()));
            
            // Handle the easiest and most common case, only one outer way of a multipolygon was clipped
            // (Maybe, I should test that the old and new outer ways have non-empty intersection. Otherwise,
            // it means that one brand new outer way was created and one old outer way was completely deleted.
            // But as long as clip is a simple way, it should never happen, because new way can be created only
            // by clipping another existing way.)
            if (!subject_has_nonclosed_ways && unmapped_old_outers.size() == 1 && unmapped_new_outers.size() == 1 &&
                    unmapped_old_inners.isEmpty() && unmapped_new_inners.isEmpty()) {
                EdWay old_outer_way = unmapped_old_outers.get(0);
                List<EdNode> new_outer_way = unmapped_new_outers.get(0);                
                clipLanduseHandleSimpleMultiOneOuterModified (editor, clip_way, subject_mp, old_outer_way, new_outer_way);
                return;
            }
            
            // If modified ways have no (interesting) tags and no other referrers, we can 
            // replace multipolygon's geometry quite agressively.
            if (!subject_has_nonclosed_ways && 
                    untaggedSingleReferrerWays(unmapped_old_outers, subject_mp) && 
                    untaggedSingleReferrerWays(unmapped_old_inners, subject_mp)) {
                clipLanduseHandleSimpleMultiAgressiveUpdate(editor, clip_way, subject_mp, 
                    unmapped_old_outers, unmapped_old_inners, unmapped_new_outers, unmapped_new_inners);
                return;
            }
            
            addPostTraceNotification(String.format(tr("Multipolygon clipping result of %d is too complex."), subject_mp.getUniqueId()));
        }

        /**
         * Returns true if all ways in the list have no (interesting) tag,
         * have the given multipolygon referrer and no other referrers.
         * @param list list of ways to check
         * @param referrer expected single referrer
         * @return true if all ways satisfy the requirements
         */
        private boolean untaggedSingleReferrerWays(List<EdWay> list, EdMultipolygon referrer) {
            for (EdWay way: list) {
                if (way.isTagged())
                    return false;
                if (!way.hasSingleReferrer(referrer))
                    return false;
            }
            return true;
        }
        
        private void mapIdenticalWays(List<EdWay> unmapped_old, List<List<EdNode>> unmapped_new, Map<EdWay, List<EdNode>> mapped_old_new, Map<List<EdNode>, EdWay> mapped_new_old) {
            int iold = 0;
            while (iold < unmapped_old.size()) {
                EdWay old_way = unmapped_old.get(iold);
                List<EdNode> new_way = null;
                int inew = 0;
                for (inew = 0; inew < unmapped_new.size(); inew++) {
                    List<EdNode> test_way = unmapped_new.get(inew);
                    if (old_way.hasIdenticalEdNodeGeometry(test_way, true)) {
                        new_way = test_way;
                        break;
                    }
                }
                if (new_way != null) {
                    mapped_old_new.put(old_way, new_way);
                    mapped_new_old.put(new_way, old_way);
                    unmapped_old.remove(iold);
                    unmapped_new.remove(inew);
                }
                else {
                    iold++;
                }
            }
        }
        
        class SimilarWaysPair implements Comparable<SimilarWaysPair> {
            public final EdWay src;
            public final List<EdNode> dst;
            public final double similarity;
            
            public SimilarWaysPair(EdWay s, List<EdNode> d, double sim) {
                src = s;
                dst = d;
                similarity = sim;
            }

            @Override
            public int compareTo(SimilarWaysPair t) {
                int x = -Double.compare(this.similarity, t.similarity);
                if (x != 0)
                    return x;
                return -Integer.compare(this.src.getNodesCount(), t.src.getNodesCount());
            }
        }
        
        /**
         * For every source EdWay, it tries to find the most similar dest way;
         * for every dest way, it tries to find the most similar source EdWay.
         * Ways with no suitable similar ways are excluded from the results.
         * Forward mapping is guaranteed to be a bijection (suitable for
         * way updates), reverse mapping can contain duplicate values (surjection).
         * 
         * @param srcs list of source EdWays
         * @param dsts list of dest ways
         * @return a pair, first element is a map of source EdWays to most similar
         * dest ways, second element is a map of dest ways to most similar source EdWay
         */
        private Pair<Map<EdWay, List<EdNode>>, Map<List<EdNode>, EdWay>> pairSimilarWays (List<EdWay> srcs, List<List<EdNode>> dsts) {
            // Create Cartesian product of ways and sort pairs according to their similarity
            // (For simplicity, we estimate similarity from percentage of shared nodes.
            // It would be better to base the similarity on areas of polygon intersections.)
            PriorityQueue<SimilarWaysPair> queue = new PriorityQueue<>();
            for(EdWay w: srcs) {
                for (List<EdNode> nw: dsts) {
                    int shared_nodes = w.getSharedNodesCount(nw);
                    if (shared_nodes > 0) {
                        double similarity = shared_nodes / w.getNodesCount();
                        queue.add(new SimilarWaysPair(w, nw, similarity));
                    }
                }
            }

            // Map ways...
            Map<EdWay, List<EdNode>> res1 = new HashMap<>();
            Map<List<EdNode>, EdWay> res2 = new HashMap<>();
            Set<List<EdNode>> res1used = new HashSet<>();
            SimilarWaysPair swp;
            while ((swp = queue.poll()) != null) {
                
                // forward mapping
                if (!res1.containsKey(swp.src) && !res1used.contains(swp.dst)) {
                    res1.put(swp.src, swp.dst);
                    res1used.add(swp.dst);
                }
                
                // reverse mapping
                if (!res2.containsKey(swp.dst)) {
                    res2.put(swp.dst, swp.src);
                }
            }
            
            return new Pair<>(res1, res2);
        }
        
        private void clipLanduseHandleSimpleSimpleSimple(WayEditor editor, EdWay clip_way, EdWay subject_way, List<EdNode> result) {
            // ** Easiest case - simple way clipped by a simple way produced a single polygon **

            System.out.println(tr("Clip result: simple"));

            // Subject way unchanged?
            if (subject_way.hasIdenticalEdNodeGeometry(result, true)) {
                System.out.println(tr(" o subject unchanged"));
                return;
            }

            System.out.println(tr(" ! CLIPPING subject " + Long.toString(subject_way.getUniqueId())));

            // Subject way changed, change its geometry
            subject_way.setNodes(result);

            // Connect clip_way to subject_way, this step guarantees that all newly created
            // intersection nodes will be included in both ways.
            clip_way.connectNonIncludedTouchingNodes(subject_way);
        }
        
        private void clipLanduseHandleSimpleMultiOneOuterModified (WayEditor editor, EdWay clip_way, EdMultipolygon subject_mp, EdWay old_outer_way, List<EdNode> result) {
            // ** Easy case - clip of a multipolygon modified exactly one outer way and nothing else **
            
            System.out.println(tr(" ! CLIPPING subject " + Long.toString(subject_mp.getUniqueId())) + ", outer way modified: " + Long.toString(old_outer_way.getUniqueId()));
            
            // Change geometry of the changed outer way
            old_outer_way.setNodes(result);

            // Connect clip_way to subject_way, this step guarantees that all newly created
            // intersection nodes will be included in both ways.
            clip_way.connectNonIncludedTouchingNodes(old_outer_way);            
        }
        
        private void clipLanduseHandleSimpleSimpleMulti(WayEditor editor, EdWay clip_way, EdWay subject_way, List<List<EdNode>> outers, List<List<EdNode>> inners) {        
            // ** Simple way clipped by a simple way produced multiple polygons **

            if (inners.isEmpty()) {
                System.out.println(tr("Clip result: multi outers"));
                clipLanduseHandleSimpleSimpleMultiOuters(editor, clip_way, subject_way, outers);
            }
            else {
                System.out.println(tr("Clip result: multi mixed"));
                // #### not completed
                addPostTraceNotification(String.format(tr("Clipping changes simple way %d to multipolygon, not supported yet."), subject_way.getUniqueId()));
            }
        }

        private void clipLanduseHandleSimpleSimpleMultiOuters(WayEditor editor, EdWay clip_way, EdWay subject_way, List<List<EdNode>> outers) {
            // ** Simple subject clipped by a simple way produced multiple simple (outer) polygons **

            // Don't clip subject which is a member of a (non-multipolygon) relation.
            // It's questionable if all pieces should be added to the relation, or only some of them, etc...
            if (subject_way.hasEditorReferrers() || subject_way.hasExternalReferrers()) {
                addPostTraceNotification(String.format(tr("Clipped way %d is a member of non-multipolygon relation, not supported yet."), subject_way.getUniqueId()));
                return;
            }

            System.out.println(tr(" ! CLIPPING subject " + Long.toString(subject_way.getUniqueId()) + " to multiple simple ways"));

            // #### Generally, it's better to create multiple simple ways than combine them to a new multipolygon.
            // But in some cases, maybe it would make sense to create a multipolygon... E.g. named landuse areas??

            // find the largest polygon, which will be used to update subject_way's geometry
            List<EdNode> maxnodes = null;
            double maxarea = Double.NEGATIVE_INFINITY;
            for (List<EdNode> nodes: outers) {
                double area = getClosedWayArea(nodes);
                if (area < maxarea)
                    continue;
                maxnodes = nodes;
                maxarea = area;
            }

            // update subject_way geometry and create new ways for the other pieces
            for (List<EdNode> nodes: outers) {
                if (nodes == maxnodes) {
                    subject_way.setNodes(nodes);
                    clip_way.connectNonIncludedTouchingNodes(subject_way);
                }
                else {
                    EdWay new_way = editor.newWay(nodes);
                    new_way.setKeys(subject_way.getKeys());
                    clip_way.connectNonIncludedTouchingNodes(new_way);
                }
            }
        }        
        
        private void clipLanduseHandleSimpleMultiAgressiveUpdate(WayEditor editor, EdWay clip_way, EdMultipolygon subject_mp, List<EdWay> unmapped_old_outers, List<EdWay> unmapped_old_inners, List<List<EdNode>> unmapped_new_outers, List<List<EdNode>> unmapped_new_inners) {
            // ** Agressive update of a multipolygon clipped by a simple way

            if (!unmapped_old_outers.isEmpty() || !unmapped_new_outers.isEmpty()) {
                
                // Update geometry of outer ways that can be paired together as similar ones
                Pair<Map<EdWay, List<EdNode>>, Map<List<EdNode>, EdWay>> outer_pair_maps = pairSimilarWays (unmapped_old_outers, unmapped_new_outers);
                Map<EdWay, List<EdNode>> outer_pairs = outer_pair_maps.a;
                Map<List<EdNode>, EdWay> outer_revs = outer_pair_maps.b;
                for (Map.Entry<EdWay, List<EdNode>> pair: outer_pairs.entrySet()) {
                    EdWay old_way = pair.getKey();
                    List<EdNode> new_nodes = pair.getValue();
                    old_way.setNodes(new_nodes);
                    clip_way.connectNonIncludedTouchingNodes(old_way);
                    unmapped_old_outers.remove(old_way);
                    unmapped_new_outers.remove(new_nodes);
                    System.out.println("Changing outer geometry " + Long.toString(old_way.getUniqueId()));
                }

                // Create new outer ways with tagging based on reverse similarity mapping
                // (If no reverse mapping is available, leave way untagged)
                for (List<EdNode> new_nodes: unmapped_new_outers) {
                    EdWay new_way = editor.newWay(new_nodes);
                    EdWay old_way = outer_revs.get(new_nodes);
                    if (old_way != null)
                        new_way.setKeys(old_way.getKeys());
                    clip_way.connectNonIncludedTouchingNodes(new_way);
                    subject_mp.addOuterWay(new_way);
                    System.out.println("Adding outer way " + Long.toString(new_way.getUniqueId()));
                }

                // Remove old outer ways that weren't mapped to new ways
                // (We assume that the ways have no interesting tags and referrers, 
                // and will be automatically deleted by WayEditor.)
                for (EdWay old_way: unmapped_old_outers) {
                    subject_mp.removeOuterWay(old_way);
                    System.out.println("Removing outer way " + Long.toString(old_way.getUniqueId()));
                }
            }
            
            if (!unmapped_old_inners.isEmpty() || !unmapped_new_inners.isEmpty()) {
                
                // Update geometry of inner ways that can be paired together as similar ones
                Pair<Map<EdWay, List<EdNode>>, Map<List<EdNode>, EdWay>> inner_pair_maps = pairSimilarWays (unmapped_old_inners, unmapped_new_inners);
                Map<EdWay, List<EdNode>> inner_pairs = inner_pair_maps.a;
                Map<List<EdNode>, EdWay> inner_revs = inner_pair_maps.b;
                for (Map.Entry<EdWay, List<EdNode>> pair: inner_pairs.entrySet()) {
                    EdWay old_way = pair.getKey();
                    List<EdNode> new_nodes = pair.getValue();
                    old_way.setNodes(new_nodes);
                    clip_way.connectNonIncludedTouchingNodes(old_way);
                    unmapped_old_inners.remove(old_way);
                    unmapped_new_inners.remove(new_nodes);
                    System.out.println("Changing inner geometry " + Long.toString(old_way.getUniqueId()));
                }
                
                // Create new inner ways
                for (List<EdNode> new_nodes: unmapped_new_inners) {
                    // use tagging of the most similar reverse mapped way, if any
                    EdWay new_way = editor.newWay(new_nodes);
                    EdWay old_way = inner_revs.get(new_nodes);
                    if (old_way != null)
                        new_way.setKeys(old_way.getKeys());
                    clip_way.connectNonIncludedTouchingNodes(new_way);
                    subject_mp.addInnerWay(new_way);
                    System.out.println("Adding inner way " + Long.toString(new_way.getUniqueId()));
                }

                // Remove old inner ways that weren't mapped to new ways
                // (We assume that the ways have no interesting tags and referrers, 
                // and will be automatically deleted by WayEditor.)
                for (EdWay old_way: unmapped_old_inners) {
                    subject_mp.removeInnerWay(old_way);
                    System.out.println("Removing inner way " + Long.toString(old_way.getUniqueId()));
                }                
            }
        }
        
        
        private double getClosedWayArea(List<EdNode> nodes) {
            // Joseph O'Rourke, Computational Geometry in C, copied from GPCJ2

            if (nodes.size() < 4)
                return 0;

            double area = 0;
            EastNorth a = nodes.get(0).getEastNorth();

            for (int i = 1; i < nodes.size() - 2; i++) {
                EastNorth b = nodes.get(i).getEastNorth();
                EastNorth c = nodes.get(i+1).getEastNorth();
                double t = ((c.getX() - b.getX()) * (a.getY() - b.getY())) - ((a.getX() - b.getX()) * (c.getY() - b.getY()));
                area += t;
            }

            return Math.abs(area) / 2;
        }


        private void connectExistingTouchingNodesMulti(WayEditor editor, EdMultipolygon multipolygon) {
            // Setup filters - include landuse nodes only, exclude all nodes of the multipolygon itself
            IEdNodePredicate landuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(multipolygon);
            IEdNodePredicate filter = new EdNodeLogicalAndPredicate (exclude_my_nodes, landuse_filter);

            // Connect all outer ways
            List<EdWay> outer_ways = multipolygon.outerWays();
            for (EdWay way: outer_ways)
                way.connectExistingTouchingNodes(filter);

            // Connect all inner ways
            List<EdWay> inner_ways = multipolygon.innerWays();
            for (EdWay way: inner_ways)
                way.connectExistingTouchingNodes(filter);
        }

        private void connectExistingTouchingNodesSimple(WayEditor editor, EdWay way) {
            // Setup filters - include landuse nodes only, exclude all nodes of the way itself
            IEdNodePredicate landuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(way);
            IEdNodePredicate filter = new EdNodeLogicalAndPredicate (exclude_my_nodes, landuse_filter);

            // Connect nodes
            way.connectExistingTouchingNodes(filter);
        }
    }
}

