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
import java.util.List;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import static org.openstreetmap.josm.tools.I18n.tr;


public class EdNode extends EdObject {

    private Node m_node;

    EdNode (WayEditor editor, LatLon latlon) {
        super(editor, null);
        m_node = new Node(latlon);
    }

    EdNode (WayEditor editor, Node original_node) {
        super(editor, original_node);
        m_node = new Node(original_node);
    }

    @Override
    protected OsmPrimitive currentPrimitive() {
        return m_node;
    }

    void forgeOriginalNodeDangerous (Node original_node) {
        checkEditable();
        super.forgeOriginalDangerous(original_node);
        Node n = new Node(original_node);
        n.setKeys(m_node.getKeys());
        n.setCoor(m_node.getCoor());
        m_node = n;
        setModified();
    }

    public Node originalNode() {
        if (!hasOriginal())
            throw new IllegalStateException(tr("EdNode has no original Node"));
        return (Node)originalPrimitive();
    }

    public Node finalNode() {
        checkNotDeleted();
        if (!isFinalized()) {
            if (hasOriginal() && !isModified())
                m_node = originalNode();
            setFinalized();
        }
        return m_node;
    }

    /**
     * Returns a final Node that can be referenced in other EdObject's finalization.
     * The main difference from finalNode() is that for a modified node, it doesn't
     * return new final Node but -surprisingly- the original Node. Indeed, because
     * JOSM's ChangeCommand does not replace the original object wit the new one but
     * just copies it's contents, EdObjects must be finalized with references to
     * the original Node. Command ordering in WayEditor.finalizeEdit() guarantees that
     * the original Node is changed prior the referencing EdObject uses it.
     * @return final Way
     */
    Node finalReferenceableNode() {
        checkNotDeleted();
        Node fin = finalNode();
        if (hasOriginal() && isModified())
            return originalNode();
        else
            return fin;
    }


    public LatLon getCoor() {
        return m_node.getCoor();
    }

    public void setCoor(LatLon ll) {
        checkEditable();
        if (ll.equals(m_node.getCoor()))
                return;
        m_node.setCoor(ll);
        setModified();
    }

    @Override
    public BBox getBBox() {
        return m_node.getBBox();
    }

    public EastNorth getEastNorth() {
        return m_node.getEastNorth();
    }

    Node currentNodeUnsafe() {
        checkNotDeleted();
        // Be careful, never modify returned Node!!
        return m_node;
    }

    @Override
    void updateModifiedFlag() {
        checkEditable();
        if (!hasOriginal() || isDeleted() || !isModified())
            return;
        Node orig = originalNode();

        if (orig.getUniqueId() != m_node.getUniqueId())
            return;
        if (!orig.getCoor().equals(m_node.getCoor()))
            return;
        if (!hasIdenticalKeys(orig))
            return;
        resetModified();
    }

    /**
     * Returns all way referrers of this EdNode that match given
     * area filter predicate. Non-edited matching ways are automatically
     * included into node's WayEditor.
     * @param filter area ways filter
     * @return list of all EdWay referrers of this EdNode that match given predicate
     */
    List<EdWay> getAllAreaWayReferrers(IEdAreaPredicate filter) {
        List<EdWay> result = new ArrayList<>();

        List<EdWay> edways = this.getEditorReferrers(EdWay.class);
        for (EdWay way: edways) {
            if (filter.evaluate(way))
                result.add(way);
        }

        List<Way> ways = this.getExternalReferrers(Way.class);
        for (Way way: ways) {
            if (filter.evaluate(way))
                result.add(getEditor().useWay(way));
        }

        return result;
    }

    @Override
    public boolean reuseExistingNodes(GeomConnector gconn, IEdNodePredicate filter) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean reuseNearNodes(GeomConnector gconn, IEdNodePredicate filter, boolean move_near_nodes) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean connectExistingTouchingNodes(GeomConnector gconn, IEdNodePredicate filter) {
        throw new UnsupportedOperationException("This operation is not supported for nodes.");
    }

    @Override
    public double getEastNorthArea() {
        return 0.0;
    }

    @Override
    protected void deleteContentsShallow() {
        this.m_node.removeAll();
    }
}


