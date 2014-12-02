/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral, Martin Svec
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
package org.openstreetmap.josm.plugins.tracer.modules.ruian;

import java.awt.Cursor;
import java.util.*;

import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationTask;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.tracer.PostTraceNotifications;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.*;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;
import org.xml.sax.SAXException;

public class RuianModule implements TracerModule {

    protected boolean cancel;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;
    private boolean moduleEnabled;

    private final String source = "cuzk:ruian"; // obsolete
    private final String ruianUrl = "http://josm.poloha.net";

    private static final double resurrectNodesDistanceMeters = 10.0;

    private static final GeomDeviation m_connectTolerance = new GeomDeviation (0.15, Math.PI / 50);
    private static final double m_discardCutoffsPercent = 15.0;
    private final ClipAreasSettings m_clipSettings =
        new ClipAreasSettings (m_connectTolerance, m_discardCutoffsPercent, new DiscardableBuildingCutoff());

    private static final String reuseExistingBuildingNodePattern =
        "(building=* -building=no -building=entrance)";

    private static final String retraceAreaPattern =
        "(building=* -building=no -building=entrance)";

    private static final String ruianSourcePattern =
        "(source=\"cuzk:ruian\")";

    private static final Match m_reuseExistingBuildingNodeMatch;
    private static final Match m_clipBuildingWayMatch;
    private static final Match m_mergeBuildingWayMatch;
    private static final Match m_retraceAreaMatch;
    private static final Match m_ruianSourceMatch;

    static {
        try {
            m_reuseExistingBuildingNodeMatch = SearchCompiler.compile(reuseExistingBuildingNodePattern, false, false);
            m_clipBuildingWayMatch = m_reuseExistingBuildingNodeMatch; // use the same
            m_mergeBuildingWayMatch = m_clipBuildingWayMatch; // use the same
            m_retraceAreaMatch = SearchCompiler.compile(retraceAreaPattern, false, false);
            m_ruianSourceMatch = SearchCompiler.compile(ruianSourcePattern, false, false);
        }
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile pattern"));
        }
    }

    public RuianModule(boolean enabled) {
        moduleEnabled = enabled;
    }

    @Override
    public void init() {
    }

    @Override
    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-ruian-sml");
    }

    @Override
    public String getName() {
        return tr("RUIAN");
    }

    @Override
    public boolean moduleIsEnabled() {
        return moduleEnabled;
    };

    @Override
    public void setModuleIsEnabled(boolean enabled) {
        moduleEnabled = enabled;
    };

    @Override
    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new RuianTracerTask (pos, ctrl, alt, shift);
    }

    class DiscardableBuildingCutoff implements IDiscardableCutoffPredicate {

        @Override
        public boolean canSilentlyDiscard(EdWay way, double cutoffs_percent) {

            if (cutoffs_percent > 30.0)
                return false;

            // can silently discard given building as a result of clipping/cutoff removal?
            Map<String, String> keys = way.getInterestingKeys();
            for (Map.Entry<String, String> entry: keys.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if (k != null && v != null && !(k.equals("building") && v.equals("yes")))
                    return false;
            }
            return true;
        }
    }

    class ReuseBuildingNearNodes implements IReuseNearNodePredicate {

        // distance tolerancies are in meters
        private final double m_reuseNearNodesToleranceDefault = m_connectTolerance.distanceMeters();
        private final double m_reuseNearNodesToleranceNonRuian = 0.30;
        private final double m_reuseNearNodesToleranceRetracedNodes = 0.40;

        private final double m_lookupDistanceMeters;
        private final ReuseNearNodeMethod m_reuseMethod = ReuseNearNodeMethod.moveAndReuseNode;
        private final AreaBoundaryWayNodePredicate m_ruianArea = new AreaBoundaryWayNodePredicate(m_ruianSourceMatch);

        private final Set<EdNode> m_retracedNodes;

        ReuseBuildingNearNodes (Set<EdNode> retraced_nodes) {
            m_retracedNodes = retraced_nodes;
            m_lookupDistanceMeters = Math.max (Math.max(m_reuseNearNodesToleranceDefault, m_reuseNearNodesToleranceRetracedNodes), m_reuseNearNodesToleranceNonRuian);
        }

        @Override
        public ReuseNearNodeMethod reuseNearNode(EdNode node, EdNode near_node, double distance_meters) {

            boolean retraced = m_retracedNodes != null && m_retracedNodes.contains(near_node);
            boolean ruian = m_ruianArea.evaluate(near_node);

            // be more tolerant for nodes occurring in retraced building
            if (retraced && !ruian) {
                System.out.println("RNN: retraced, dist=" + Double.toString(distance_meters));
                if (distance_meters <= m_reuseNearNodesToleranceRetracedNodes)
                    return m_reuseMethod;
            }

            // be more tolerant for non-ruian buildings
            if (!ruian) {
                System.out.println("RNN: non-ruian, dist=" + Double.toString(distance_meters));
                if (distance_meters <= m_reuseNearNodesToleranceNonRuian)
                    return m_reuseMethod;
            }

            // use default tolerance for others
                System.out.println("RNN: default, dist=" + Double.toString(distance_meters));
            if (distance_meters <= m_reuseNearNodesToleranceDefault)
                return m_reuseMethod;

            return ReuseNearNodeMethod.dontReuseNode;
        }

        @Override
        public double lookupDistanceMeters() {
            return m_lookupDistanceMeters;
        }
    }


    class RuianTracerTask extends PleaseWaitRunnable {

        private final LatLon m_pos;
        private final boolean m_ctrl;
        private final boolean m_alt;
        private final boolean m_shift;
        private final boolean m_performClipping;
        private final boolean m_performWayMerging;
        private final boolean m_performRetrace;
        private final boolean m_performNearBuildingsEdit;

        private RuianRecord m_record;
        private boolean m_cancelled;

        private final PostTraceNotifications m_postTraceNotifications = new PostTraceNotifications();

        RuianTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
            this.m_record = null;
            this.m_cancelled = false;

            this.m_performClipping = !m_ctrl;
            this.m_performWayMerging = !m_ctrl;
            this.m_performRetrace = !m_ctrl;
            this.m_performNearBuildingsEdit = !m_ctrl;
        }

        @Override
        @SuppressWarnings({"CallToPrintStackTrace", "UseSpecificCatch", "BroadCatchBlock", "TooBroadCatch"})
        protected void realRun() throws SAXException {

            TracerPreferences pref = TracerPreferences.getInstance();

            String sUrl = ruianUrl;

            if (pref.isCustomRuainUrlEnabled())
              sUrl = pref.getCustomRuainUrl();

            System.out.println("");
            System.out.println("-----------------");
            System.out.println("----- Trace -----");
            System.out.println("-----------------");
            System.out.println("");

            progressMonitor.indeterminateSubTask(tr("Downloading RUIAN building data..."));
            try {
                RuianServer server = new RuianServer();
                m_record = server.trace(m_pos, sUrl);
            }
            catch (final Exception e) {
                e.printStackTrace();
                TracerUtils.showNotification(tr("RUIAN download failed.") + "\n(" + m_pos.toDisplayString() + ")", "error");
                return;
            }

            if (m_cancelled)
                return;

            // No data available?
            if (m_record.getBuildingID() == -1) {
                TracerUtils.showNotification(tr("Data not available.")+ "\n(" + m_pos.toDisplayString() + ")", "warning");
                return;
            }

            // Look for incomplete multipolygons that might participate in clipping
            List<Relation> incomplete_multipolygons = null;
            if (m_performClipping)
                incomplete_multipolygons = getIncompleteMultipolygonsForDownload ();

            // No multipolygons to download, create traced polygon immediately within this task
            if (incomplete_multipolygons == null || incomplete_multipolygons.isEmpty()) {
                progressMonitor.subTask(tr("Creating RUIAN polygon..."));
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
         * building polygon clipping. These relations must be downloaded first, clipping
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

        private void createTracedPolygon() {
            GuiHelper.runInEDT(new Runnable() {
                @Override
                @SuppressWarnings("CallToPrintStackTrace")
                public void run() {
                    long start_time = System.nanoTime();
                    DataSet data_set = Main.main.getCurrentDataSet();
                    data_set.beginUpdate();
                    try {
                        createTracedPolygonImpl (data_set);
                        m_postTraceNotifications.show();
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

            System.out.println("  RUIAN keys: " + m_record.getKeys(m_alt));

            WayEditor editor = new WayEditor (data_set);

            // Look for object to retrace
            EdObject retrace_object = null;
            if (m_performRetrace) {
                Pair<EdObject, Boolean> repl = getObjectToRetrace(editor, m_pos, m_retraceAreaMatch);
                retrace_object = repl.a;
                boolean ambiguous_retrace = repl.b;

                if (ambiguous_retrace) {
                    m_postTraceNotifications.add(tr("Multiple existing Ruian building polygons found, retrace is not possible."));
                    return;
                }
            }

            Set<EdNode> retrace_nodes = null;
            if (retrace_object != null)
                retrace_nodes = retrace_object.getAllNodes();

            // Create traced object
            Pair<EdWay, EdMultipolygon> trobj = this.createTracedEdObject(editor);
            if (trobj == null)
                return;
            EdWay outer_way = trobj.a;
            EdMultipolygon multipolygon = trobj.b;

            // Connect to near building polygons
            // (must be done before retrace updates, we want to use as much old nodes as possible)
            if (!m_performNearBuildingsEdit) {
                reuseExistingNodes(multipolygon == null ? outer_way : multipolygon);
            }
            else {
                reuseNearNodes(multipolygon == null ? outer_way : multipolygon, retrace_nodes);
                connectExistingTouchingNodes(multipolygon == null ? outer_way : multipolygon);
            }

            // Retrace simple ways - just use the old way
            if (retrace_object != null) {
                Pair<EdWay, EdMultipolygon> reobj = this.updateRetracedObjects(multipolygon == null ? outer_way : multipolygon, retrace_object);
                if (reobj == null)
                    return;
                outer_way = reobj.a;
                multipolygon = reobj.b;
            }

            // Tag object
            tagTracedObject(multipolygon == null ? outer_way : multipolygon);

            // Clip other areas
            if (m_performClipping) {
                // #### Now, it clips using only the outer way. Consider if multipolygon clip is necessary/useful.
                AreaPredicate filter = new AreaPredicate (m_clipBuildingWayMatch);
                ClipAreas clip = new ClipAreas(editor, m_clipSettings, m_postTraceNotifications);
                clip.clipAreas(outer_way, filter);

                // Remove needless nodes
                AreaPredicate remove_filter = new AreaPredicate (m_clipBuildingWayMatch);
                RemoveNeedlessNodes remover = new RemoveNeedlessNodes(remove_filter, 0.07, (Math.PI*2)/3);
                remover.removeNeedlessNodes(editor.getModifiedWays());
            }

            // Merge duplicate ways
            // (Note that outer way can be merged to another way too, so we must watch it.
            // Otherwise, outer_way variable would refer to an unused way.)
            if (m_performWayMerging) {
                AreaPredicate merge_filter = new AreaPredicate (m_mergeBuildingWayMatch);
                MergeIdenticalWays merger = new MergeIdenticalWays(editor, merge_filter);
                outer_way = merger.mergeWays(editor.getModifiedWays(), true, outer_way);
            }

            List<Command> commands = editor.finalizeEdit(resurrectNodesDistanceMeters);

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
                m_postTraceNotifications.add(tr("Nothing changed."));
            }
        }

        @Override
        protected void finish() {
        }

        @Override
        protected void cancel() {
            m_cancelled = true;
            // #### TODO: break the connection to remote server
        }

        private void tagTracedObject (EdObject obj) {

            Map <String, String> map = obj.getKeys();

            Map <String, String> new_keys = new HashMap <> (m_record.getKeys());
            for (Map.Entry<String, String> new_key: new_keys.entrySet()) {
                map.put(new_key.getKey(), new_key.getValue());
            }
            // #### delete any existing retraced tags??
            obj.setKeys(map);
        }

        private Pair<EdWay, EdMultipolygon> createTracedEdObject (WayEditor editor) {

            TracerPreferences pref = TracerPreferences.getInstance();

            double dAdjX = 0, dAdjY = 0;

            if (pref.isRuianAdjustPositionEnabled()) {
              dAdjX = pref.getRuianAdjustPositionLat();
              dAdjY = pref.getRuianAdjustPositionLon();
            }

            final double precision = GeomUtils.duplicateNodesPrecision();

            // Prepare outer way nodes
            LatLon prev_coor = null;
            List<LatLon> outer_rls = m_record.getOuter();
            List<EdNode> outer_nodes = new ArrayList<> (outer_rls.size());
            // m_record.getCoorCount() - 1 - omit last node
            for (int i = 0; i < outer_rls.size() - 1; i++) {
                EdNode node;

                // Apply corrections to node coordinates
                if (!pref.isRuianAdjustPositionEnabled()) {
                  node = editor.newNode(outer_rls.get(i));
                } else {
                  node = editor.newNode(new LatLon(LatLon.roundToOsmPrecision(outer_rls.get(i).lat()+dAdjX),
                                                   LatLon.roundToOsmPrecision(outer_rls.get(i).lon()+dAdjY)));
                }

                if (!editor.insideDataSourceBounds(node)) {
                    wayIsOutsideDownloadedAreaDialog();
                    return null;
                }

                if (!GeomUtils.duplicateNodes(node.getCoor(), prev_coor, precision)) {
                    outer_nodes.add(node);
                    prev_coor = node.getCoor();
                }
            }
            if (outer_nodes.size() < 3)
                throw new AssertionError(tr("Outer way consists of less than 3 nodes"));

            // Close & create outer way
            outer_nodes.add(outer_nodes.get(0));
            EdWay outer_way = editor.newWay(outer_nodes);

            // Simple way?
            if (!m_record.hasInners())
                return new Pair<>(outer_way, null);

            // Create multipolygon
            prev_coor = null;
            EdMultipolygon multipolygon = editor.newMultipolygon();
            multipolygon.addOuterWay(outer_way);

            for (List<LatLon> inner_rls: m_record.getInners()) {
                List<EdNode> inner_nodes = new ArrayList<>(inner_rls.size());
                for (int i = 0; i < inner_rls.size() - 1; i++) {
                    EdNode node;
                    // Apply corrections to node coordinates
                    if (!pref.isRuianAdjustPositionEnabled()) {
                      node = editor.newNode(inner_rls.get(i));
                    } else {
                      node = editor.newNode(new LatLon(LatLon.roundToOsmPrecision(inner_rls.get(i).lat()+dAdjX),
                                                      LatLon.roundToOsmPrecision(inner_rls.get(i).lon()+dAdjY)));
                    }

                    if (!GeomUtils.duplicateNodes(node.getCoor(), prev_coor, precision)) {
                        inner_nodes.add(node);
                        prev_coor = node.getCoor();
                    }
                }

                // Close & create inner way
                if (inner_nodes.size() < 3)
                    throw new AssertionError(tr("Inner way consists of less than 3 nodes"));
                inner_nodes.add(inner_nodes.get(0));
                EdWay way = editor.newWay(inner_nodes);

                multipolygon.addInnerWay(way);
            }

            return new Pair<>(outer_way, multipolygon);
        }

        private Pair<EdObject, Boolean> getObjectToRetrace(WayEditor editor, LatLon pos, Match retraceAreaMatch) {
            AreaPredicate filter = new AreaPredicate(retraceAreaMatch);
            Set<EdObject> areas = editor.useNonEditedAreasContainingPoint(pos, filter);

            String ruianref = Long.toString(m_record.getBuildingID());

            // restrict to RUIAN areas only, yet ... #### improve in the future
            boolean multiple_areas = false;
            EdObject building_area = null;
            for (EdObject area: areas) {
                  // Retrace all building if possible
//                 String source = area.get("source");
//                 if (source == null || !source.equals("cuzk:ruian"))
//                     continue;

                if (area instanceof EdWay)
                    System.out.println("Retrace candidate EdWay: " + Long.toString(area.getUniqueId()));
                else if (area instanceof EdMultipolygon)
                    System.out.println("Retrace candidate EdMultipolygon: " + Long.toString(area.getUniqueId()));

                String ref = area.get("ref:ruian:building");
                if (ref != null && ref.equals(ruianref)) // exact match ;)
                    return new Pair<>(area, false);

                if (building_area == null)
                    building_area = area;
                else
                    multiple_areas = true;
            }

            if (multiple_areas) {
                return new Pair<>(null, true);
            }

            if (building_area != null) {
                return new Pair<>(building_area, false);
            }

            return new Pair<>(null, false);
        }

        private void connectExistingTouchingNodes(EdObject obj) {
            IEdNodePredicate filter = reuseExistingNodesFilter(obj);
            obj.connectExistingTouchingNodes(m_connectTolerance, filter);
        }

        private void reuseExistingNodes(EdObject obj) {
            obj.reuseExistingNodes (reuseExistingNodesFilter(obj));
        }

        private void reuseNearNodes(EdObject obj, Set<EdNode> retraced_nodes) {
            obj.reuseNearNodes (new ReuseBuildingNearNodes(retraced_nodes), reuseExistingNodesFilter(obj));
        }

        private IEdNodePredicate reuseExistingNodesFilter(EdObject obj) {
            // Setup filtes - include building nodes only, exclude all nodes of the object itself
            IEdNodePredicate nodes_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingBuildingNodeMatch);
            IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(obj);
            return new EdNodeLogicalAndPredicate (exclude_my_nodes, nodes_filter);
        }

        @SuppressWarnings("null")
        private Pair<EdWay, EdMultipolygon> updateRetracedObjects(EdObject new_object, EdObject retrace_object) {

            boolean retrace_is_simple_way = (retrace_object instanceof EdWay) && !retrace_object.hasReferrers();
            boolean new_is_way = (new_object instanceof EdWay);
            boolean new_is_multipolygon = (new_object instanceof EdMultipolygon);

            // Simple way -> Simple way
            if (retrace_is_simple_way && new_is_way) {
                EdWay outer_way = (EdWay) new_object;
                EdWay retrace_way = (EdWay) retrace_object;
                retrace_way.setNodes(outer_way.getNodes());
                outer_way = retrace_way;
                return new Pair<>(outer_way, null);
            }

            // Simple way -> Multipolygon
            // Move all non-linear tags from way to multipolygon, use retraced way as outer way of the new multipolygon
            if (retrace_is_simple_way && new_is_multipolygon) {
                EdWay retrace_way = (EdWay) retrace_object;
                EdMultipolygon multipolygon = (EdMultipolygon)new_object;
                multipolygon.moveAllNonLinearTagsFrom(retrace_way);
                EdWay outer_way = multipolygon.outerWays().get(0);
                multipolygon.removeOuterWay(outer_way);
                retrace_way.setNodes(outer_way.getNodes());
                outer_way = retrace_way;
                multipolygon.addOuterWay(outer_way);
                return new Pair<>(outer_way, multipolygon);
            }

            // Multipolygon -> Multipolygon
            if ((retrace_object instanceof EdMultipolygon) && new_is_multipolygon) {
                EdMultipolygon retrace_multipolygon = (EdMultipolygon)retrace_object;
                EdMultipolygon new_multipolygon = (EdMultipolygon)new_object;
                Pair<EdWay, EdMultipolygon> res = updateRetracedMultipolygons(new_multipolygon, retrace_multipolygon);
                if (res != null)
                    return res;
            }

            m_postTraceNotifications.add(tr("This kind of multipolygon retrace is not supported."));
            return null;
        }

        private Pair<EdWay, EdMultipolygon> updateRetracedMultipolygons(EdMultipolygon new_multipolygon, EdMultipolygon retrace_multipolygon) {

            // don't retrace non-standalone multipolygons
            boolean retrace_is_standalone = !retrace_multipolygon.hasReferrers() && retrace_multipolygon.allWaysHaveSingleReferrer();
            if (!retrace_is_standalone)
                return null;

            // don't retrace multipolygons with nonclosed ways
            boolean retrace_is_closed = !retrace_multipolygon.containsNonClosedWays();
            if (!retrace_is_closed)
                return null;

            // try to convert old-style multipolygon to new-style
            if (retrace_multipolygon.containsTaggedWays() && Main.pref.getBoolean("multipoly.movetags", true))
                retrace_multipolygon.removeTagsFromWaysIfNeeded();

            // agressive retrace of multipolygon with untagged ways
            if (!retrace_multipolygon.containsTaggedWays()) {
                updateRetracedMultipolygonWaysAgressive(retrace_multipolygon, new_multipolygon, true);
                updateRetracedMultipolygonWaysAgressive(retrace_multipolygon, new_multipolygon, false);
                new_multipolygon.deleteShallow();
                return new Pair<>(retrace_multipolygon.outerWays().get(0), retrace_multipolygon);
            }

            // other possibilites not supported yet
            return null;
        }

        private void updateRetracedMultipolygonWaysAgressive(EdMultipolygon retrace_multipolygon, EdMultipolygon new_multipolygon, boolean out) {
            // #### use pairing based on way area intersection?

            List<EdWay> retraces = out ? retrace_multipolygon.outerWays() : retrace_multipolygon.innerWays();
            List<EdWay> news = out ? new_multipolygon.outerWays() : new_multipolygon.innerWays();
            int ridx = 0;

            for (int i = 0; i < news.size(); i++) {
                EdWay new_way = news.get(i);
                // update geometry of existing way
                if (ridx < retraces.size()) {
                    EdWay retrace_way = retraces.get(ridx++);
                    retrace_way.setNodes(new_way.getNodes());
                }
                // no more existing ways available, add new way
                else if (out)
                    retrace_multipolygon.addOuterWay(new_way);
                else
                    retrace_multipolygon.addInnerWay(new_way);
            }

            // remove unused old ways if the new multipolygon has less ways than the old one
            for (int i = ridx; i < retraces.size(); i++) {
                if (out)
                    retrace_multipolygon.removeOuterWay(retraces.get(i));
                else
                    retrace_multipolygon.removeInnerWay(retraces.get(i));
            }
        }
    }
}

