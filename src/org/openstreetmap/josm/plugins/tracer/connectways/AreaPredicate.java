package org.openstreetmap.josm.plugins.tracer.connectways;

import java.util.List;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

public class AreaPredicate implements IEdAreaPredicate {

    private final Match m_filter;
    private final MultipolygonBoundaryWayPredicate m_filterMultipolygon;
    
    public AreaPredicate (Match filter) {
        m_filter = filter;
        m_filterMultipolygon = new MultipolygonBoundaryWayPredicate(filter);
    }
    
    @Override
    public boolean evaluate(EdWay way) {
        if (way.isClosed() && way.matches(m_filter))
            return true;
        if (m_filterMultipolygon.evaluate(way))
            return true;
        return false;
    }

    @Override
    public boolean evaluate(Way way) {
        if (way.isClosed() && m_filter.match(way))
            return true;
        if (m_filterMultipolygon.evaluate(way))
            return true;
        return false;
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
            if (!member.hasRole() || !member.getRole().equals("outer") || !member.isWay())
                continue;
            if (m_filter.match(member.getWay()))
                return true;
        }
        
        return false;
    }
    
}