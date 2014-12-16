/**
 *  Tracer - plugin for JOSM
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

package org.openstreetmap.josm.plugins.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;

public abstract class TracerRecord {

    private List<LatLon> m_outer;
    private List<List<LatLon>> m_inners;

    public TracerRecord () {
        m_outer = null;
        m_inners = new ArrayList<>();
    }

    protected void init() {
        m_outer = null;
        m_inners = new ArrayList<>();
    }

    /**
     *  Return outer polygon
     *  @return Outer polygon nodes
     */
    public final List <LatLon> getOuter() {
        if (m_outer == null)
            throw new IllegalStateException("No outer geometry available");
        return Collections.unmodifiableList(m_outer);
    }

    /**
     * Returns whether there's an outer way.
     * @return true if outer way is available
     */
    public final boolean hasOuter() {
        return m_outer != null;
    }

    /**
     *  Return whether there are inner polygons
     *  @return True/False
     */
    public final boolean hasInners() {
        return m_inners.size() > 0;
    }

    /**
     *  Returns the list of inner polygons
     *  @return inner polygons
     */
    public final List<List<LatLon>> getInners() {
        return Collections.unmodifiableList(m_inners);
    }

    /**
     * Returns BBox of the traced geometry
     * @return BBox of the geometry
     */
    public final BBox getBBox() {
        List<LatLon> outer = this.getOuter();
        LatLon p0 = outer.get(0);

        BBox bbox = new BBox(p0.lon(), p0.lat());
        for (int i = 1; i < outer.size(); i++) {
            LatLon p = outer.get(i);
            bbox.add(p.lon(), p.lat());
        }

        return bbox;
    }

    protected final void setOuter(List<LatLon> outer) {
        m_outer = outer;
    }

    protected final void addInner(List<LatLon> inner) {
        m_inners.add(inner);
    }

    public abstract boolean hasData();
}
