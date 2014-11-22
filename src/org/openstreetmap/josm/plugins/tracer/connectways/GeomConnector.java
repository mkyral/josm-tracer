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


import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.tools.Geometry;


public class GeomConnector {

    private final double m_duplicateNodesPrecision;

    private final double m_metersPerDegree = 111120.00071117;

    private final double m_pointOnLineToleranceMeters;        // 0.2 for LPIS
    private final double m_pointOnLineMaxLateralAngle;        // Math.PI / 3 for LPIS, must be < Pi/2
    private final double m_pointOnLineToleranceDegrees;

    private final double m_nearNodeToleranceMeters;
    private final double m_nearNodeToleranceDegrees;

    public GeomConnector (double point_on_line_tolerance_meters, double point_on_line_max_lateral_angle) {
        m_duplicateNodesPrecision = Main.pref.getDouble("validator.duplicatenodes.precision", 0.0);
        m_pointOnLineToleranceMeters = point_on_line_tolerance_meters;
        m_pointOnLineMaxLateralAngle = point_on_line_max_lateral_angle;
        m_pointOnLineToleranceDegrees = m_pointOnLineToleranceMeters/m_metersPerDegree;

        m_nearNodeToleranceMeters = m_pointOnLineToleranceMeters;
        m_nearNodeToleranceDegrees = m_nearNodeToleranceMeters/m_metersPerDegree;
    }

    public double pointOnLineToleranceDegrees() {
        return m_pointOnLineToleranceDegrees;
    }

    public double nearNodeToleranceDegrees() {
        return m_nearNodeToleranceDegrees;
    }

    public double nearNodeToleranceMeters() {
        return m_nearNodeToleranceMeters;
    }

    public double distanceOfNodesMeters(EdNode x, EdNode y) {
        return x.getCoor().greatCircleDistance(y.getCoor());
    }

    public boolean duplicateNodes(LatLon l1, LatLon l2) {
        return (l1 == l2) ||
            (l1 != null && l2 != null && roundCoor(l1).equals(roundCoor(l2)));
    }

    private LatLon roundCoor(LatLon coor) {
        if (m_duplicateNodesPrecision == 0)
            return coor.getRoundedToOsmPrecision();
        return new LatLon(
            Math.round(coor.lat() / m_duplicateNodesPrecision) * m_duplicateNodesPrecision,
            Math.round(coor.lon() / m_duplicateNodesPrecision) * m_duplicateNodesPrecision);
    }

    public boolean pointOnLine(EdNode p, EdNode x, EdNode y) {
        return pointOnLine(p.currentNodeUnsafe(), x.currentNodeUnsafe(), y.currentNodeUnsafe());
    }

    public double distanceToSegmentMeters(EdNode p, EdNode x, EdNode y) {
        return distanceToSegmentMeters(p.currentNodeUnsafe(), x.currentNodeUnsafe(), y.currentNodeUnsafe());
    }


    public double distanceToSegmentMeters(Node p, Node x, Node y) {
        EastNorth ep = p.getEastNorth();
        EastNorth ex = x.getEastNorth();
        EastNorth ey = y.getEastNorth();

        EastNorth cp = Geometry.closestPointToSegment(ex, ey, ep);
        return p.getCoor().greatCircleDistance(Projections.inverseProject(cp));
    }

    public boolean pointOnLine(Node p, Node x, Node y) {

        EastNorth ep = p.getEastNorth();
        EastNorth ex = x.getEastNorth();
        EastNorth ey = y.getEastNorth();

        EastNorth cp = Geometry.closestPointToSegment(ex, ey, ep);
        if (p.getCoor().greatCircleDistance(Projections.inverseProject(cp)) > m_pointOnLineToleranceMeters)
            return false;

        double a1 = unorientedAngleBetween(x, y, p);
        double a2 = unorientedAngleBetween(y, x, p);
        double limit = m_pointOnLineMaxLateralAngle;
        return (a1 < limit && a2 < limit);
    }

    private double unorientedAngleBetween(Node p0, Node p1, Node p2) {
        double a1 = p1.getCoor().heading(p0.getCoor());
        double a2 = p1.getCoor().heading(p2.getCoor());
        double angle = Math.abs(a2 - a1) % (2 * Math.PI);
        if (angle < 0)
            angle += 2 * Math.PI;
        if (angle > Math.PI)
            angle = (2 * Math.PI) - angle;
        return angle;
    }

}


