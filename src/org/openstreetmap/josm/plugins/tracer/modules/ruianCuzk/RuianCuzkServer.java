/**
 *  Tracer - plugin for JOSM (RUIAN support)
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

package org.openstreetmap.josm.plugins.tracer.modules.ruianCuzk;

import java.io.IOException;
import jakarta.json.JsonObject;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.CuzkRuian;

public final class RuianCuzkServer {

    private static final String FIELDS = "kod,zpusobvyuzitikod,pocetpodlazi,pocetbytu,dokonceni";

    public RuianCuzkServer() {
    }

    /**
     * Trace building on given position.
     *
     * @param pos Position of building.
     * @param url Url of the ČÚZK RÚIAN MapServer
     * @param adjlat Latitude correction to be applied to building geometry.
     * @param adjlon Longitude correction to be applied to building geometry.
     * @return Building data.
     * @throws java.io.IOException Input/Output issue
     */
    public RuianCuzkRecord trace(LatLon pos, String url, double adjlat, double adjlon) throws IOException {
        RuianCuzkRecord ruian = new RuianCuzkRecord(adjlat, adjlon);
        JsonObject feature = CuzkRuian.featureAt(url, CuzkRuian.LAYER_BUILDING, pos, FIELDS);
        System.out.println("Reply: " + feature);
        if (feature != null)
            ruian.parseCuzk(feature, pos);
        return ruian;
    }
}
