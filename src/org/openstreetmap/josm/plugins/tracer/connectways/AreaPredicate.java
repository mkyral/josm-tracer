package org.openstreetmap.josm.plugins.tracer.connectways;

import java.util.List;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

public class AreaPredicate implements IEdAreaPredicate {

    private final Match m_filter;
    private final AreaBoundaryWayPredicate m_boundaryWayFilter;
    
    public AreaPredicate (Match filter) {
        m_filter = filter;
        m_boundaryWayFilter = new AreaBoundaryWayPredicate(filter);
    }
    
    @Override
    public boolean evaluate(EdWay way) {
        return m_boundaryWayFilter.evaluate(way);
    }

    @Override
    public boolean evaluate(Way way) {
        return m_boundaryWayFilter.evaluate(way);
    }

    @Override
    public boolean evaluate(EdMultipolygon mp) {
        // new-style multipolygon
        if (mp.matches(m_filter))
            return true;
        
        // old-style multipolygon, we detect only outer way tags
        List<EdWay> ways = mp.outerWays();
        for (EdWay way: ways) {
            if (way.matches(m_filter))
                return true;
        }
        
        return false;
    }

    @Override
    public boolean evaluate(Relation mp) {
        
        if (!MultipolygonMatch.match(mp))
            return false;
        
        // new-style multipolygon
        if (m_filter.match(mp))
            return true;
        
        // old-style multipolygon, we detect only outer way tags
        for (RelationMember member: mp.getMembers()) {
            if (!member.getRole().equals("outer") || !member.isWay())
                continue;
            if (m_filter.match(member.getWay()))
                return true;
        }
        
        return false;
    }
    
}
