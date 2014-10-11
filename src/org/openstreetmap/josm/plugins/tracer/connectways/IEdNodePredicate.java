package org.openstreetmap.josm.plugins.tracer.connectways;

import org.openstreetmap.josm.data.osm.Node;

public interface IEdNodePredicate {
    public boolean evaluate (EdNode n);
    public boolean evaluate (Node n);
}

