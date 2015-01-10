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

import java.util.List;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;

public abstract class BBoxUtils {

    public static void extendBBox(BBox box, LatLonSize oversize) {
        box.add(box.getTopLeftLon() - oversize.lonSize(), box.getBottomRightLat() - oversize.latSize());
        box.add(box.getBottomRightLon() + oversize.lonSize(), box.getTopLeftLat() + oversize.latSize());
    }

    public static boolean isInsideBounds(LatLon p, List<Bounds> bounds, LatLonSize extrasize) {
        if (extrasize.isZero()) {
            return isInsideBounds(p, bounds);
        }

        // check given point and four corners around it specified by extrasize
        LatLon p1 = new LatLon(p.lat() - extrasize.latSize(), p.lon() - extrasize.lonSize());
        LatLon p2 = new LatLon(p.lat() - extrasize.latSize(), p.lon() + extrasize.lonSize());
        LatLon p3 = new LatLon(p.lat() + extrasize.latSize(), p.lon() - extrasize.lonSize());
        LatLon p4 = new LatLon(p.lat() + extrasize.latSize(), p.lon() + extrasize.lonSize());
        return isInsideBounds(p, bounds) && isInsideBounds(p1, bounds) &&
                isInsideBounds(p2, bounds) && isInsideBounds(p3, bounds) &&
                isInsideBounds(p4, bounds);
    }

    private static boolean isInsideBounds(LatLon p, List<Bounds> bounds) {
        for (Bounds b: bounds) {
            if (b.contains(p))
                return true;
        }
        return false;
    }
}
