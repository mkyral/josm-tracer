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

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;

public class LatLonSize {
    private final double m_lat;
    private final double m_lon;

    public LatLonSize(double lat, double lon) {
        m_lat = lat;
        m_lon = lon;
    }

    public double latSize() {
        return m_lat;
    }

    public double lonSize() {
        return m_lon;
    }

    public static LatLonSize get(LatLon ll, double radius_in_meters) {
        double meters_per_degree_lat = GeomUtils.getMetersPerDegreeOfLatitude(ll);
        double meters_per_degree_lon = GeomUtils.getMetersPerDegreeOfLongitude(ll);
        double radius_lat = radius_in_meters / meters_per_degree_lat;
        double radius_lon = radius_in_meters / meters_per_degree_lon;
        return new LatLonSize(radius_lat, radius_lon);
    }

    public static LatLonSize get(BBox box, double radius_in_meters) {
        double meters_per_degree_lat1 = GeomUtils.getMetersPerDegreeOfLatitude(box.getTopLeft());
        double meters_per_degree_lon1 = GeomUtils.getMetersPerDegreeOfLongitude(box.getTopLeft());
        double meters_per_degree_lat2 = GeomUtils.getMetersPerDegreeOfLatitude(box.getBottomRight());
        double meters_per_degree_lon2 = GeomUtils.getMetersPerDegreeOfLongitude(box.getBottomRight());
        double radius_lat = radius_in_meters / Math.min(meters_per_degree_lat1, meters_per_degree_lat2);
        double radius_lon = radius_in_meters / Math.min(meters_per_degree_lon1, meters_per_degree_lon2);
        return new LatLonSize(radius_lat, radius_lon);
    }
}
