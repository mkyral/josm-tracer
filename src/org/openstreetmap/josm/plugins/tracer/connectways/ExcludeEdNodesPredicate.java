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

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.openstreetmap.josm.data.osm.Node;

public final class ExcludeEdNodesPredicate implements IEdNodePredicate {

    private final Set<EdNode> m_nodes;

    public ExcludeEdNodesPredicate(EdObject obj) {
        if (obj.isMultipolygon()) {
            m_nodes = new HashSet<>();
            EdMultipolygon mp = (EdMultipolygon)obj;
            List<EdWay> outer_ways = mp.outerWays();
            for (EdWay way: outer_ways) {
                List<EdNode> nodes = way.getNodes();
                for (EdNode node: nodes)
                    m_nodes.add(node);
            }
            List<EdWay> inner_ways = mp.innerWays();
            for (EdWay way: inner_ways) {
                List<EdNode> nodes = way.getNodes();
                for (EdNode node: nodes)
                    m_nodes.add(node);
            }
        }
        else if (obj.isWay()) {
            m_nodes = new HashSet<>(((EdWay)obj).getNodes());
        }
        else if (obj.isNode()) {
            m_nodes = new HashSet<>();
            m_nodes.add((EdNode)obj);
        }
        else {
            throw new AssertionError("Unknown EdObject instance");
        }
    }

    @Override
    public boolean evaluate(EdNode ednode) {
        return !m_nodes.contains(ednode);
    }

    @Override
    public boolean evaluate(Node node) {
        return true;
    }
}

