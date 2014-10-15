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

public final class AreaBoundaryWayPredicate implements IEdWayPredicate {

    private final Match m_filter;
    private final MultipolygonBoundaryWayPredicate m_filterMultipolygon;

    public AreaBoundaryWayPredicate(Match filter) {
        m_filter = filter;
        m_filterMultipolygon = new MultipolygonBoundaryWayPredicate(filter);
    }

    public boolean evaluate(EdWay edway) {
        if (edway.isClosed() && edway.matches(m_filter))
            return true;
        if (m_filterMultipolygon.evaluate(edway))
            return true;
        return false;
    }

    public boolean evaluate(Way way) {
        if (way.isClosed() && m_filter.match(way))
            return true;
        if (m_filterMultipolygon.evaluate(way))
            return true;
        return false;
    }
}


