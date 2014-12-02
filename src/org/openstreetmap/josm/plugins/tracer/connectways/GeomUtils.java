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

package org.openstreetmap.josm.plugins.tracer.connectways;

import java.util.List;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.tools.Geometry;

public abstract class GeomUtils {

    public static final double metersPerDegree = 111120.00071117;

    /**
     * Calculates approximate planar area determined by the given polygon,
     * in east/north coordinates. Relies on current projection and works
     * only for small areas. Use it only for relative comparisons of areas!
     *
     * Both closed and non-closed polygons are supported.
     *
     * @param nodes List of points defining the polygon
     * @return Calculated area
     */
    public static double getEastNorthArea(List<EdNode> nodes) {

        int count = nodes.size();

        // for closed ways, ignore last node
        if (count > 1 && nodes.get(0).equals(nodes.get(count - 1))) {
            --count;
        }

        if (count <= 2) {
            return 0;
        }

        double area = 0;
        EastNorth ej = nodes.get(count - 1).getEastNorth();
        for (int i = 0; i < count; ++i) {
            EastNorth ei = nodes.get(i).getEastNorth();
            area += (ej.getX() + ei.getX()) * (ej.getY() - ei.getY());
            ej = ei;
        }
        return Math.abs(area / 2);
    }

    /**
     * Calculates unoriented angle between two line segments, in radians.
     * The angle is in range 0 .. PI.
     * @param p0 First point of the first segment
     * @param p1 Vertex point, shared by both segments
     * @param p2 Second point of the second segment
     * @return Angle in radians
     */
    public static double unorientedAngleBetween (EdNode p0, EdNode p1, EdNode p2) {
        return unorientedAngleBetween(p0.currentNodeUnsafe(), p1.currentNodeUnsafe(), p2.currentNodeUnsafe());
    }

    /**
     * Calculates unoriented angle between two line segments, in radians.
     * The angle is in range 0 .. PI.
     * @param p0 First point of the first segment
     * @param p1 Vertex point, shared by both segments
     * @param p2 Second point of the second segment
     * @return Angle in radians
     */
    public static double unorientedAngleBetween(Node p0, Node p1, Node p2) {
        double a1 = p1.getCoor().heading(p0.getCoor());
        double a2 = p1.getCoor().heading(p2.getCoor());
        double angle = Math.abs(a2 - a1) % (2 * Math.PI);
        if (angle < 0)
            angle += 2 * Math.PI;
        if (angle > Math.PI)
            angle = (2 * Math.PI) - angle;
        return angle;
    }

    /**
     * Calculates distance of a point to a line segment.
     * Distance is calculated as a great circle distance between <code>point</code>
     * and the closest point within the line segment.
     *
     * @param point Point for which the minimal distance is calculated
     * @param segp1 First point determining the line segment
     * @param segp2 Second point determining the line segment
     * @return Distance in meters
     */
    public static double distanceToSegmentMeters(EdNode point, EdNode segp1, EdNode segp2) {
        return distanceToSegmentMeters(point.currentNodeUnsafe(), segp1.currentNodeUnsafe(), segp2.currentNodeUnsafe());
    }

    /**
     * Calculates distance of a point to a line segment.
     * Distance is calculated as a great circle distance between <code>point</code>
     * and the closest point within the line segment.
     *
     * @param point Point for which the minimal distance is calculated
     * @param segp1 First point determining the line segment
     * @param segp2 Second point determining the line segment
     * @return Distance in meters
     */
    public static double distanceToSegmentMeters(Node point, Node segp1, Node segp2) {
        EastNorth cp = Geometry.closestPointToSegment(
                segp1.getEastNorth(), segp2.getEastNorth(), point.getEastNorth());
        return point.getCoor().greatCircleDistance(Projections.inverseProject(cp));
    }

    /**
     * Returns distance and angle deviations of a point from a line segment.
     * Distance deviation is the minimal distance of the point to the line. Angle
     * deviation is the greater of unoriented vertex angles (point, segp1, segp2) and
     * (point, segp2, segp1).
     * @param point Point for which the deviation is calculated
     * @param segp1 First point determining the line segment
     * @param segp2 Second point determining the line segment
     * @return Calculated GeomDeviation
     */
    public static GeomDeviation pointDeviationFromSegment(EdNode point, EdNode segp1, EdNode segp2) {
        return pointDeviationFromSegment(point.currentNodeUnsafe(), segp1.currentNodeUnsafe(), segp2.currentNodeUnsafe());
    }

    /**
     * Returns distance and angle deviations of a point from a line segment.
     * Distance deviation is the minimal distance of the point to the line. Angle
     * deviation is the greater of unoriented vertex angles (point, segp1, segp2) and
     * (point, segp2, segp1).
     * @param point Point for which the deviation is calculated
     * @param segp1 First point determining the line segment
     * @param segp2 Second point determining the line segment
     * @return Calculated GeomDeviation
     */
    public static GeomDeviation pointDeviationFromSegment(Node point, Node segp1, Node segp2) {
        EastNorth ep = point.getEastNorth();
        EastNorth ex = segp1.getEastNorth();
        EastNorth ey = segp2.getEastNorth();

        EastNorth cp = Geometry.closestPointToSegment(ex, ey, ep);
        double dev_distance_meters = point.getCoor().greatCircleDistance(Projections.inverseProject(cp));

        double a1 = GeomUtils.unorientedAngleBetween(segp1, segp2, point);
        double a2 = GeomUtils.unorientedAngleBetween(segp2, segp1, point);
        double dev_angle = Math.max(a1, a2);

        return new GeomDeviation (dev_distance_meters, dev_angle);
    }

    public static double distanceOfNodesMeters(EdNode x, EdNode y) {
        return x.getCoor().greatCircleDistance(y.getCoor());
    }

    private static LatLon roundCoor(LatLon coor, double precision) {
        if (precision <= 0)
            return coor.getRoundedToOsmPrecision();
        return new LatLon(
            Math.round(coor.lat() / precision) * precision,
            Math.round(coor.lon() / precision) * precision);
    }

    public static boolean duplicateNodes(LatLon l1, LatLon l2, double precision) {
        return (l1 == l2) ||
            (l1 != null && l2 != null && roundCoor(l1, precision).equals(roundCoor(l2, precision)));
    }

    public static double duplicateNodesPrecision() {
        return Main.pref.getDouble("validator.duplicatenodes.precision", 0.0);
    }

    public static double getMetersPerDegreeOfLatitude (LatLon ll) {
        double phi = Math.toRadians(ll.lat());
        return 111132.954 - 559.822 * Math.cos(2 * phi) + 1.175 * Math.cos(4 * phi);
    }

    public static double getMetersPerDegreeOfLongitude(LatLon ll) {
        double phi = Math.toRadians(ll.lat());
        double a = Ellipsoid.WGS84.a;
        double e2 = Ellipsoid.WGS84.e2;
        double v1 = Math.PI * a * Math.cos(phi);
        double sinphi = Math.sin(phi);
        double v2 = 180.0 * Math.sqrt(1 - e2 * sinphi * sinphi);
        return v1/v2;
    }
}
