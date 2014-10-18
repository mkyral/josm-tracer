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

import java.util.List;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;

public final class EdNodeLogicalAndPredicate implements IEdNodePredicate {

    private final IEdNodePredicate m_pred1;
    private final IEdNodePredicate m_pred2;

    public EdNodeLogicalAndPredicate(IEdNodePredicate pred1, IEdNodePredicate pred2) {
        m_pred1 = pred1;
        m_pred2 = pred2;
    }

    public boolean evaluate(EdNode ednode) {
        return m_pred1.evaluate(ednode) && m_pred2.evaluate(ednode);
    }

    public boolean evaluate(Node node) {
        return m_pred1.evaluate(node) && m_pred2.evaluate(node);
    }
}

