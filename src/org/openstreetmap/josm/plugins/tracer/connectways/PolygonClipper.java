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

public class PolygonClipper {
    
    private final WayEditor m_editor;

    private List<List<EdNode>> m_outerPolygons;
    private List<List<EdNode>> m_innerPolygons;

    private Map<EastNorth, EdNode> m_nodesMap;

    public PolygonClipper (WayEditor editor) {
        m_editor = editor;
        m_outerPolygons = null;
        m_outerPolygons = null;
        m_nodesMap = null;
    }

    public List<List<EdNode>> outerPolygons() {
        if (m_outerPolygons == null)
            throw new IllegalStateException();
        return m_outerPolygons;
    }
    
    public List<List<EdNode>> innerPolygons() {
        if (m_innerPolygons == null)
            throw new IllegalStateException();
        return m_innerPolygons;
    }

    public void polygonDifference (EdWay clip_way, EdWay subject_way) {

        // convert EdWays to Polys, populate nodes map
        m_nodesMap = new HashMap<EastNorth, EdNode>();
        Poly clip = wayToPoly (clip_way);
        Poly subject = wayToPoly (subject_way);

        PolyDefault result = (PolyDefault)Clip.difference (subject, clip);

        m_outerPolygons = new ArrayList<List<EdNode>>();
        m_innerPolygons = new ArrayList<List<EdNode>>();

        if (result.getNumInnerPoly () == 1) {
            List<EdNode> list = polyToEdNodes (result);
            if (list.size() >= 3)
                m_outerPolygons.add (Collections.unmodifiableList(list));
        }
        else {
            for (int pi = 0; pi < result.getNumInnerPoly (); pi++)
            {
                Poly p = result.getInnerPoly (pi);
                List<EdNode> list = polyToEdNodes (p);
                if (list.size() >= 3) {
                    if (p.isHole())
                        m_innerPolygons.add(Collections.unmodifiableList(list));
                    else
                        m_outerPolygons.add(Collections.unmodifiableList(list));
                }
            }
        }

        m_outerPolygons = Collections.unmodifiableList(m_outerPolygons);
        m_innerPolygons = Collections.unmodifiableList(m_innerPolygons);
    }

    private Poly wayToPoly (EdWay w)
    {
        return wayToPoly (w, false);
    }

    private Poly wayToPoly (EdWay w, boolean hole)
    {
        if (!w.isClosed())
            throw new IllegalArgumentException (tr("Way must be closed"));

        Poly p = new PolyDefault (hole);

        for (int i = 0; i < w.getNodesCount() - 1; i++)
        {
            EdNode node = w.getNode(i);
            EastNorth east_north = node.getEastNorth();
            p.add (east_north.getX(), east_north.getY());
            m_nodesMap.put(east_north, node);
        }
        return p;
    }

    private List<EdNode> polyToEdNodes (Poly p)
    {
        //System.out.println("d: poly:");
        List<EdNode> list = new ArrayList<EdNode> ();

        LatLon prev_coor = null;
        for (int i = 0; i < p.getNumPoints(); i++)
        {
            EastNorth east_north = new EastNorth(p.getX(i), p.getY(i));
            EdNode node = m_nodesMap.get(east_north);
            if (node == null) {
                LatLon ll = Projections.inverseProject(east_north);
                node = m_editor.newNode(ll);
                //System.out.println(" -  + new node " + Long.toString(node.getUniqueId()));
            }
            // avoid two consecutive duplicate nodes ..x,x..
            if (!m_editor.geomUtils().duplicateNodes(node.getCoor(), prev_coor)) {
                list.add(node);
                prev_coor = node.getCoor();
                //System.out.println(" - d: node " + Long.toString(node.getUniqueId()));
            }
        }

        boolean changed;
        int i;
        do {
            changed = false;
            // Remove false degnerated tails of the form ..x,y,z.., where either "z" is
            // on line "xy" or "x" is on line "yz".
            i = 0;
            while ((list.size() >= 3) && i < list.size ()) {
                int i1 = (i + 1) % list.size();
                int i2 = (i + 2) % list.size();
                EdNode p0 = list.get(i);
                EdNode p1 = list.get(i1);
                EdNode p2 = list.get(i2);
                if (m_editor.geomUtils().pointOnLine(p2, p0, p1) ||
                    m_editor.geomUtils().pointOnLine(p0, p1, p2)) {
                    list.remove(i1);
                    i = i >= 1 ? i - 1 : 0;
                    changed = true;
                }
                else {
                    i++;
                }
            }

            // Remove degenerated tails of the form ..x,y,x..
            i = 0;
            while ((list.size() >= 3) && i < list.size ()) {
                int i1 = (i + 1) % list.size();
                int i2 = (i + 2) % list.size();
                if (m_editor.geomUtils().duplicateNodes(list.get(i).getCoor(), list.get(i2).getCoor())) {
                    System.out.println(" x d: tail " + Long.toString(list.get(i).getUniqueId()));
                    list.remove(i1);
                    list.remove(i2 > i1 ? i1 : 0);
                    i = i >= 3 ? i - 3 : 0;
                    changed = true;
                }
                else {
                    i++;
                }
            }
        } while (changed);

        // Polygon degenerated to two or one nodes
        if (list.size() < 3) {
            list.clear();
            return list;
        }

        // #### Sometimes, this function produces invalid polygon of the form x1...,xn,...,xn,...x1. That is,
        // a polygon that contains the same node more than once. Fix it and split the polygon to multiple polygons.
        // Be careful, there can be more such nodes in one polygon. There can be subareas degenerated to just a line
        // as well.

        // Close the polygon
        list.add(list.get(0));

        return list;
    }
}



