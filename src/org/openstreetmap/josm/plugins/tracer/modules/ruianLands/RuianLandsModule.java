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
package org.openstreetmap.josm.plugins.tracer.modules.ruianLands;

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
import org.openstreetmap.josm.plugins.tracer.connectways.*;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;



public final class RuianLandsModule extends TracerModule {

    protected boolean cancel;
    private boolean moduleEnabled;

    private static final double oversizeInDataBoundsMeters = 5.0;

    private static final String RuianLandsUrl = "http://josm.poloha.net";

    private static final String reuseExistingLanduseNodePattern =
        "((landuse=* -landuse=no -landuse=military) | natural=scrub | natural=wood | natural=grassland | leisure=garden)";

    private static final String retraceLandsAreaPattern =
        "(landuse=farmland | landuse=meadow | landuse=orchard | landuse=vineyard | landuse=plant_nursery | (landuse=forest source=\"cuzk:ruian\") | (landuse=forest source=\"cuzk:km\") | natural=scrub | natural=wood | natural=grassland | leisure=garden)";

    private static final Match m_reuseExistingLanduseNodeMatch;
    private static final Match m_clipLanduseWayMatch;
    private static final Match m_mergeLanduseWayMatch;
    private static final Match m_retraceLandsAreaMatch;

    static {
        try {
            m_reuseExistingLanduseNodeMatch = SearchCompiler.compile(reuseExistingLanduseNodePattern, false, false);
            m_clipLanduseWayMatch = m_reuseExistingLanduseNodeMatch; // use the same
            m_mergeLanduseWayMatch = m_clipLanduseWayMatch; // use the same
            m_retraceLandsAreaMatch = SearchCompiler.compile(retraceLandsAreaPattern, false, false);
        }
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile landuse pattern"));
        }
    }

    private static final String reuseExistingGardenWayPattern =
        "(landuse=farmland | landuse=meadow | landuse=orchard | landuse=vineyard | landuse=plant_nursery | (landuse=forest source=\"cuzk:ruian\") | (landuse=forest source=\"cuzk:km\") | natural=scrub | natural=wood | natural=grassland | leisure=garden)";
    private static final Match m_clipGardenWayMatch;

    static {
        try {
            m_clipGardenWayMatch = SearchCompiler.compile(reuseExistingGardenWayPattern, false, false);
        }
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile garden pattern"));
        }
    }

    private static final String reuseExistingBuildingNodePattern =
        "(building=* -building=no -building=entrance)";

    private static final String retraceBuildingAreaPattern =
        "(building=* -building=no -building=entrance)";

    private static final Match m_reuseExistingBuildingNodeMatch;
    private static final Match m_clipBuildingWayMatch;
    private static final Match m_mergeBuildingWayMatch;
    private static final Match m_retraceBuildingAreaMatch;

    static {
        try {
            m_reuseExistingBuildingNodeMatch = SearchCompiler.compile(reuseExistingBuildingNodePattern, false, false);
            m_clipBuildingWayMatch = m_reuseExistingBuildingNodeMatch; // use the same
            m_mergeBuildingWayMatch = m_clipBuildingWayMatch; // use the same
            m_retraceBuildingAreaMatch = SearchCompiler.compile(retraceBuildingAreaPattern, false, false);
        }
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile building pattern"));
        }
    }


    public RuianLandsModule(boolean enabled) {
      moduleEnabled = enabled;
    }

    @Override
    public void init() {

    }

    @Override
    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-ruian-lands-sml");
    }

    @Override
    public String getName() {
        return tr("RUIAN-Lands");
    }

    @Override
    public boolean moduleIsEnabled() {
        return moduleEnabled;
    };

    @Override
    public void setModuleIsEnabled(boolean enabled){
        moduleEnabled = enabled;
    };

    @Override
    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new RuianLandsTracerTask (pos, ctrl, alt, shift);
    }

    class RuianLandsTracerTask extends AbstractTracerTask {

        private final GeomDeviation m_tolerance = new GeomDeviation (0.2, Math.PI / 3);
        private final ClipAreasSettings m_clipSettings = new ClipAreasSettings (m_tolerance);

        RuianLandsTracerTask  (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (pos, ctrl, alt, shift);
        }

        private RuianLandsRecord record() {
            return (RuianLandsRecord)super.getRecord();
        }

        @Override
        protected TracerRecord downloadRecord(LatLon pos) throws Exception {
            TracerPreferences pref = TracerPreferences.getInstance();
            String sUrl = RuianLandsUrl;
            if (pref.isCustomRuainUrlEnabled())
              sUrl = pref.getCustomRuainUrl();

            // Get coordinate corrections
            double adjlat = 0, adjlon = 0;
            if (pref.isRuianAdjustPositionEnabled()) {
              adjlat = pref.getRuianAdjustPositionLat();
              adjlon = pref.getRuianAdjustPositionLon();
            }

            RuianLandsServer server = new RuianLandsServer();
            return server.trace(m_pos, sUrl, adjlat, adjlon);
        }

        @Override
        protected EdObject createTracedPolygonImpl(WayEditor editor) {

            System.out.println("  RUIAN keys: " + record().getKeys());

            Match clipWayMatch;
            Match mergeWayMatch;
            Match retraceAreaMatch;

            // Determine type of the objects
            if (record().isBuilding()) {
              clipWayMatch = m_clipBuildingWayMatch;
              mergeWayMatch = m_mergeBuildingWayMatch;
              retraceAreaMatch = m_retraceBuildingAreaMatch;
            } else if (record().isGarden()) {
              clipWayMatch = m_clipGardenWayMatch;
              mergeWayMatch = m_mergeLanduseWayMatch;
              retraceAreaMatch = m_retraceLandsAreaMatch;
            } else {
              clipWayMatch = m_clipLanduseWayMatch;
              mergeWayMatch = m_mergeLanduseWayMatch;
              retraceAreaMatch = m_retraceLandsAreaMatch;
            }

            // Look for object to retrace
            EdObject retrace_object = null;
            if (m_performRetrace) {
                Pair<EdObject, Boolean> repl = getObjectToRetrace(editor, m_pos, retraceAreaMatch);
                retrace_object = repl.a;
                boolean ambiguous_retrace = repl.b;

                if (ambiguous_retrace) {
                    postTraceNotifications().add(tr("Multiple existing Ruian building polygons found, retrace is not possible."));
                    return null;
                }
            }

            // Create traced object
            Pair<EdWay, EdMultipolygon> trobj = this.createTracedEdObject(editor);
            if (trobj == null)
                return null;
            EdWay outer_way = trobj.a;
            EdMultipolygon multipolygon = trobj.b;

            // Retrace simple ways - just use the old way
            if (retrace_object != null) {
                if ((multipolygon != null) || !(retrace_object instanceof EdWay) || retrace_object.hasReferrers()) {
                    postTraceNotifications().add(tr("Multipolygon retrace is not supported yet."));
                    return null;
                }
                EdWay retrace_way = (EdWay)retrace_object;
                retrace_way.setNodes(outer_way.getNodes());
                outer_way = retrace_way;
            }

            // Everything is inside DataSource bounds?
            if (!checkInsideDataSourceBounds(multipolygon == null ? outer_way : multipolygon, retrace_object)) {
                wayIsOutsideDownloadedAreaDialog();
                return null;
            }

            // Tag object
            tagTracedObject(multipolygon == null ? outer_way : multipolygon);

            // Connect to touching nodes of near building polygons
            connectExistingTouchingNodes(multipolygon == null ? outer_way : multipolygon);

            // Clip other areas
            if (m_performClipping) {
                // #### Now, it clips using only the outer way. Consider if multipolygon clip is necessary/useful.
                AreaPredicate filter = new AreaPredicate (clipWayMatch);
                ClipAreas clip = new ClipAreas(editor, m_clipSettings, postTraceNotifications());
                clip.clipAreas(outer_way, filter);
            }

            // Merge duplicate ways
            // (Note that outer way can be merged to another way too, so we must watch it.
            // Otherwise, outer_way variable would refer to an unused way.)
            if (m_performWayMerging) {
                AreaPredicate merge_filter = new AreaPredicate (mergeWayMatch);
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

            IEdNodePredicate reuse_filter;

            // Determine type of the objects
            if (record().isBuilding()) {
              reuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingBuildingNodeMatch);
            } else if (record().isGarden()) {
              reuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            } else {
              reuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            }

            final double precision = GeomUtils.duplicateNodesPrecision();

            // Prepare outer way nodes
            List<EdNode> outer_nodes = new ArrayList<> ();
            List<LatLon> outer = record().getOuter();
            LatLon prev_coor = null;
            // m_record.getCoorCount() - 1 - omit last node
            for (int i = 0; i < outer.size() - 1; i++) {
                EdNode node = editor.newNode(outer.get(i));

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

            outer_way.reuseExistingNodes(reuse_filter);

// #### Multipolygons are not supported yet.
            // Simple way?
//             if (!m_record.hasInners())
                return new Pair<>(outer_way, null);

//             // Create multipolygon
//             EdMultipolygon multipolygon = editor.newMultipolygon();
//             multipolygon.addOuterWay(outer_way);
//
//             for (List<LatLon> inner_lls: m_record.getInners()) {
//                 List<EdNode> inner_nodes = new ArrayList<>(inner_lls.size());
//                 for (int i = 0; i < inner_lls.size() - 1; i++) {
//                     inner_nodes.add(editor.newNode(inner_lls.get(i)));
//                 }
//
//                 // Close & create inner way
//                 if (inner_nodes.size() < 3)
//                     throw new AssertionError(tr("Inner way consists of less than 3 nodes"));
//                 inner_nodes.add(inner_nodes.get(0));
//                 EdWay way = editor.newWay(inner_nodes);
//                 way.reuseExistingNodes(reuse_filter);
//
//                 multipolygon.addInnerWay(way);
//             }
//
//             return new Pair<>(outer_way, multipolygon);
        }

        private Pair<EdObject, Boolean> getObjectToRetrace(WayEditor editor, LatLon pos, Match retraceAreaMatch) {
            AreaPredicate filter = new AreaPredicate(retraceAreaMatch);
            Set<EdObject> areas = editor.useNonEditedAreasContainingPoint(pos, filter);

            String ruianref = Long.toString(record().getLandID());

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
            // Setup filters - include building nodes only, exclude all nodes of the object itself
            IEdNodePredicate area_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingBuildingNodeMatch);
            IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(obj);
            IEdNodePredicate filter = new EdNodeLogicalAndPredicate (exclude_my_nodes, area_filter);

            obj.connectExistingTouchingNodes(m_tolerance, filter);
        }

        private boolean checkInsideDataSourceBounds(EdObject new_object, EdObject retrace_object) {
            LatLonSize bounds_oversize = LatLonSize.get(new_object.getBBox(), oversizeInDataBoundsMeters);
            if (retrace_object != null && !retrace_object.isInsideDataSourceBounds(bounds_oversize))
                return false;
            return new_object.isInsideDataSourceBounds(bounds_oversize);
        }
    }
}
