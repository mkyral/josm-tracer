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

public interface IDiscardableCutoffPredicate {

    /**
     * Replies whether the given way can be completely removed as a result of polygon clipping.
     * If <code>cutoffs_percent</code> is zero, entire way was clipped out.
     * If <code>cutoffs_percent</code> is non-zero, way was cut to one or more
     * cutoffs with areas below the given threshold. (Note that the sum of all
     * cutoffs can be greater than the threshold.)
     * @param way way to be discarded
     * @param cutoffs_percent sum of all cutoffs, expressed in percent of the original area
     * @return true if way can be discarded, false otherwise
     */
    public boolean canSilentlyDiscard(EdWay way, double cutoffs_percent);
}
