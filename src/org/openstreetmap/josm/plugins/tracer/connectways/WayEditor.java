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


public class WayEditor {

    private double s_dMinDistance      = 0.0000005; // Minimal distance, for objects

    private final long m_idGuard;
    private final DataSet m_dataSet;
    private final GeomUtils m_geomUtils;

    private final Set<EdNode> m_nodes;
    private final Set<EdWay> m_ways;
    private final Set<EdMultipolygon> m_multipolygons;
    private final HashMap<Long, EdNode> m_originalNodes;
    private final HashMap<Long, EdWay> m_originalWays;
    private final HashMap<Long, EdMultipolygon> m_originalMultipolygons;

    public WayEditor(DataSet dataset, GeomUtils geom_utils) {
        m_dataSet = dataset;
        m_idGuard = (new Node()).getUniqueId();
        m_geomUtils = geom_utils;
        m_nodes = new HashSet<EdNode> ();
        m_ways = new HashSet<EdWay> ();
        m_multipolygons = new HashSet<EdMultipolygon> ();
        m_originalNodes = new HashMap<Long, EdNode> ();
        m_originalWays = new HashMap<Long, EdWay> ();
        m_originalMultipolygons = new HashMap<Long, EdMultipolygon> ();
    }

    public GeomUtils geomUtils() {
        return m_geomUtils;
    }

    public DataSet getDataSet() {
        return m_dataSet;
    }
    
    public EdNode newNode(LatLon latlon) {
        EdNode node = new EdNode(this, latlon);        
        m_nodes.add(node);
        return node;
    }

    public EdWay newWay(List<EdNode> nodes) {
        EdWay way = new EdWay(this, nodes);
        m_ways.add(way);
        return way;
    }

    public EdMultipolygon newMultipolygon() {
        EdMultipolygon multipolygon = new EdMultipolygon(this);
        m_multipolygons.add(multipolygon);
        return multipolygon;
    }

    public EdNode useNode(Node node) {
        if (node == null)
            throw new IllegalArgumentException();
        if (node.getUniqueId() <= m_idGuard)
            throw new IllegalArgumentException(tr("Non-original Node created outside WayEditor scope"));
        if (node.getDataSet() != this.getDataSet())
            throw new IllegalArgumentException(tr("Cannot use Node from with a different/null DataSet"));
        
        EdNode en = m_originalNodes.get(node.getUniqueId());
        if (en != null) {
            if (en.originalNode() != node)
                throw new IllegalArgumentException(tr("Original Node ID mapped to a different Node object"));
            return en;
        }

        en = new EdNode(this, node);
        m_originalNodes.put(node.getUniqueId(), en);
        m_nodes.add(en);
        return en;
    }

    public EdWay useWay(Way way) {
        if (way == null)
            throw new IllegalArgumentException();
        if (way.getUniqueId() <= m_idGuard)
            throw new IllegalArgumentException(tr("Non-original Way created outside WayEditor scope"));
        if (way.getDataSet() != this.getDataSet())
            throw new IllegalArgumentException(tr("Cannot use Way from with a different/null DataSet"));

        EdWay ew = m_originalWays.get(way.getUniqueId());
        if (ew != null) {
            if (ew.originalWay() != way)
                throw new IllegalArgumentException(tr("Original Way ID mapped to a different Way object"));
            return ew;
        }

        ew = new EdWay(this, way);
        m_originalWays.put(way.getUniqueId(), ew);
        m_ways.add(ew);
        return ew;
    }

    boolean isEdited(OsmPrimitive prim) {
        if (prim instanceof Node) {
            EdNode en = m_originalNodes.get(prim.getUniqueId());
            return en != null && en.originalNode() == prim;
        }
        if (prim instanceof Way) {
            EdWay ew = m_originalWays.get(prim.getUniqueId());
            return ew != null && ew.originalWay() == prim;
        }
        if (prim instanceof Relation) {
            EdMultipolygon emp = m_originalMultipolygons.get(prim.getUniqueId());
            return emp != null && emp.originalMultipolygon() == prim;
        }
        return false;
    }

    boolean ownedByEditor(List<EdNode> list) {
        if (list == null)
            return true;
        for (EdObject obj: list)
            if (obj.getEditor() != this)
                return false;
        return true;
    }

    boolean ownedByEditor(EdObject obj) {
        if (obj == null)
            return false;
        return obj.getEditor() == this;
    }

    Set<EdNode> findExistingNodesTouchingWaySegment(LatLon x, LatLon y, IEdNodePredicate filter) {
        double oversize = geomUtils().pointOnLineTolerance() * 10;
        BBox bbox = new BBox (new Node(x));
        bbox.addPrimitive (new Node(y), oversize);
        bbox.addPrimitive (new Node(x), oversize);

        Set<EdNode> result = new HashSet<EdNode>();

        // (1) original nodes that are not tracked yet
        for (Node nd : getDataSet().searchNodes(bbox)) {
            if (!nd.isUsable() || nd.isOutsideDownloadArea())
                continue;
            if (isEdited(nd))
                continue;
            if (!filter.evaluate(nd))
                continue;
            if (!geomUtils().pointOnLine(nd.getCoor(), x, y))
                continue;
            result.add(useNode(nd));
        }

        // (2) edited nodes
        for (EdNode ednd: searchEdNodes(bbox)) {
            if (ednd.isDeleted())
                continue;
            if (!ednd.hasEditorReferrers())
                continue;
            if (!filter.evaluate(ednd))
                continue;
            if (!geomUtils().pointOnLine(ednd.getCoor(), x, y))
                continue;
            result.add(ednd);
        }

        return result;
    }

    EdNode findExistingNodeForDuplicateMerge(EdNode src, IEdNodePredicate filter) {
        if (src == null || src.isDeleted())
            throw new IllegalArgumentException();

        BBox bbox = src.getBBox(s_dMinDistance);

        // (1) original nodes that are either not tracked or didn't move from original position;
        //     prefer edited EdNodes, use a matching one with the highest ID
        Node node1 = null;
        EdNode ednode1 = null;
        for (Node nd : getDataSet().searchNodes(bbox)) {
            if (!nd.isUsable() || nd.isOutsideDownloadArea())
                continue;
            EdNode ednd = m_originalNodes.get(nd.getUniqueId());
            if (ednd != null) {
                if (ednd.isDeleted())
                    continue;
                if (src == ednd || ((m_geomUtils.duplicateNodes(ednd.getCoor(), src.getCoor()) && filter.evaluate(ednd)))) {
                    if (ednode1 == null)
                        ednode1 = ednd;
                    else if (ednd.originalNode().getUniqueId() > ednode1.originalNode().getUniqueId())
                        ednode1 = ednd;
                }
            }
            else {
                if ((m_geomUtils.duplicateNodes(nd.getCoor(), src.getCoor()) && filter.evaluate(nd))) {
                    if (node1 == null)
                        node1 = nd;
                    else if (nd.getUniqueId() > node1.getUniqueId())
                        node1 = nd;
                }
            }
        }
        if (ednode1 != null) {
            if (ednode1 == src)
                return null; // best match is the source node itself, nothing do merge
            else
                return ednode1;
        }
        if (node1 != null)
            return useNode(node1);

        // (2) look in edited nodes; prefer nodes having an original Node over new nodes, 
        //     use a matching one with the highest ID
        EdNode ornode2 = null;
        EdNode nwnode2 = null;
        for (EdNode ednd: searchEdNodes(bbox)) {
            if (ednd.isDeleted())
                continue;
            if (!ednd.hasEditorReferrers())
                continue;
            if (src == ednd || ((m_geomUtils.duplicateNodes(ednd.getCoor(), src.getCoor()) && filter.evaluate(ednd)))) {
                if (ednd.hasOriginal()) {
                    if (ornode2 == null)
                        ornode2 = ednd;
                    else if (ednd.originalNode().getUniqueId() > ornode2.originalNode().getUniqueId())
                        ornode2 = ednd;
                }
                else {
                    if (nwnode2 == null)
                        nwnode2 = ednd;
                    else if (ednd.getUniqueId() > nwnode2.getUniqueId())
                        nwnode2 = ednd;
                }
            }
        }
        EdNode node3 = (ornode2 != null ? ornode2 : nwnode2);
        if (node3 == src)
            return null; // best match is the source node itself, nothing to merge

        return node3;
    }

    public List<Command> finalizeEdit () {

        // get ways and nodes required in the resulting DataSet
        Set<EdWay> required_ways = new HashSet<EdWay> ();
        Set<EdNode> required_nodes = new HashSet<EdNode> ();
        for (EdWay w: m_ways) {
            if (w.isDeleted())
                continue;
            System.out.println("Considering way: " + Long.toString(w.getUniqueId()));
            if (w.hasEditorReferrers() || w.isTagged() || w.hasExternalReferrers()) {
                System.out.println(" - required way: " + Long.toString(w.getUniqueId()));
                required_ways.add(w);
                for (int i = 0; i < w.getNodesCount(); i++)
                    required_nodes.add(w.getNode(i));
            }
        }
        for (EdNode n: m_nodes) {
            if (n.isDeleted())
                continue;
            if (required_nodes.contains(n))
                continue;
            if (n.isTagged() || n.hasExternalReferrers())
                required_nodes.add(n);
        }

        // nodes to be added/changed
        Set<EdNode> add_nodes = new HashSet<EdNode> ();
        Set<EdNode> change_nodes = new HashSet<EdNode> ();
        for (EdNode n: required_nodes) {
            if (!n.hasOriginal())
                add_nodes.add(n);
            else if (n.isModified())
                change_nodes.add(n);
        }

        // original nodes to be deleted
        Set<EdNode> delete_nodes = new HashSet<EdNode> ();
        for (EdNode n: m_nodes) {
            if (!n.hasOriginal())
                continue;
            if (required_nodes.contains(n))
                continue;
            delete_nodes.add(n);
        }

        // ways to be added/changed
        Set<EdWay> add_ways = new HashSet<EdWay>();
        Set<EdWay> change_ways = new HashSet<EdWay>();
        for (EdWay w: required_ways) {
            if (!w.hasOriginal() && !w.isDeleted())
                add_ways.add(w);
            else if (w.hasOriginal() && !w.isDeleted() && w.isModified()) {
                change_ways.add(w);
                System.out.println("Change way: " + Long.toString(w.getUniqueId()));
            }
        }

        // ways to be deleted
        Set<EdWay> delete_ways = new HashSet<EdWay>();
        for (EdWay w: m_ways) {
            if (!w.hasOriginal())
                continue;
            if (required_ways.contains(w))
                continue;
            delete_ways.add(w);
        }

        List<Command> cmds = new LinkedList<Command>();

        // commands to add new nodes
        for (EdNode n: add_nodes)
            cmds.add(new AddCommand(n.finalNode()));

        // commands to change original nodes
        for (EdNode n: change_nodes)
            cmds.add(new ChangeCommand(n.originalNode(), n.finalNode()));

        // commands to add new ways
        for (EdWay w: add_ways)
            cmds.add(new AddCommand(w.finalWay()));

        // commands to change original ways
        for (EdWay w: change_ways)
            cmds.add(new ChangeCommand(w.originalWay(), w.finalWay()));

        // multipolygon commands
        for (EdMultipolygon emp: m_multipolygons) {
            if (!emp.hasOriginal() && !emp.isDeleted()) {
                cmds.add(new AddCommand(emp.finalMultipolygon()));
            }
            else if (emp.hasOriginal() && emp.isDeleted() && emp.isModified()) {
                cmds.add(new ChangeCommand(emp.originalMultipolygon(), emp.finalMultipolygon()));
            }
            else if (emp.hasOriginal() && emp.isDeleted()) {
                cmds.add(new DeleteCommand(emp.finalMultipolygon()));
            }
        }

        // commands to delete original ways
        for (EdWay w: delete_ways)
            cmds.add(new DeleteCommand(w.originalWay()));

        // commands to delete original nodes
        for (EdNode n: delete_nodes)
            cmds.add(new DeleteCommand(n.originalNode()));

        return cmds;
    }

    private List<EdNode> searchEdNodes(BBox bbox) {
        List<EdNode> result = new ArrayList<EdNode>();
        for (EdNode ednd: m_nodes) {
            if (ednd.isDeleted())
                continue;
            if (bbox.bounds(ednd.getCoor()))
                result.add(ednd);
        }
        return result;
    }

    public boolean insideDataSourceBounds(EdNode node) {
        List<Bounds> bounds = getDataSet().getDataSourceBounds();
        for (Bounds b: bounds) {
            if (b.contains(node.getCoor()))
                return true;
        }
        return false;
    }

    public List<EdObject> useAllAreasInBBox(BBox bbox, IEdWayPredicate filter) {
        // #### add multipolygons support!!!
        // it will need proper handling of incomplete multipolygons in EdMultipolygon!!

        List<EdObject> list = new ArrayList<EdObject>();

        for (EdWay w: m_ways) {
            if (w.isDeleted())
                continue;
            if (!bbox.intersects(w.getBBox(s_dMinDistance)))
                continue;
            if (!filter.evaluate(w))
                continue;
            if (w.isMemberOfAnyMultipolygon()) // #### multipolygons are skipped now here
                continue;
            list.add(w);
        }

        for (Way w : getDataSet().searchWays(bbox)) {
            if (!w.isUsable())
                continue;
            if (isEdited(w))
                continue;
            if (!filter.evaluate(w))
                continue;            

            // #### multipolygons are skipped now here
            {
                List<Relation> relations = OsmPrimitive.getFilteredList(w.getReferrers(), Relation.class);
                boolean is_member_of_any_multipolygon = false;
                for (Relation rel: relations) {
                    if (MultipolygonMatch.match(rel)) {
                        is_member_of_any_multipolygon = true;
                        break;
                    }
                }
                if (is_member_of_any_multipolygon)
                    continue;
            }

            list.add(useWay(w));
        }
        return list;
    }
}


