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

package org.openstreetmap.josm.plugins.tracer.connectways;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.tools.Geometry;


public class GeomUtils {

    private final double m_duplicateNodesPrecision;

    private final double m_metersPerDegree = 111120.00071117;
    private final double m_pointOnLineToleranceMeters = 0.20;        // #### magic, tuned for LPIS, make it optional
    private final double m_pointOnLineMaxLateralAngle = Math.PI / 3; // #### magic, tuned for LPIS, make it optional, must be < Pi/2

	private final double m_pointOnLineToleranceDegrees = m_pointOnLineToleranceMeters/m_metersPerDegree;

    public GeomUtils () {
        m_duplicateNodesPrecision = Main.pref.getDouble("validator.duplicatenodes.precision", 0.);
    }

    public double pointOnLineToleranceDegrees() {
        return m_pointOnLineToleranceDegrees;
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


