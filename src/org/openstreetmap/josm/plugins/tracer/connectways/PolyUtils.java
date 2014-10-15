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
import java.util.Collections;

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
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.data.coor.EastNorth;

import com.seisw.util.geom.*;

public final class PolyUtils {
    
    public static Pair<List<List<EdNode>>, List<List<EdNode>>> polygonDifference (WayEditor editor, EdWay clip_way, EdWay subject_way) {
        Map<EastNorth, EdNode> nodes_map = new HashMap<EastNorth, EdNode>();

        Poly clip = wayToPoly (clip_way, nodes_map);
        Poly subject = wayToPoly (subject_way, nodes_map);

        PolyDefault result = (PolyDefault)Clip.difference (subject, clip);

        if (result.getNumInnerPoly () == 1) {
            List<EdNode> outer = polyToEdNodes (editor, result, nodes_map);
            List<List<EdNode>> outers = new ArrayList<List<EdNode>>();
            outers.add (outer);
            return new Pair <List<List<EdNode>>, List<List<EdNode>>> (outers, Collections.<List<EdNode>>emptyList());
        }
        else {
            List<List<EdNode>> outers = new ArrayList<List<EdNode>>();
            List<List<EdNode>> inners = new ArrayList<List<EdNode>>();
            for (int pi = 0; pi < result.getNumInnerPoly (); pi++)
            {
                Poly p = result.getInnerPoly (pi);
                List<EdNode> list = polyToEdNodes (editor, p, nodes_map);
                if (list.size() >= 3) {
                    if (p.isHole())
                        inners.add(list);
                    else
                        outers.add(list);
                }
            }
            return new Pair <List<List<EdNode>>, List<List<EdNode>>> (outers, inners);
        }
    }

    private static Poly wayToPoly (EdWay w, Map<EastNorth, EdNode> nodes_map)
    {
        return wayToPoly (w, nodes_map, false);
    }

    private static Poly wayToPoly (EdWay w, Map<EastNorth, EdNode> nodes_map, boolean hole)
    {
        if (!w.isClosed())
            throw new IllegalArgumentException (tr("Way must be closed"));

        Poly p = new PolyDefault (hole);
        for (int i = 0; i < w.getNodesCount() - 1; i++)
        {
            EdNode node = w.getNode(i);
            EastNorth east_north = node.getEastNorth();
            p.add (east_north.getX(), east_north.getY());
            nodes_map.put(east_north, node);
        }
        return p;
    }

    private static List<EdNode> polyToEdNodes (WayEditor editor, Poly p, Map<EastNorth, EdNode> nodes_map)
    {
        System.out.println("d: poly:");
        List<EdNode> list = new ArrayList<EdNode> ();

        LatLon prev_coor = null;
        for (int i = 0; i < p.getNumPoints(); i++)
        {
            EastNorth east_north = new EastNorth(p.getX(i), p.getY(i));
            EdNode node = nodes_map.get(east_north);
            if (node == null) {
                LatLon ll = Projections.inverseProject(east_north);
                node = editor.newNode(ll);
                System.out.println(" -  + new node " + Long.toString(node.getUniqueId()));
            }
            // avoid two consecutive duplicate nodes ..x,x..
            if (!almostEquals(node.getCoor(), prev_coor)) {
                list.add(node);
                prev_coor = node.getCoor();
                System.out.println(" - d: node " + Long.toString(node.getUniqueId()));
            }
        }

        // #### Remove false degnerated tails of the form ..x,y,z.., where either "z" is
        // on line "xy" or "x" is on line "yz".
        // Together with true degenerated tails, these two fixes should be repeatedly 
        // applied as long as something changes.

        // remove degenerated tails of the form ..x,y,x..
        int i = 0;
        while ((list.size() >= 3) && i < list.size ()) {
            int i1 = (i + 1) % list.size();
            int i2 = (i + 2) % list.size();
            if (almostEquals(list.get(i).getCoor(), list.get(i2).getCoor())) {
                System.out.println(" x d: tail " + Long.toString(list.get(i).getUniqueId()));
                list.remove(i1);
                list.remove(i2 > i1 ? i1 : 0);
                i = i >= 2 ? i - 2 : 0;
            }
            else {
                i++;
            }
        }
        if (list.size() < 3)
            list.clear();

        return list;
    }

    private static boolean almostEquals(LatLon l1, LatLon l2) {
        return l1 != null && l2 != null && 
            l1.distance(l2) < 0.0000005;
    }

    private static List<LatLon> polyToSanitizedList (Poly p) {
        LatLon prev = null;
        List<LatLon> list = new ArrayList<LatLon> (p.getNumPoints());

        // remove duplicates
        for (int i = 0; i < p.getNumPoints(); i++)
        {
            EastNorth east_north = new EastNorth(p.getX(i), p.getY(i));
            LatLon ll = Projections.inverseProject(east_north);
            if (!ll.equals(prev)) {
                list.add(ll);
                prev = ll;
            }
        }


        return list;
    }
}



