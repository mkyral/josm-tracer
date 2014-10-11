package org.openstreetmap.josm.plugins.tracer.connectways;

import org.openstreetmap.josm.data.osm.Way;

public interface IEdWayPredicate {
    public boolean evaluate (EdWay w);
    public boolean evaluate (Way w);
}

