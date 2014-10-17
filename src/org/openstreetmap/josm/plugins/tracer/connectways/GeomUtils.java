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


public class GeomUtils {

    private final double m_duplicateNodesPrecision;

    public GeomUtils () {
        m_duplicateNodesPrecision = Main.pref.getDouble("validator.duplicatenodes.precision", 0.);
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

    public boolean pointOnLine(LatLon p, LatLon x, LatLon y) {

        // Compare distances
        double xy = x.distance(y);
        double xpy = x.distance(p) + p.distance(y);
        double delta = Math.abs (xpy - xy);
        if (delta >= 0.0000007) // #### magic
            return false;

        // Consider angles as well.
        // Distance test is unreliable if "p" is very close to "x" or "y".
        double a1 = unorientedAngleBetween(x, y, p);
        double a2 = unorientedAngleBetween(y, x, p);
        double limit = Math.PI / 32; // #### magic
        return (a1 <= limit && a2 <= limit);
    }

    public double unorientedAngleBetween(LatLon p0, LatLon p1, LatLon p2) {
        double a1 = p1.heading(p0);
        double a2 = p1.heading(p2);
        double angle = Math.abs(a2 - a1) % (2 * Math.PI);
        if (angle < 0)
            angle += 2 * Math.PI;
        if (angle > Math.PI)
            angle = (2 * Math.PI) - angle;
        return angle;
    }

}


