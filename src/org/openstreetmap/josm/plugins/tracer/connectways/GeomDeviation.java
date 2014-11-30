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

public class GeomDeviation {
    private final double m_distanceMeters;
    private final double m_distanceLatLon;
    private final double m_angleRad;

    public GeomDeviation (double distance_meters, double angle_rad) {
        if (distance_meters < 0.0)
            throw new IllegalArgumentException("Negative deviation distance");
        if (angle_rad < 0.0)
            throw new IllegalArgumentException("Negative deviation angle");

        m_distanceMeters = distance_meters;
        m_angleRad = angle_rad;
        m_distanceLatLon = m_distanceMeters/GeomUtils.metersPerDegree;
    }

    public double distanceMeters() {
        return m_distanceMeters;
    }

    public double distanceLatLon() {
        return m_distanceLatLon;
    }

    public double angleRad() {
        return m_angleRad;
    }

    public boolean inTolerance(GeomDeviation tolerance) {
        return
            m_distanceMeters <= tolerance.m_distanceMeters &&
            m_angleRad <= tolerance.m_angleRad;
    }

    public boolean distanceIsWithin(double distance) {
        return distance <= m_distanceMeters;
    }
}
