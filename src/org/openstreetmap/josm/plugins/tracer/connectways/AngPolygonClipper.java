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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.plugins.tracer.clipper.ClipType;
import org.openstreetmap.josm.plugins.tracer.clipper.Clipper;
import org.openstreetmap.josm.plugins.tracer.clipper.ClipperException;
import org.openstreetmap.josm.plugins.tracer.clipper.Path;
import org.openstreetmap.josm.plugins.tracer.clipper.Paths;
import org.openstreetmap.josm.plugins.tracer.clipper.Point2d;
import org.openstreetmap.josm.plugins.tracer.clipper.PolyNode;
import org.openstreetmap.josm.plugins.tracer.clipper.PolyTree;
import org.openstreetmap.josm.plugins.tracer.clipper.PolyType;

public class AngPolygonClipper {
    private final WayEditor m_editor;
    private final GeomDeviation m_tolerance;
    private final double m_duplicateNodesPrecision;

    private final double m_DiscardCutoffsPercent;
    private double m_DiscardedPercent;

    private List<List<EdNode>> m_outers;
    private List<List<EdNode>> m_inners;

    private Map<LatLon, EdNode> m_nodesMap;

    public AngPolygonClipper (WayEditor editor, GeomDeviation tolerance, double discard_cutoffs_percent) {

        m_editor = editor;
        m_tolerance = tolerance;
        m_duplicateNodesPrecision = GeomUtils.duplicateNodesPrecision();

        m_DiscardCutoffsPercent = discard_cutoffs_percent;
        m_DiscardedPercent = 0.0;

        m_outers = null;
        m_inners = null;
        m_nodesMap = null;
    }

    public List<List<EdNode>> outerPolygons() {
        if (m_outers == null)
            throw new IllegalStateException();
        return m_outers;
    }

    public List<List<EdNode>> innerPolygons() {
        if (m_inners == null)
            throw new IllegalStateException();
        return m_inners;
    }

    public double discardedPercent() {
        return m_DiscardedPercent;
    }

    @SuppressWarnings("CallToPrintStackTrace")
    public void polygonDifference (EdWay clip_way, EdObject subj) {

        // initialize collections
        m_outers = new ArrayList<>();
        m_inners = new ArrayList<>();
        m_nodesMap = new HashMap<>();

        m_DiscardedPercent = 0.0;

        double subj_area = Double.NaN;
        if (m_DiscardCutoffsPercent > 0.0)
            subj_area = subj.getEastNorthArea();

        try {
            // Note: always preserve collinear nodes! Otherwise, clipper disconnects
            // non-intersecting touching nodes of the subject from other polygons!

            Clipper clipper = new Clipper(Clipper.ioStrictlySimple + Clipper.ioPreserveCollinear);
            clipper.addPath(wayToPath(clip_way), PolyType.ptClip, true);
            clipper.addPaths(edObjectToPaths(subj), PolyType.ptSubject, true);

            PolyTree ptree = new PolyTree();
            clipper.execute(ClipType.ctDifference, ptree);

            List<PolyNode> pnodes = ptree.getChilds();
            for (PolyNode pn: pnodes) {
                processPolyNode(pn, m_outers, m_inners, subj_area);
            }
        }
        catch (ClipperException e) {
            e.printStackTrace();
            m_nodesMap = null;
            m_outers = null;
            m_inners = null;
            throw new AssertionError("AngPolygonClipper.polygonDifference failed, ClipperException", e);
        }

        m_nodesMap = null;
        m_outers = Collections.unmodifiableList(m_outers);
        m_inners = Collections.unmodifiableList(m_inners);
    }

    private void processPolyNode(PolyNode pn, List<List<EdNode>> aouters, List<List<EdNode>> ainners, double subj_area) {

        List<List<EdNode>> outers = aouters;
        List<List<EdNode>> inners = ainners;

        Path path = pn.getContour();
        boolean hole = pn.isHole();
        boolean discard_cutoffs = !hole && m_DiscardCutoffsPercent > 0.0 && subj_area > 0.0;

        if (discard_cutoffs) {
            inners = new ArrayList<>();
            outers = new ArrayList<>();
        }

        List<EdNode> nodes = pathToEdNodes(path);
        if (nodes.size() > 3) {
            if (hole)
                inners.add(Collections.unmodifiableList(nodes));
            else
                outers.add(Collections.unmodifiableList(nodes));
        }

        if (pn.getChildCount() > 0) {
            List<PolyNode> children = pn.getChilds();
            for (PolyNode child: children) {
                processPolyNode(child, outers, inners, subj_area);
            }
        }

        if (!discard_cutoffs)
            return;

        double area = getEastNorthArea(outers, inners);
        double percent = (area/subj_area) * 100.0;
        if (percent >= 0.0 && percent < m_DiscardCutoffsPercent) {
            System.out.println("Discarding cutoff area, percent=" + Double.toString(percent));
            m_DiscardedPercent += percent;
            return;
        }
        else {
            System.out.println("Cutoff out of limit, percent=" + Double.toString(percent));
        }

        aouters.addAll(outers);
        ainners.addAll(inners);
    }

    private double getEastNorthArea(List<List<EdNode>> outers, List<List<EdNode>> inners) {
        double area = 0.0;

        for (List<EdNode> outer: outers) {
            area += GeomUtils.getEastNorthArea(outer);
        }

        for (List<EdNode> inner: inners) {
            area -= GeomUtils.getEastNorthArea(inner);
        }

        return area;
    }

    private List<EdNode> pathToEdNodes (Path p)
    {
        //System.out.println("d: poly:");
        List<EdNode> list = new ArrayList<> ();

        LatLon prev_coor = null;
        for (Point2d point : p) {
            EdNode node = point2dToNode(point);
            // avoid two consecutive duplicate nodes ..x,x..
            if (!GeomUtils.duplicateNodes(node.getCoor(), prev_coor, m_duplicateNodesPrecision)) {
                list.add(node);
                prev_coor = node.getCoor();
                //System.out.println(" - d: node " + Long.toString(node.getUniqueId()));
            }
        }

        // Avoid consecutive duplicate nodes around the end of polygon
        while ((list.size() >= 2) &&
               (GeomUtils.duplicateNodes(list.get(0).getCoor(), list.get(list.size() - 1).getCoor(), m_duplicateNodesPrecision)))
            list.remove(list.size() - 1);

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
                if (GeomUtils.pointDeviationFromSegment(p2, p0, p1).inTolerance(m_tolerance) ||
                    GeomUtils.pointDeviationFromSegment(p0, p1, p2).inTolerance(m_tolerance)) {
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
                if (GeomUtils.duplicateNodes(list.get(i).getCoor(), list.get(i2).getCoor(), m_duplicateNodesPrecision)) {
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

        // Close the polygon
        list.add(list.get(0));

        return list;
    }


    private Path wayToPath(EdWay w) {
        if (!w.isClosed())
            throw new IllegalArgumentException ("Way must be closed");

        Path p = new Path();

        for (int i = 0; i < w.getNodesCount(); i++)
        {
            EdNode node = w.getNode(i);
            p.add (nodeToPoint2d(node));
        }
        return p;
    }

    // #### improve! the scale must be derived from EastNorth precision, which depends on
    // present JOSM projection! Or try to rewrite clipper to floating point...
    private final static double fixedPointScale = 10000000000.0;

    private Point2d nodeToPoint2d(EdNode node) {
        EastNorth en = node.getEastNorth();
        long x = (long)(en.getX() * fixedPointScale);
        long y = (long)(en.getY() * fixedPointScale);
        Point2d pt = new Point2d(x, y);
        m_nodesMap.put(node.getCoor().getRoundedToOsmPrecision(), node);
        return pt;
    }

    private EdNode point2dToNode(Point2d pt) {
        // perform inverse projection to LatLon
        double x = ((double)pt.X) / fixedPointScale;
        double y = ((double)pt.Y) / fixedPointScale;
        EastNorth en = new EastNorth (x,y);
        LatLon ll = Projections.inverseProject(en);

        // lookup in LatLon map
        EdNode node = m_nodesMap.get(ll.getRoundedToOsmPrecision());
        if (node != null)
            return node;

        // create new node
        node = m_editor.newNode(ll);
        m_nodesMap.put(ll, node);
        return node;
    }

    private Paths edObjectToPaths(EdObject obj) {
        if (obj instanceof EdWay) {
            Path p = wayToPath((EdWay)obj);
            Paths pp = new Paths();
            pp.add(p);
            return pp;
        }
        if (obj instanceof EdMultipolygon)
            return multipolygonToPaths((EdMultipolygon)obj);
        throw new IllegalArgumentException("EdObject must be either EdWay or EdMultipolygon");
    }

    private Paths multipolygonToPaths(EdMultipolygon mp) {
        Paths pp = new Paths();
        for (EdWay w: mp.outerWays())
            pp.add(wayToPath(w));
        for (EdWay w: mp.innerWays())
            pp.add(wayToPath(w));
        return pp;
    }

}

