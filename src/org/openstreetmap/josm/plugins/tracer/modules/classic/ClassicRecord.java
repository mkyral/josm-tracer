/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral
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

package org.openstreetmap.josm.plugins.tracer.modules.classic;

import java.util.ArrayList;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;

public final class ClassicRecord extends TracerRecord {

    public ClassicRecord(double adjlat, double adjlon) {
        super(adjlat, adjlon);
    }

    void parseOutput (String content) {
        ArrayList<LatLon> nodelist = new ArrayList<>();
        String[] lines = content.split("\\|");
        for (String line : lines) {
            String[] items = line.split(";");
            double x = Double.parseDouble(items[0]);
            double y = Double.parseDouble(items[1]);
            nodelist.add(new LatLon(x, y));
        }
        if (nodelist.size() > 0) {
            nodelist.add(nodelist.get(0));
            super.setOuter(nodelist);
        }
    }

    @Override
    public boolean hasData() {
        return super.hasOuter();
    }
}
