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

import java.util.Set;

import org.openstreetmap.josm.data.osm.Node;

public final class ExcludeEdNodesPredicate implements IEdNodePredicate {

    private final Set<EdNode> m_nodes;

    public ExcludeEdNodesPredicate(EdObject obj) {
        m_nodes = obj.getAllNodes();
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

