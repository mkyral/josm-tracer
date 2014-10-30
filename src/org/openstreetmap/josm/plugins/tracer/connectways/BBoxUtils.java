package org.openstreetmap.josm.plugins.tracer.connectways;

import org.openstreetmap.josm.data.osm.BBox;

public abstract class BBoxUtils {
    
    public static void extendBBox(BBox box, double oversize) {
        box.add(box.getTopLeftLon() - oversize, box.getBottomRightLat() - oversize);
        box.add(box.getBottomRightLon() + oversize, box.getTopLeftLat() + oversize);
    }
}
