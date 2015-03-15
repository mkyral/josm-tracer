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

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import java.awt.Cursor;
import java.util.*;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;
import org.openstreetmap.josm.plugins.tracer.connectways.*;

// import org.openstreetmap.josm.plugins.tracer.modules.lpis.LpisRecord;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;

public final class LpisModule extends TracerModule  {

    private boolean moduleEnabled;

    private static final ExecutorService m_downloadExecutor;

    static {
        int threads = Main.pref.getInteger("tracer.lpis.downloadThreads", 4);
        if (threads > 0) {
            if (threads > 20) // avoid stupid values
                threads = 20;
            m_downloadExecutor = Executors.newFixedThreadPool(threads);
        }
        else {
            m_downloadExecutor = null;
        }
    }

    private static final double oversizeInDataBoundsMeters = 5.0;
    private static final double automaticOsmDownloadMeters = 900.0;
    private static final GeomDeviation m_connectTolerance = new GeomDeviation(0.25, Math.PI / 15);
    private static final GeomDeviation m_removeNeedlesNodesTolerance = new GeomDeviation (0.25, Math.PI / 40);

    private static final String source = "lpis";
    private static final String lpisUrl = "http://eagri.cz/public/app/wms/plpis_wfs.fcgi";
    private static final String reuseExistingLanduseNodePattern =
        "((landuse=* -landuse=no -landuse=military) | natural=scrub | natural=wood | natural=grassland | natural=wood | leisure=garden)";
    private static final String retraceAreaPattern =
        "(landuse=farmland | landuse=meadow | landuse=orchard | landuse=vineyard | landuse=plant_nursery | (landuse=forest source=lpis))";

    private static final Match m_reuseExistingLanduseNodeMatch;
    private static final Match m_clipLanduseWayMatch;
    private static final Match m_mergeLanduseWayMatch;
    private static final Match m_retraceAreaMatch;

    static {
        try {
            m_reuseExistingLanduseNodeMatch = SearchCompiler.compile(reuseExistingLanduseNodePattern, false, false);
            m_clipLanduseWayMatch = m_reuseExistingLanduseNodeMatch; // use the same
            m_mergeLanduseWayMatch = m_clipLanduseWayMatch; // use the same
            m_retraceAreaMatch = SearchCompiler.compile(retraceAreaPattern, false, false);
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
    public AbstractTracerTask trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new LpisTracerTask (pos, ctrl, alt, shift);
    }

    class ReuseLanduseNearNodes implements IReuseNearNodePredicate {

        // distance tolerancies are in meters
        private final double m_reuseNearNodesToleranceDefault = m_connectTolerance.distanceMeters();
        private final double m_reuseNearNodesToleranceRetracedNodes = 0.40;

        private final double m_lookupDistanceMeters;

        private final Set<EdNode> m_retracedNodes;

        ReuseLanduseNearNodes (Set<EdNode> retraced_nodes) {
            m_retracedNodes = retraced_nodes;
            m_lookupDistanceMeters = Math.max(m_reuseNearNodesToleranceDefault, m_reuseNearNodesToleranceRetracedNodes);
        }

        @Override
        public ReuseNearNodeMethod reuseNearNode(EdNode node, EdNode near_node, double distance_meters) {

            boolean retraced = m_retracedNodes != null && m_retracedNodes.contains(near_node);

            // be more tolerant for untagged nodes occurring in retraced ways, feel free to move them
            if (retraced) {
                System.out.println("RNN: retraced, dist=" + Double.toString(distance_meters));
                if (distance_meters <= m_reuseNearNodesToleranceRetracedNodes)
                    if (!near_node.isTagged())
                        return ReuseNearNodeMethod.moveAndReuseNode;
            }

            // use default tolerance for others, don't move them, just reuse
            System.out.println("RNN: default, dist=" + Double.toString(distance_meters));
            if (distance_meters <= m_reuseNearNodesToleranceDefault)
                return ReuseNearNodeMethod.reuseNode;

            return ReuseNearNodeMethod.dontReuseNode;
        }

        @Override
        public double lookupDistanceMeters() {
            return m_lookupDistanceMeters;
        }
    }

    class LpisTracerTask extends AbstractTracerTask {

        private final ClipAreasSettings m_clipSettings = new ClipAreasSettings (m_connectTolerance);

        LpisTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (pos, ctrl, alt, shift);
        }

        private LpisRecord record() {
            return (LpisRecord) super.getRecord();
        }

        @Override
        protected ExecutorService getDownloadRecordExecutor() {
            return m_downloadExecutor;
        }

        @Override
        protected TracerRecord downloadRecord(LatLon pos) throws Exception {
            LpisServer server = new LpisServer();
            return server.getElementData(pos, lpisUrl, 0.0, 0.0);
        }

        @Override
        protected EdObject createTracedPolygonImpl(WayEditor editor) {

            System.out.println("  LPIS ID: " + record().getLpisID());
            System.out.println("  LPIS usage: " + record().getUsage());

            // Look for object to retrace
            EdObject retrace_object = null;
            if (m_performRetrace) {
                Pair<EdObject, Boolean> repl = getObjectToRetrace(editor, m_pos);
                retrace_object = repl.a;
                boolean ambiguous_retrace = repl.b;

                if (ambiguous_retrace) {
                    postTraceNotifications().add(tr("Multiple existing LPIS polygons found, retrace is not possible."));
                    return null;
                }
            }

            // Create traced object
            EdObject trobj = record().createObject(editor);

            // Everything is inside DataSource bounds?
            if (!checkInsideDataSourceBounds(trobj, retrace_object)) {
                wayIsOutsideDownloadedAreaDialog();
                return null;
            }

            // Connect nodes to near landuse nodes
            // (must be done before retrace updates, we want to use as much old nodes as possible)
            if (!m_performClipping) {
                reuseExistingNodes(trobj);
            }
            else {
                reuseNearNodes(trobj, retrace_object);
            }

            // Update geometries of retraced object
            if (retrace_object != null) {
                RetraceUpdater retr = new RetraceUpdater(false, postTraceNotifications());
                trobj = retr.updateRetracedObjects(trobj, retrace_object);
                if (trobj == null)
                    return null;
            }

            // Tag object
            tagTracedObject(trobj);

            // Connect to touching nodes of near landuse polygons
            connectExistingTouchingNodes(trobj);

            // Clip other areas
            if (m_performClipping) {
                // #### Now, it clips using only the outer way. Consider if multipolygon clip is necessary/useful.
                AreaPredicate filter = new AreaPredicate (m_clipLanduseWayMatch);
                ClipAreas clip = new ClipAreas(editor, m_clipSettings, postTraceNotifications());
                clip.clipAreas(getOuterWay(trobj), filter);

                // Remove needless nodes
                AreaPredicate remove_filter = new AreaPredicate (m_clipLanduseWayMatch);
                BBox remove_bbox = trobj.getBBox();
                BBoxUtils.extendBBox(remove_bbox, LatLonSize.get(remove_bbox, oversizeInDataBoundsMeters));
                RemoveNeedlessNodes remover = new RemoveNeedlessNodes(remove_filter, m_removeNeedlesNodesTolerance, (Math.PI*2)/3, remove_bbox);
                remover.removeNeedlessNodes(editor.getModifiedWays());
            }

            // Merge duplicate ways
            // (Note that outer way can be merged to another way too, so we must watch it.
            // Otherwise, trobj variable would refer to an unused way.)
            if (m_performWayMerging) {
                AreaPredicate merge_filter = new AreaPredicate (m_mergeLanduseWayMatch);
                MergeIdenticalWays merger = new MergeIdenticalWays(editor, merge_filter);
                EdWay outer_way = merger.mergeWays(editor.getModifiedWays(), true, getOuterWay(trobj));
                if (trobj.isWay()) {
                    trobj = outer_way;
                }
            }

            return trobj;
        }

        private void tagTracedObject (EdObject obj) {

            Map <String, String> map = obj.getKeys();

            Map <String, String> new_keys = new HashMap <> (record().getKeys());
            for (Map.Entry<String, String> new_key: new_keys.entrySet()) {
                map.put(new_key.getKey(), new_key.getValue());
            }

            map.put("source", source);
            map.put("ref", Long.toString(record().getLpisID()));
            obj.setKeys(map);
        }

        private Pair<EdObject, Boolean> getObjectToRetrace(WayEditor editor, LatLon pos) {
            AreaPredicate filter = new AreaPredicate(m_retraceAreaMatch);
            Set<EdObject> areas = editor.useNonEditedAreasContainingPoint(pos, filter);

            String lpisref = Long.toString(record().getLpisID());

            // restrict to LPIS areas only, yet ... #### improve in the future
            boolean multiple_areas = false;
            EdObject lpis_area = null;
            for (EdObject area: areas) {
                String source = area.get("source");
                if (source == null || !source.equals("lpis"))
                    continue;

                if (area.isWay())
                    System.out.println("Retrace candidate EdWay: " + Long.toString(area.getUniqueId()));
                else if (area.isMultipolygon())
                    System.out.println("Retrace candidate EdMultipolygon: " + Long.toString(area.getUniqueId()));

                String ref = area.get("ref");
                if (ref != null && ref.equals(lpisref)) // exact match ;)
                    return new Pair<>(area, false);

                if (lpis_area == null)
                    lpis_area = area;
                else
                    multiple_areas = true;
            }

            if (multiple_areas) {
                return new Pair<>(null, true);
            }

            if (lpis_area != null) {
                return new Pair<>(lpis_area, false);
            }

            return new Pair<>(null, false);
        }

        private void connectExistingTouchingNodes(EdObject obj) {
            obj.connectExistingTouchingNodes(m_connectTolerance, reuseExistingNodesFilter(obj));
        }

        private void reuseExistingNodes(EdObject obj) {
            obj.reuseExistingNodes (reuseExistingNodesFilter(obj));
        }

        private void reuseNearNodes(EdObject obj, EdObject retrace_object) {
            Set<EdNode> retrace_nodes = null;
            if (retrace_object != null)
                retrace_nodes = retrace_object.getAllNodes();
            obj.reuseNearNodes (new ReuseLanduseNearNodes(retrace_nodes), reuseExistingNodesFilter(obj));
        }

        private IEdNodePredicate reuseExistingNodesFilter(EdObject obj) {
            // Setup filters - include landuse nodes only, exclude all nodes of the object itself
            IEdNodePredicate nodes_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(obj);
            return new EdNodeLogicalAndPredicate (exclude_my_nodes, nodes_filter);
        }

        private boolean checkInsideDataSourceBounds(EdObject new_object, EdObject retrace_object) {
            LatLonSize bounds_oversize = LatLonSize.get(new_object.getBBox(), oversizeInDataBoundsMeters);
            if (retrace_object != null && !retrace_object.isInsideDataSourceBounds(bounds_oversize))
                return false;
            return new_object.isInsideDataSourceBounds(bounds_oversize);
        }

        @Override
        protected LatLonSize getMissingAreaCheckExtraSize(LatLon pos) {
            return LatLonSize.get(pos, 3 * oversizeInDataBoundsMeters);
        }

        @Override
        protected double getAutomaticOsmDownloadMeters () {
            return automaticOsmDownloadMeters;
        }
    }
}

