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

public class ClipAreasSettings {
    private final GeomDeviation m_connectSubjectToClipTolerance;
    private final GeomDeviation m_clipperWayCleanupsTolerance;
    private final GeomDeviation m_reconnectIntersectionNodesTolerance;
    private final double m_discardCutoffsPercent;
    private final IDiscardableCutoffPredicate m_discardablePredicate;

    public ClipAreasSettings(GeomDeviation cs2ct, GeomDeviation cwct, GeomDeviation rint, double discard_cutoffs, IDiscardableCutoffPredicate discard_pred) {
        m_connectSubjectToClipTolerance = cs2ct;
        m_clipperWayCleanupsTolerance = cwct;
        m_reconnectIntersectionNodesTolerance = rint;
        m_discardCutoffsPercent = discard_cutoffs;
        m_discardablePredicate = discard_pred;
    }

    public ClipAreasSettings(GeomDeviation tolerance) {
        this (tolerance, tolerance, defaultReconnectIntersectionNodesTolerance(), 0.0, null);
    }

    public ClipAreasSettings(GeomDeviation tolerance, double discard_cutoffs, IDiscardableCutoffPredicate discard_pred) {
        this (tolerance, tolerance, defaultReconnectIntersectionNodesTolerance(), discard_cutoffs, discard_pred);
    }

    public GeomDeviation connectSubjectToClipTolerance() {
        return m_connectSubjectToClipTolerance;
    }

    public GeomDeviation clipperWayCleanupsTolerance() {
        return m_clipperWayCleanupsTolerance;
    }

    public GeomDeviation reconnectIntersectionNodesTolerance() {
        return m_reconnectIntersectionNodesTolerance;
    }

    public double discardCutoffsPercent() {
        return m_discardCutoffsPercent;
    }

    public IDiscardableCutoffPredicate discardablePredicate() {
        return m_discardablePredicate;
    }

    public static GeomDeviation defaultReconnectIntersectionNodesTolerance() {
        return new GeomDeviation(0.01, Math.PI / 40);
    }
}
