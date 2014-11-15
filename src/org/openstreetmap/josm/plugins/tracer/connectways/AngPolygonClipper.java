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
    
    private List<List<EdNode>> m_outers;
    private List<List<EdNode>> m_inners;
    
    private Map<Point2d, EdNode> m_nodesMap;

    public AngPolygonClipper (WayEditor editor) {
        
        m_editor = editor;
        
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
    
    @SuppressWarnings("CallToPrintStackTrace")
    public void polygonDifference (EdWay clip_way, EdObject subj) {

        // initialize collections
        m_outers = new ArrayList<>();
        m_inners = new ArrayList<>();
        m_nodesMap = new HashMap<>();        
    
        try {
       
            Clipper clipper = new Clipper(Clipper.ioStrictlySimple);
            clipper.addPath(wayToPath(clip_way), PolyType.ptClip, true);
            clipper.addPaths(edObjectToPaths(subj), PolyType.ptSubject, true);

            PolyTree ptree = new PolyTree();
            clipper.execute(ClipType.ctDifference, ptree);

            List<PolyNode> pnodes = ptree.getChilds();
            for (PolyNode pn: pnodes) {
                processPolyNode(pn);
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
    
    private void processPolyNode(PolyNode pn) {
        Path path = pn.getContour();
        boolean hole = pn.isHole();
        List<EdNode> nodes = pathToEdNodes(path);
        if (nodes.size() > 3) {
            if (hole)
                m_inners.add(Collections.unmodifiableList(nodes));
            else
                m_outers.add(Collections.unmodifiableList(nodes));
        }
        
        if (pn.getChildCount() > 0) {        
            List<PolyNode> children = pn.getChilds();
            for (PolyNode child: children) {
                processPolyNode(child);
            }
        }
    }
    
    private List<EdNode> pathToEdNodes (Path p)
    {
        //System.out.println("d: poly:");
        List<EdNode> list = new ArrayList<> ();

        LatLon prev_coor = null;
        for (Point2d point : p) {
            EdNode node = point2dToNode(point);
            // avoid two consecutive duplicate nodes ..x,x..
            if (!m_editor.geomUtils().duplicateNodes(node.getCoor(), prev_coor)) {
                list.add(node);
                prev_coor = node.getCoor();
                //System.out.println(" - d: node " + Long.toString(node.getUniqueId()));
            }
        }

        // Avoid consecutive duplicate nodes around the end of polygon
        while ((list.size() >= 2) &&
               (m_editor.geomUtils().duplicateNodes(list.get(0).getCoor(), list.get(list.size() - 1).getCoor())))
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
        m_nodesMap.put(pt, node);
        return pt;
    }
    
    private EdNode point2dToNode(Point2d pt) {
        EdNode node = m_nodesMap.get(pt);
        if (node != null)
            return node;
        double x = ((double)pt.X) / fixedPointScale;
        double y = ((double)pt.Y) / fixedPointScale;
        EastNorth en = new EastNorth (x,y);
        LatLon ll = Projections.inverseProject(en);
        node = m_editor.newNode(ll);
        m_nodesMap.put(pt, node);
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

