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

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.OsmPrimitive;


public final class MultipolygonBoundaryWayPredicate implements IEdWayPredicate {

    private final Match m_filter;

    public MultipolygonBoundaryWayPredicate(Match filter) {
        m_filter = filter;
    }

    public boolean evaluate(EdWay way) {
        List<EdMultipolygon> mps = way.getEditorReferrers(EdMultipolygon.class);
        for (EdMultipolygon mp: mps) {
            if (mp.matches(m_filter))
                return true;
        }

        boolean way_match = way.matches(m_filter);

        List<Relation> relations = way.getExternalReferrers(Relation.class);
        for (Relation rel: relations) {
            if (!MultipolygonMatch.match(rel))
                continue;
            if (way_match)
                return true;
            if (m_filter.match(rel))
                return true;
        }

        return false;
    }

    public boolean evaluate(Way way) {

        boolean way_match = m_filter.match(way);

        List<Relation> relations = OsmPrimitive.getFilteredList(way.getReferrers(), Relation.class);
        for (Relation rel: relations) {
            if (!MultipolygonMatch.match(rel))
                continue;
            if (way_match)
                return true;
            if (m_filter.match(rel))
                return true;
        }
        return false;
    }
}

