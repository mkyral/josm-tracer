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
import org.openstreetmap.josm.data.osm.Node;


public class GeomConnector {

    private final double m_duplicateNodesPrecision;

    private final GeomDeviation m_tolerance;

    public GeomConnector (GeomDeviation poldev) {
        m_duplicateNodesPrecision = Main.pref.getDouble("validator.duplicatenodes.precision", 0.0);
        m_tolerance = poldev;
    }

    public double pointOnLineToleranceLatLon() {
        return m_tolerance.distanceLatLon();
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
        return GeomUtils.pointDeviationFromSegment(p, x, y).inTolerance(m_tolerance);
    }

    public boolean pointOnLine(Node p, Node x, Node y) {
        return GeomUtils.pointDeviationFromSegment(p, x, y).inTolerance(m_tolerance);
    }
}


