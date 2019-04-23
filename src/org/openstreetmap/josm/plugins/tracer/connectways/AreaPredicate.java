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
import org.openstreetmap.josm.data.osm.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Utils;

public class AreaPredicate implements IEdAreaPredicate {

    private final Match m_filter;

    public AreaPredicate (Match filter) {
        m_filter = filter;
    }

    @Override
    public boolean evaluate(EdWay way) {

        // closed matching way
        if (way.isClosed() && way.matches(m_filter))
            return true;

        // boundary way of a matching edited multipolygon
        List<EdMultipolygon> mps = way.getEditorReferrers(EdMultipolygon.class);
        for (EdMultipolygon mp: mps) {
            if (this.evaluate(mp))
                return true;
        }

        // boundary way of a matching external multipolygon
        List<Relation> relations = way.getExternalReferrers(Relation.class);
        for (Relation rel: relations) {
            if (this.evaluate(rel))
                return true;
        }

        return false;
    }

    @Override
    public boolean evaluate(Way way) {

        // closed matching way
        if (way.isClosed() && m_filter.match(way))
            return true;

        // boundary way of a matching multipolygon relation
        List<Relation> relations =  new ArrayList<>(Utils.filteredCollection(way.getReferrers(), Relation.class));
        for (Relation rel: relations) {
            if (this.evaluate(rel))
                return true;
        }

        return false;
    }

    @Override
    public boolean evaluate(EdMultipolygon mp) {

        // new-style multipolygon, ignore way tags
        if (mp.matches(m_filter))
            return true;

        // old-style multipolygon, considered as matching if:
        // (1) there's at least one matching outer way
        // (2) all non-matching outer ways are untagged
        int matching_ways = 0;
        List<EdWay> ways = mp.outerWays();
        for (EdWay way: ways) {
            if (way.matches(m_filter)) {
                ++matching_ways;
                continue;
            }
            if (way.isTagged())
                return false;
        }
        return matching_ways > 0;
    }

    @Override
    public boolean evaluate(Relation mp) {

        if (!mp.isMultipolygon())
            return false;

        // new-style multipolygon, ignore way tags
        if (m_filter.match(mp))
            return true;

        // old-style multipolygon, considered as matching if:
        // (1) there's at least one matching outer way
        // (2) all non-matching outer ways are untagged
        int matching_ways = 0;
        for (RelationMember member: mp.getMembers()) {
            if (!member.hasRole() || !member.getRole().equals("outer") || !member.isWay())
                continue;
            Way way = member.getWay();
            if (m_filter.match(way)) {
                ++matching_ways;
                continue;
            }
            if (way.isTagged())
                return false;
        }
        return matching_ways > 0;
    }
}
