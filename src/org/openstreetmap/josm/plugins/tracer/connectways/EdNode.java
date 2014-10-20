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

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.coor.EastNorth;


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

    protected OsmPrimitive currentPrimitive() {
        return m_node;
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

    public LatLon getCoor() {
        return m_node.getCoor();
    }

    public BBox getBBox(double dist) {
        BBox bbox = new BBox(m_node);
        bbox.addPrimitive(m_node, dist);
        return bbox;
    }

    public long getUniqueId() {
        return m_node.getUniqueId();
    }

    public EastNorth getEastNorth() {
        return m_node.getEastNorth();
    }

    Node currentNodeUnsafe() {
        checkNotDeleted();
        // Be careful, never modify returned Node!!
        return m_node;
    }
}


