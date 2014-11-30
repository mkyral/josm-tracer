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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Geometry;
import static org.openstreetmap.josm.tools.I18n.tr;


public class WayEditor {

    private final double s_dMinDistance      = 0.0000005; // Minimal distance, for objects

    private final double m_duplicateNodesPrecision;

    private final long m_idGuard;
    private final DataSet m_dataSet;

    private final Set<EdNode> m_nodes;
    private final Set<EdWay> m_ways;
    private final Set<EdMultipolygon> m_multipolygons;
    private final HashMap<Long, EdNode> m_originalNodes;
    private final HashMap<Long, EdWay> m_originalWays;
    private final HashMap<Long, EdMultipolygon> m_originalMultipolygons;

    public WayEditor(DataSet dataset) {
        this (dataset, 0.0);
    }


    public WayEditor(DataSet dataset, double resurrect_dist) {
        m_dataSet = dataset;
        m_idGuard = (new Node()).getUniqueId();
        m_nodes = new HashSet<> ();
        m_ways = new HashSet<> ();
        m_multipolygons = new HashSet<> ();
        m_originalNodes = new HashMap<> ();
        m_originalWays = new HashMap<> ();
        m_originalMultipolygons = new HashMap<> ();
        m_duplicateNodesPrecision = GeomUtils.duplicateNodesPrecision();
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
        if (!node.isUsable())
            throw new IllegalArgumentException(tr("Cannot edit unusable Node"));

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
        if (!way.isUsable() || way.hasIncompleteNodes())
            throw new IllegalArgumentException(tr("Cannot edit unusable Way"));

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

    public EdMultipolygon useMultipolygon(Relation rel) {
        if (rel == null)
            throw new IllegalArgumentException();
        if (rel.getUniqueId() <= m_idGuard)
            throw new IllegalArgumentException(tr("Non-original Relation created outside WayEditor scope"));
        if (rel.getDataSet() != this.getDataSet())
            throw new IllegalArgumentException(tr("Cannot use Relation from with a different/null DataSet"));
        if (!rel.isUsable() || rel.hasIncompleteMembers())
            throw new IllegalArgumentException(tr("Cannot edit unusable/incomplete Relation"));
        if (!MultipolygonMatch.match(rel))
            throw new IllegalArgumentException(tr("Relation is not a multipolygon"));

        EdMultipolygon emp = m_originalMultipolygons.get(rel.getUniqueId());
        if (emp != null) {
            if (emp.originalMultipolygon() != rel)
                throw new IllegalArgumentException(tr("Original Relation ID mapped to a different Relation object"));
            return emp;
        }

        emp = new EdMultipolygon(this, rel);
        m_originalMultipolygons.put(rel.getUniqueId(), emp);
        m_multipolygons.add(emp);
        return emp;
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

    Set<EdNode> findExistingNodesTouchingWaySegment(GeomConnector gconn, EdNode x, EdNode y, IEdNodePredicate filter) {
        double oversize = gconn.pointOnLineToleranceLatLon() * 10;
        Node nx = new Node(x.currentNodeUnsafe());
        Node ny = new Node(y.currentNodeUnsafe());
        BBox bbox = new BBox (nx);
        bbox.addPrimitive (ny, oversize);
        bbox.addPrimitive (nx, oversize);

        Set<EdNode> result = new HashSet<>();

        // (1) original nodes that are not tracked yet
        for (Node nd : getDataSet().searchNodes(bbox)) {
            if (!nd.isUsable() || nd.isOutsideDownloadArea())
                continue;
            if (isEdited(nd))
                continue;
            if (!filter.evaluate(nd))
                continue;
            if (!gconn.pointOnLine(nd, nx, ny))
                continue;
            result.add(useNode(nd));
        }

        // (2) edited nodes
        for (EdNode ednd: searchEdNodes(bbox)) {
            if (ednd.isDeleted())
                continue;
            if (!filter.evaluate(ednd))
                continue;
            if (!gconn.pointOnLine(ednd.currentNodeUnsafe(), nx, ny))
                continue;
            result.add(ednd);
        }

        return result;
    }

    Set<EdNode> findExistingNodesInBBox(BBox bbox, IEdNodePredicate filter) {

        Set<EdNode> result = new HashSet<>();

        // (1) original nodes that are not tracked yet
        for (Node nd : getDataSet().searchNodes(bbox)) {
            if (!nd.isUsable() || nd.isOutsideDownloadArea())
                continue;
            if (isEdited(nd))
                continue;
            if (!filter.evaluate(nd))
                continue;
            result.add(useNode(nd));
        }

        // (2) edited nodes
        for (EdNode ednd: searchEdNodes(bbox)) {
            if (ednd.isDeleted())
                continue;
            if (!filter.evaluate(ednd))
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
                if (src == ednd || ((GeomUtils.duplicateNodes(ednd.getCoor(), src.getCoor(), m_duplicateNodesPrecision) && filter.evaluate(ednd)))) {
                    if (ednode1 == null)
                        ednode1 = ednd;
                    else if (ednd.originalNode().getUniqueId() > ednode1.originalNode().getUniqueId())
                        ednode1 = ednd;
                }
            }
            else {
                if ((GeomUtils.duplicateNodes(nd.getCoor(), src.getCoor(), m_duplicateNodesPrecision) && filter.evaluate(nd))) {
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
            if (src == ednd || ((GeomUtils.duplicateNodes(ednd.getCoor(), src.getCoor(), m_duplicateNodesPrecision) && filter.evaluate(ednd)))) {
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

    public void updateModifiedFlags() {
        for (EdNode n: m_nodes)
            if (!n.isDeleted())
                n.updateModifiedFlag();
        for (EdWay w: m_ways)
            if (!w.isDeleted())
                w.updateModifiedFlag();
        for (EdMultipolygon mp: m_multipolygons)
            if (!mp.isDeleted())
                mp.updateModifiedFlag();
    }

    public List<Command> finalizeEdit () {
        return finalizeEdit(0.0);
    }


    public List<Command> finalizeEdit (double resurrect_dist) {

        System.out.println("WayEditor.finalizeEdit(): ");

        // reset modified flags, if possible
        updateModifiedFlags();

        // get ways and nodes required in the resulting DataSet
        Set<EdWay> required_ways = new HashSet<> ();
        Set<EdNode> required_nodes = new HashSet<> ();
        for (EdWay w: m_ways) {
            if (w.isDeleted())
                continue;
            if (w.hasEditorReferrers() || w.isTagged() || w.hasExternalReferrers()) {
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
        Set<EdNode> add_nodes = new HashSet<> ();
        Set<EdNode> change_nodes = new HashSet<> ();
        for (EdNode n: required_nodes) {
            if (!n.hasOriginal())
                add_nodes.add(n);
            else if (n.isModified())
                change_nodes.add(n);
        }

        // original nodes to be deleted
        Set<EdNode> delete_nodes = new HashSet<> ();
        for (EdNode n: m_nodes) {
            if (!n.hasOriginal())
                continue;
            if (required_nodes.contains(n))
                continue;
            delete_nodes.add(n);
        }

        // resurrect anonymous deleted nodes
        if (resurrect_dist > 0) {
            Set<EdWay> affected_ways = resurrectNodes(add_nodes, change_nodes, delete_nodes, resurrect_dist);
            if (affected_ways != null) {
                for (EdWay way: affected_ways)
                    way.updateModifiedFlag();
            }
        }

        // ways to be added/changed
        Set<EdWay> add_ways = new HashSet<>();
        Set<EdWay> change_ways = new HashSet<>();
        for (EdWay w: required_ways) {
            if (!w.hasOriginal() && !w.isDeleted())
                add_ways.add(w);
            else if (w.hasOriginal() && !w.isDeleted() && w.isModified()) {
                change_ways.add(w);
            }
        }

        // ways to be deleted
        Set<EdWay> delete_ways = new HashSet<>();
        for (EdWay w: m_ways) {
            if (!w.hasOriginal())
                continue;
            if (required_ways.contains(w))
                continue;
            delete_ways.add(w);
        }

        List<Command> cmds = new LinkedList<>();

        // commands to add new nodes
        for (EdNode n: add_nodes)
            cmds.add(new AddCommand(n.finalNode()));

        // commands to change original nodes
        for (EdNode n: change_nodes)
            cmds.add(new ChangeCommand(n.originalNode(), n.finalNode()));

        // commands to add new ways
        for (EdWay w: add_ways) {
            cmds.add(new AddCommand(w.finalWay()));
            System.out.println(" - add way: " + Long.toString(w.getUniqueId()));
        }

        // commands to change original ways
        for (EdWay w: change_ways) {
            cmds.add(new ChangeCommand(w.originalWay(), w.finalWay()));
            System.out.println(" - change way: " + Long.toString(w.getUniqueId()));
        }

        // multipolygon commands
        for (EdMultipolygon emp: m_multipolygons) {
            if (!emp.hasOriginal() && !emp.isDeleted()) {
                cmds.add(new AddCommand(emp.finalMultipolygon()));
                System.out.println(" - add multipolygon: " + Long.toString(emp.getUniqueId()));
            }
            else if (emp.hasOriginal() && !emp.isDeleted() && emp.isModified()) {
                cmds.add(new ChangeCommand(emp.originalMultipolygon(), emp.finalMultipolygon()));
                System.out.println(" - change multipolygon: " + Long.toString(emp.getUniqueId()));
            }
            else if (emp.hasOriginal() && emp.isDeleted()) {
                cmds.add(new DeleteCommand(emp.finalMultipolygon()));
                System.out.println(" - delete multipolygon: " + Long.toString(emp.getUniqueId()));
            }
        }

        // commands to delete original ways
        for (EdWay w: delete_ways) {
            cmds.add(new DeleteCommand(w.originalWay()));
            System.out.println(" - delete way: " + Long.toString(w.getUniqueId()));
        }

        // commands to delete original nodes
        for (EdNode n: delete_nodes)
            cmds.add(new DeleteCommand(n.originalNode()));

        return cmds;
    }

    private List<EdNode> searchEdNodes(BBox bbox) {
        List<EdNode> result = new ArrayList<>();
        for (EdNode ednd: m_nodes) {
            if (ednd.isDeleted())
                continue;
            if (bbox.bounds(ednd.getCoor()))
                result.add(ednd);
        }
        return result;
    }

    private List<EdMultipolygon> searchEdMultipolygons(BBox bbox) {
        List<EdMultipolygon> result = new ArrayList<>();
        for (EdMultipolygon edmp: m_multipolygons) {
            if (edmp.isDeleted())
                continue;
            if (bbox.intersects(edmp.getBBox()))
                result.add(edmp);
        }
        return result;
    }

    private List<EdWay> searchEdWays(BBox bbox) {
        List<EdWay> result = new ArrayList<>();
        for (EdWay edw: m_ways) {
            if (edw.isDeleted())
                continue;
            if (bbox.intersects(edw.getBBox()))
                result.add(edw);
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

    public Set<EdWay> getModifiedWays() {
        Set<EdWay> result = new HashSet<>(m_ways);
        for (EdWay w: m_ways) {
            if (w.isModified())
                result.add(w);
        }
        return result;
    }

    public Set<EdObject> useNonEditedAreasContainingPoint(LatLon pos, IEdAreaPredicate filter) {
        Set<EdObject> areas = new HashSet<>();
        BBox bbox = new BBox(pos, pos);
        Node posnode = new Node (pos);

        // look for non-edited multipolygons
        for (Relation rel: getDataSet().searchRelations(bbox)) {
            if (!rel.isUsable() || rel.hasIncompleteMembers() || isEdited(rel))
                continue;
            if (!filter.evaluate(rel))
                continue;
            if (!Geometry.isNodeInsideMultiPolygon(posnode, rel, null))
                continue;
            areas.add(this.useMultipolygon(rel));
        }

        // look for non-edited ways
        for (Way w : getDataSet().searchWays(bbox)) {
            if (!w.isUsable() || isEdited(w) || w.hasIncompleteNodes())
                continue;
            if (!filter.evaluate(w))
                continue;
            if (!Geometry.nodeInsidePolygon(posnode, w.getNodes()))
                continue;

            areas.add(useWay(w));
        }

        return areas;
    }

    public Set<EdObject> useAllAreasInBBox(BBox bbox, IEdAreaPredicate filter) {
        Set<EdObject> areas = new HashSet<>();

        // look for non-edited multipolygons
        for (Relation rel: getDataSet().searchRelations(bbox)) {
            if (!rel.isUsable() || rel.hasIncompleteMembers() || isEdited(rel))
                continue;
            if (filter.evaluate(rel))
                areas.add(this.useMultipolygon(rel));
        }

        // look for edited multipolygons
        for (EdMultipolygon mp: this.searchEdMultipolygons(bbox)) {
            if (areas.contains(mp))
                continue;
            if (filter.evaluate(mp))
                areas.add(mp);
        }

        // look for edited ways
        for (EdWay w: this.searchEdWays(bbox)) {
            if (!filter.evaluate(w))
                continue;

            // Way is member of a multipolygon, so it was already implicitly included
            // as a part of EdMultipolygon above, or it's a tagged member of
            // multipolygon that doesn't match given filter. Which can be dangerous
            // and we ignore it (for now). #### review this note
            if (w.isMemberOfAnyMultipolygon())
                continue;

            areas.add(w);
        }

        // look for non-edited ways
        for (Way w : getDataSet().searchWays(bbox)) {
            if (!w.isUsable())
                continue;
            if (isEdited(w))
                continue;
            if (!filter.evaluate(w))
                continue;

            // Ignore member of a multipolygon, see notes above
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

            areas.add(useWay(w));
        }
        return areas;
    }

    class ResurrectableNodesPair implements Comparable<ResurrectableNodesPair> {
        public final EdNode delete_node;
        public final EdNode add_node;
        public final double distance;

        public ResurrectableNodesPair(EdNode an, EdNode dn, double dist) {
            delete_node = dn;
            add_node = an;
            distance = dist;
        }

        @Override
        public int compareTo(ResurrectableNodesPair t) {
            return Double.compare(this.distance, t.distance);
        }
    }


    private Set<EdWay> resurrectNodes(Set<EdNode> add_nodes, Set<EdNode> change_nodes, Set<EdNode> delete_nodes, double resurrect_dist) {
        if (add_nodes.isEmpty() || delete_nodes.isEmpty())
            return null;

        PriorityQueue<ResurrectableNodesPair> queue = new PriorityQueue<>();

        for (EdNode dn: delete_nodes) {

            Node dnorig = dn.originalNode();

            // don't resurrect explicitly deleted nodes and nodes that were originally tagged
            if (dn.isDeleted() || dnorig.isTagged())
                continue;
            
            for (EdNode an: add_nodes) {
                double dist = dnorig.getCoor().greatCircleDistance(an.getCoor());
                if (dist > resurrect_dist)
                    continue;
                queue.add(new ResurrectableNodesPair (an, dn, dist));
            }
        }

        Set<EdWay> affected_ways = new HashSet<>();

        ResurrectableNodesPair rnp;
        while ((rnp = queue.poll()) != null) {
            if (!delete_nodes.contains(rnp.delete_node) || !add_nodes.contains(rnp.add_node))
                continue;
            System.out.println("Resurrecting node " + Long.toString(rnp.delete_node.getUniqueId()) + " <- " + Long.toString(rnp.add_node.getUniqueId()) + ", dist: " + Double.toString(rnp.distance));
            add_nodes.remove(rnp.add_node);
            delete_nodes.remove(rnp.delete_node);
            change_nodes.add(rnp.add_node);
            rnp.add_node.forgeOriginalNodeDangerous(rnp.delete_node.originalNode());
            affected_ways.addAll(rnp.add_node.getEditorReferrers(EdWay.class));
        }

        return affected_ways;
    }
}


