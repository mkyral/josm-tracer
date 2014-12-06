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

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;
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
            RuianServer server = new RuianServer();
            return server.trace(m_pos, sUrl);
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
            Pair<EdWay, EdMultipolygon> trobj = this.createTracedEdObject(editor);
            EdWay outer_way = trobj.a;
            EdMultipolygon multipolygon = trobj.b;

            // Everything is inside DataSource bounds?
            if (!checkInsideDataSourceBounds(multipolygon == null ? outer_way : multipolygon, retrace_object)) {
                wayIsOutsideDownloadedAreaDialog();
                return null;
            }

            // Connect to near building polygons
            // (must be done before retrace updates, we want to use as much old nodes as possible)
            if (!m_performNearBuildingsEdit) {
                reuseExistingNodes(multipolygon == null ? outer_way : multipolygon);
            }
            else {
                reuseNearNodes(multipolygon == null ? outer_way : multipolygon, retrace_object);
            }

            // Update geometries of retraced object
            if (retrace_object != null) {
                Pair<EdWay, EdMultipolygon> reobj = this.updateRetracedObjects(multipolygon == null ? outer_way : multipolygon, retrace_object);
                if (reobj == null)
                    return null;
                outer_way = reobj.a;
                multipolygon = reobj.b;
            }

            // Connect to touching nodes of near buildings
            // (must be done after retrace updates, we don't want to connect to the old polygon)
            if (m_performNearBuildingsEdit) {
                connectExistingTouchingNodes(multipolygon == null ? outer_way : multipolygon);
            }

            // Tag object
            tagTracedObject(multipolygon == null ? outer_way : multipolygon);

            // Clip other areas
            if (m_performClipping) {
                // #### Now, it clips using only the outer way. Consider if multipolygon clip is necessary/useful.
                AreaPredicate filter = new AreaPredicate (m_clipBuildingWayMatch);
                ClipAreas clip = new ClipAreas(editor, m_clipSettings, postTraceNotifications());
                clip.clipAreas(outer_way, filter);

                // Remove needless nodes
                AreaPredicate remove_filter = new AreaPredicate (m_clipBuildingWayMatch);
                RemoveNeedlessNodes remover = new RemoveNeedlessNodes(remove_filter, m_removeNeedlesNodesTolerance, (Math.PI*2)/3);
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

            return multipolygon == null ? outer_way : multipolygon;
        }

        private void tagTracedObject (EdObject obj) {

            Map <String, String> map = obj.getKeys();

            Map <String, String> new_keys = new HashMap <> (record().getKeys());
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
            List<LatLon> outer_rls = record().getOuter();
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
            if (!record().hasInners())
                return new Pair<>(outer_way, null);

            // Create multipolygon
            prev_coor = null;
            EdMultipolygon multipolygon = editor.newMultipolygon();
            multipolygon.addOuterWay(outer_way);

            for (List<LatLon> inner_rls: record().getInners()) {
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

            String ruianref = Long.toString(record().getBuildingID());

            boolean multiple_areas = false;
            EdObject building_area = null;
            for (EdObject area: areas) {
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

            postTraceNotifications().add(tr("This kind of multipolygon retrace is not supported."));
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

        private boolean checkInsideDataSourceBounds(EdObject new_object, EdObject retrace_object) {
            LatLonSize bounds_oversize = LatLonSize.get(new_object.getBBox(), oversizeInDataBoundsMeters);
            if (retrace_object != null && !retrace_object.isInsideDataSourceBounds(bounds_oversize))
                return false;
            return new_object.isInsideDataSourceBounds(bounds_oversize);
        }
    }
}

