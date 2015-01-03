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

import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.*;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;

public final class RuianModule extends TracerModule {

    protected boolean cancel;
    private boolean moduleEnabled;

    private final String ruianUrl = "http://josm.poloha.net";

    private static final double oversizeInDataBoundsMeters = 2.0;

    private static final GeomDeviation m_connectTolerance = new GeomDeviation (0.15, Math.PI / 50);
    private static final GeomDeviation m_removeNeedlesNodesTolerance = new GeomDeviation (0.10, Math.PI / 50);
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


    class RuianTracerTask extends AbstractTracerTask {

        private final boolean m_performNearBuildingsEdit;

        RuianTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (pos, ctrl, alt ,shift);

            this.m_performNearBuildingsEdit = !m_ctrl;
        }

        private RuianRecord record() {
            return (RuianRecord)super.getRecord();
        }

        @Override
        protected TracerRecord downloadRecord(LatLon pos) throws Exception {
            TracerPreferences pref = TracerPreferences.getInstance();
            String sUrl = ruianUrl;
            if (pref.isCustomRuainUrlEnabled())
              sUrl = pref.getCustomRuainUrl();

            // Get coordinate corrections
            double adjlat = 0, adjlon = 0;
            if (pref.isRuianAdjustPositionEnabled()) {
              adjlat = pref.getRuianAdjustPositionLat();
              adjlon = pref.getRuianAdjustPositionLon();
            }

            RuianServer server = new RuianServer();
            return server.trace(m_pos, sUrl, adjlat, adjlon);
        }

        @Override
        protected EdObject createTracedPolygonImpl(WayEditor editor) {

            System.out.println("  RUIAN keys: " + record().getKeys(m_alt));

            // Look for object to retrace
            EdObject retrace_object = null;
            if (m_performRetrace) {
                Pair<EdObject, Boolean> repl = getObjectToRetrace(editor, m_pos, m_retraceAreaMatch);
                retrace_object = repl.a;
                boolean ambiguous_retrace = repl.b;

                if (ambiguous_retrace) {
                    postTraceNotifications().add(tr("Multiple existing Ruian building polygons found, retrace is not possible."));
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

            // Connect to near building polygons
            // (must be done before retrace updates, we want to use as much old nodes as possible)
            if (!m_performNearBuildingsEdit) {
                reuseExistingNodes(trobj);
            }
            else {
                reuseNearNodes(trobj, retrace_object);
            }

            // Update geometries of retraced object
            if (retrace_object != null) {
                RetraceUpdater retr = new RetraceUpdater(true, postTraceNotifications());
                trobj = retr.updateRetracedObjects(trobj, retrace_object);
                if (trobj == null)
                    return null;
            }

            // Connect to touching nodes of near buildings
            // (must be done after retrace updates, we don't want to connect to the old polygon)
            if (m_performNearBuildingsEdit) {
                connectExistingTouchingNodes(trobj);
            }

            // Tag object
            tagTracedObject(trobj);

            // Clip other areas
            if (m_performClipping) {
                // #### Now, it clips using only the outer way. Consider if multipolygon clip is necessary/useful.
                AreaPredicate filter = new AreaPredicate (m_clipBuildingWayMatch);
                ClipAreas clip = new ClipAreas(editor, m_clipSettings, postTraceNotifications());
                clip.clipAreas(getOuterWay(trobj), filter);

                // Remove needless nodes
                AreaPredicate remove_filter = new AreaPredicate (m_clipBuildingWayMatch);
                RemoveNeedlessNodes remover = new RemoveNeedlessNodes(remove_filter, m_removeNeedlesNodesTolerance, (Math.PI*2)/3);
                remover.removeNeedlessNodes(editor.getModifiedWays());
            }

            // Merge duplicate ways
            // (Note that outer way can be merged to another way too, so we must watch it.
            // Otherwise, trobj variable would refer to an unused way.)
            if (m_performWayMerging) {
                AreaPredicate merge_filter = new AreaPredicate (m_mergeBuildingWayMatch);
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

            // replace only buildings with key building=yes
            if (! TracerUtils.containsAnyTag(map, "building", "yes", ""))
                new_keys.remove("building");

            // do not update building:levels key value
            if (map.containsKey("building:levels"))
                new_keys.remove("building:levels");

            for (Map.Entry<String, String> new_key: new_keys.entrySet()) {
                map.put(new_key.getKey(), new_key.getValue());
            }
            obj.setKeys(map);
        }

        private Pair<EdObject, Boolean> getObjectToRetrace(WayEditor editor, LatLon pos, Match retraceAreaMatch) {
            AreaPredicate filter = new AreaPredicate(retraceAreaMatch);
            Set<EdObject> areas = editor.useNonEditedAreasContainingPoint(pos, filter);

            String ruianref = Long.toString(record().getBuildingID());

            boolean multiple_areas = false;
            EdObject building_area = null;
            for (EdObject area: areas) {
                if (area.isWay())
                    System.out.println("Retrace candidate EdWay: " + Long.toString(area.getUniqueId()));
                else if (area.isMultipolygon())
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

        private void reuseNearNodes(EdObject obj, EdObject retrace_object) {
            Set<EdNode> retrace_nodes = null;
            if (retrace_object != null)
                retrace_nodes = retrace_object.getAllNodes();
            obj.reuseNearNodes (new ReuseBuildingNearNodes(retrace_nodes), reuseExistingNodesFilter(obj));
        }

        private IEdNodePredicate reuseExistingNodesFilter(EdObject obj) {
            // Setup filtes - include building nodes only, exclude all nodes of the object itself
            IEdNodePredicate nodes_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingBuildingNodeMatch);
            IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(obj);
            return new EdNodeLogicalAndPredicate (exclude_my_nodes, nodes_filter);
        }

        private boolean checkInsideDataSourceBounds(EdObject new_object, EdObject retrace_object) {
            LatLonSize bounds_oversize = LatLonSize.get(new_object.getBBox(), oversizeInDataBoundsMeters);
            if (retrace_object != null && !retrace_object.isInsideDataSourceBounds(bounds_oversize))
                return false;
            return new_object.isInsideDataSourceBounds(bounds_oversize);
        }
    }
}

