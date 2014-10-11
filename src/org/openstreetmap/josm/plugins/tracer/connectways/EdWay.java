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
import org.openstreetmap.josm.data.osm.Way;
import java.util.List;
import java.util.ArrayList;
import org.openstreetmap.josm.tools.Pair;
import java.util.Map;
import org.openstreetmap.josm.data.osm.OsmPrimitive;


public class EdWay extends EdObject {
    private Way m_way;
    private List<EdNode> m_nodes;

    // Note: m_way has the following properties:
    // (a) before finalization:
    // - if this.hasOriginal(), m_way is a clone of originalNode();
    // - otherwise, m_way is a newly created Way object.
    // - in all cases, m_way contains NO Nodes, nodes are stored only in m_nodes array!
    // (b) after finalization:
    // - contains final way

    EdWay (WayEditor editor, List<EdNode> ednodes) {
        super(editor, null);
        m_way = new Way();

        if (!this.getEditor().ownedByEditor (ednodes))
            throw new IllegalArgumentException(tr("EdNode(s) from a different WayEditor"));

        if (ednodes != null)
            m_nodes = new ArrayList<EdNode>(ednodes);
        else
            m_nodes = new ArrayList<EdNode>();

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    EdWay(WayEditor editor, Way original_way) {
        super(editor, original_way);
        m_way = new Way(original_way);
        m_way.setNodes(null);

        m_nodes = new ArrayList<EdNode>(original_way.getNodesCount());
        for (int i = 0; i < original_way.getNodesCount(); i++)
            m_nodes.add(editor.useNode(original_way.getNode(i)));

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    public void removeAllNodes() {
        checkEditable();
        if (m_nodes.size() == 0)
            return;

        for (EdNode en: m_nodes)
            en.removeRef(this);

        m_nodes = new ArrayList<EdNode>();
        setModified();
    }

    public void setNodes(List<EdNode> ednodes) {
        checkEditable();
        if (!this.getEditor().ownedByEditor (ednodes))
            throw new IllegalArgumentException(tr("EdNode(s) from a different WayEditor"));

        if (ednodes == null) {
            removeAllNodes();
            return;
        }

        setModified();

        for (EdNode en: m_nodes)
            en.removeRef(this);

        m_nodes = new ArrayList<EdNode>(ednodes);

        for (EdNode en: m_nodes)
            en.addRef(this);
    }

    public void setKeys(Map<String,String> keys) {
        checkEditable();
        m_way.setKeys(keys);
        setModified();
    }

    public int getNodesCount() {
        return m_nodes.size();
    }

    public EdNode getNode(int idx) {
        return m_nodes.get(idx);
    }

    public boolean isClosed() {
        if (isFinalized())
            return m_way.isClosed();
        return (m_nodes.size() >= 3) && (m_nodes.get(0) == m_nodes.get(m_nodes.size() - 1));
    }

    protected OsmPrimitive currentPrimitive() {
        return m_way;
    }    

    public Way originalWay() {
        if (!hasOriginal())
            throw new IllegalStateException(tr("EdWay has no original Way"));
        return (Way)originalPrimitive();
    }

    public Way finalWay() {
        checkNotDeleted();
        if (isFinalized())
            return m_way;

        setFinalized();
        if (hasOriginal() && !isModified()) {
            m_way = originalWay();
        }
        else {
            Way fin = new Way(m_way);
            ArrayList<Node> nodes = new ArrayList<Node>(m_nodes.size());
            for (EdNode en: m_nodes)
                nodes.add(en.finalNode());
            fin.setNodes(nodes);            
            m_way = fin;
        }
        return m_way;
    }

    public void reuseExistingNodes(IEdNodePredicate filter) {
        checkEditable ();
        if (filter == null)
            throw new IllegalArgumentException(tr("No filter specified"));

        boolean modified = false;
        List<EdNode> new_nodes = new ArrayList<EdNode> (m_nodes.size());
        for (EdNode en: m_nodes) {
            EdNode nn = getEditor().findExistingNodeForDuplicateMerge(en, filter);
            if (nn != null) {
                new_nodes.add (nn);
                modified = true;
            }
            else {
                new_nodes.add (en);
            }
        }

        if (modified) {
            if (isClosed() && (new_nodes.get(0) != new_nodes.get(new_nodes.size() - 1)))
                throw new AssertionError(tr("EdWay.reuseExistingNodes on a closed way created a non-closed way!"));
            setNodes (new_nodes);
        }
    }
}


