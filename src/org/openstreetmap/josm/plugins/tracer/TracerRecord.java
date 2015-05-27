/**
 *  Tracer - plugin for JOSM
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

package org.openstreetmap.josm.plugins.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.projection.Projections;
import static org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.Functions.tr;
import org.openstreetmap.josm.plugins.tracer.connectways.BBoxUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.EdMultipolygon;
import org.openstreetmap.josm.plugins.tracer.connectways.EdNode;
import org.openstreetmap.josm.plugins.tracer.connectways.EdObject;
import org.openstreetmap.josm.plugins.tracer.connectways.EdWay;
import org.openstreetmap.josm.plugins.tracer.connectways.GeomUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.LatLonSize;
import org.openstreetmap.josm.plugins.tracer.connectways.WayEditor;

public abstract class TracerRecord implements IQuadCacheObject {

    private List<LatLon> m_outer;
    private List<List<LatLon>> m_inners;
    private BBox m_bbox;

    private final double m_adjustLat;
    private final double m_adjustLon;

    public TracerRecord (double adjlat, double adjlon) {
        m_outer = null;
        m_bbox = null;
        m_inners = new ArrayList<>();
        m_adjustLat = adjlat;
        m_adjustLon = adjlon;
    }

    protected void init() {
        m_outer = null;
        m_inners = new ArrayList<>();
    }

    /**
     *  Return outer polygon
     *  @return Outer polygon nodes
     */
    public final List <LatLon> getOuter() {
        if (!hasOuter())
            throw new IllegalStateException("No outer geometry available");
        return Collections.unmodifiableList(m_outer);
    }

    /**
     * Returns whether there's an outer way.
     * @return true if outer way is available
     */
    public final boolean hasOuter() {
        return m_outer != null;
    }

    /**
     *  Return whether there are inner polygons
     *  @return True/False
     */
    public final boolean hasInners() {
        return m_inners.size() > 0;
    }

    /**
     *  Returns the list of inner polygons
     *  @return inner polygons
     */
    public final List<List<LatLon>> getInners() {
        return Collections.unmodifiableList(m_inners);
    }

    /**
     * Returns BBox of the traced geometry
     * @return BBox of the geometry
     */
    @Override
    public final BBox getBBox() {
        if (!hasOuter() || m_bbox == null)
            throw new IllegalStateException(tr("No outer geometry available"));
        return m_bbox;
    }

    private void updateBBox () {
        List<LatLon> outer = this.getOuter();
        LatLon p0 = outer.get(0);

        BBox bbox = new BBox(p0.lon(), p0.lat());
        for (int i = 1; i < outer.size(); i++) {
            LatLon p = outer.get(i);
            bbox.add(p.lon(), p.lat());
        }

        m_bbox = bbox;
    }

    protected final void setOuter(List<LatLon> outer) {
        m_outer = adjustWay (outer);
        updateBBox ();
    }

    protected final void addInner(List<LatLon> inner) {
        m_inners.add(adjustWay (inner));
    }

    public abstract boolean hasData();

    private List<LatLon> adjustWay(List<LatLon> way) {

        if (way == null)
            throw new IllegalArgumentException("Null way");

        List<LatLon> list = new ArrayList<>(way.size());
        boolean adj = m_adjustLat != 0.0 && m_adjustLon != 0;
        final double precision = GeomUtils.duplicateNodesPrecision();
        LatLon prev_coor = null;

        for (LatLon ll: way) {
            // apply coordinate corrections
            LatLon latlon = (!adj) ?
                ll.getRoundedToOsmPrecision() :
                new LatLon(
                    LatLon.roundToOsmPrecision(ll.lat() + m_adjustLat),
                    LatLon.roundToOsmPrecision(ll.lon() + m_adjustLon));

            // avoid duplicate nodes
            if (GeomUtils.duplicateNodes(latlon, prev_coor, precision))
                continue;

            list.add(latlon);
            prev_coor = latlon;
        }

        if (list.size() <= 3) // we assume closed way here
            throw new IllegalStateException("Way consists of less than 3 nodes");

        return list;
    }

    public EdObject createObject (WayEditor editor) {

        if (!hasOuter())
            throw new IllegalStateException(tr("No outer geometry available"));

        // Prepare outer way nodes
        List<EdNode> outer_nodes = new ArrayList<> (m_outer.size());
        for (int i = 0; i < m_outer.size() - 1; i++) {
            outer_nodes.add(editor.newNode(m_outer.get(i)));
        }

        // Close & create outer way
        outer_nodes.add(outer_nodes.get(0));
        EdWay outer_way = editor.newWay(outer_nodes);

        // Simple way?
        if (!this.hasInners())
            return outer_way;

        // Create multipolygon
        EdMultipolygon multipolygon = editor.newMultipolygon();
        multipolygon.addOuterWay(outer_way);

        for (List<LatLon> inner_rls: m_inners) {
            List<EdNode> inner_nodes = new ArrayList<>(inner_rls.size());
            for (int i = 0; i < inner_rls.size() - 1; i++) {
                inner_nodes.add(editor.newNode(inner_rls.get(i)));
            }

            // Close & create inner way
            inner_nodes.add(inner_nodes.get(0));
            multipolygon.addInnerWay(editor.newWay(inner_nodes));
        }

        return multipolygon;
    }

    protected static long parseJsonLong(JsonObject obj, String key, long dflt) {
        String val = retrieveJsonString (obj, key);
        if (val == null)
            return dflt;
        return Long.parseLong(val);
    }

    protected static int parseJsonInt(JsonObject obj, String key, int dflt) {
        String val = retrieveJsonString (obj, key);
        if (val == null)
            return dflt;
        return Integer.parseInt(val);
    }

    protected static String parseJsonString(JsonObject obj, String key, String dflt) {
        String val = retrieveJsonString (obj, key);
        return (val != null) ? val : dflt;
    }

    protected static JsonObject retrieveJsonObject(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null)
            return null;
        if (v.getValueType() != JsonValue.ValueType.OBJECT)
            return null;
        return (JsonObject)v;
    }

    protected static JsonArray retrieveJsonArray(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null)
            return null;
        if (v.getValueType() != JsonValue.ValueType.ARRAY)
            return null;
        return (JsonArray)v;
    }

    protected static String retrieveJsonString(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null)
            return null;
        if (v.getValueType() != JsonValue.ValueType.STRING)
            return null;
        return ((JsonString)v).getString();
    }

    public abstract Map<String, String> getKeys(boolean alt);

    public final Map<String, String> getKeys() {
        return getKeys(false);
    }

    public Set<LatLon> getAllCoors() {
        Set<LatLon> result = new HashSet<>();
        if (hasOuter())
            result.addAll(m_outer);
        for (List<LatLon> inner: m_inners) {
            result.addAll(inner);
        }
        return result;
    }

    public Bounds getMissingAreaToDownload(DataSet ds, LatLonSize extrasize, LatLonSize downloadsize) {

        List<Bounds> bounds = ds.getDataSourceBounds();
        Bounds result = null;

        if (hasOuter ())
            result = includeMissingAreaToDownload (m_outer, bounds, result, extrasize, downloadsize);

        for (List<LatLon> inner: m_inners)
            result = includeMissingAreaToDownload (inner, bounds, result, extrasize, downloadsize);

        return result;
    }

    private Bounds includeMissingAreaToDownload (List<LatLon> way, List<Bounds> bounds, Bounds result, LatLonSize extrasize, LatLonSize downloadsize) {

        if (way.size () < 2)
            return result;

        LatLon p0;
        boolean p0in;
        LatLon p1 = way.get(0);
        boolean p1in = BBoxUtils.isInsideBounds(p1, bounds, extrasize);

        for (int i = 1; i < way.size (); i++) {
            p0 = p1;
            p0in = p1in;
            p1 = way.get(i);
            p1in = BBoxUtils.isInsideBounds(p1, bounds, extrasize);

            if (p0in && p1in)
                continue;

            Bounds bb = new Bounds (
                    Math.min(p0.lat(), p1.lat()) - downloadsize.latSize(),
                    Math.min(p0.lon(), p1.lon()) - downloadsize.lonSize(),
                    Math.max(p0.lat(), p1.lat()) + downloadsize.latSize(),
                    Math.max(p0.lon(), p1.lon()) + downloadsize.lonSize());

            if (result == null) {
                result = bb;
            } else {
                result.extend(bb);
            }
        }

        return result;
    }

    @Override
    public final boolean containsPoint (LatLon latlon) {

        // must be inside geometry bbox
        BBox bbox = this.getBBox();
        if (!bbox.bounds(latlon))
            return false;

        // must be inside outer way
        if (!polygonContainsPoint (this.getOuter(), latlon))
            return false;

        // must not be inside inner ways
        for (List<LatLon> inner: this.getInners()) {
            if (polygonContainsPoint (inner, latlon))
                return false;
        }

        return true;
    }

    private boolean polygonContainsPoint(List<LatLon> way, LatLon latlon) {
        // (stolen from JOSM's Geometry, nodeInsidePolygon)

        if (way.size() < 2)
            return false;

        boolean inside = false;
        EastNorth p1, p2;
        EastNorth pointEn = getEastNorth (latlon);

        // iterate each side of the polygon, start with the last segment
        LatLon oldPoint = way.get(way.size() - 1);
        EastNorth oldEn = getEastNorth(oldPoint);

        for (LatLon newPoint : way) {
            //skip duplicate points
            if (newPoint.equals(oldPoint)) {
                continue;
            }

            EastNorth newEn = getEastNorth(newPoint);

            // order points so p1.lat <= p2.lat
            if (newEn.getY() > oldEn.getY()) {
                p1 = oldEn;
                p2 = newEn;
            } else {
                p1 = newEn;
                p2 = oldEn;
            }

            //test if the line is crossed and if so invert the inside flag.
            if ((newEn.getY() < pointEn.getY()) == (pointEn.getY() <= oldEn.getY())
                && (pointEn.getX() - p1.getX()) * (p2.getY() - p1.getY())
                < (p2.getX() - p1.getX()) * (pointEn.getY() - p1.getY()))
            {
                inside = !inside;
            }

            oldPoint = newPoint;
            oldEn = newEn;
        }

        return inside;
    }

    private EastNorth getEastNorth (LatLon latlon) {
        return Projections.project(latlon);
    }
}
