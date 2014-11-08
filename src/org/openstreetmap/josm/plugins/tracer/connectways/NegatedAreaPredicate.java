package org.openstreetmap.josm.plugins.tracer.connectways;

import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

public class NegatedAreaPredicate implements IEdAreaPredicate {

    private final IEdAreaPredicate m_filter;
    
    public NegatedAreaPredicate (IEdAreaPredicate filter) {
        m_filter = filter;
    }
    
    @Override
    public boolean evaluate(EdWay way) {
        return !m_filter.evaluate(way);
    }

    @Override
    public boolean evaluate(Way way) {
        return !m_filter.evaluate(way);
    }

    @Override
    public boolean evaluate(EdMultipolygon mp) {
        return !m_filter.evaluate(mp);
    }

    @Override
    public boolean evaluate(Relation mp) {
        return !m_filter.evaluate(mp);
    }    
}
