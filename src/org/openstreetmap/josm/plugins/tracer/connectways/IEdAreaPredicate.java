package org.openstreetmap.josm.plugins.tracer.connectways;

import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

public interface IEdAreaPredicate {
    public boolean evaluate(EdWay way);
    public boolean evaluate(Way way);
    public boolean evaluate(EdMultipolygon mp);
    public boolean evaluate(Relation mp);
}
