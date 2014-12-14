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

package org.openstreetmap.josm.plugins.tracer.modules.ruianLands;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import org.openstreetmap.josm.data.coor.LatLon;

// import org.openstreetmap.josm.plugins.tracer.RuianRecord;

public class RuianLandsServer {


    public RuianLandsServer() {

    }

    /**
     * Call Trace server.
     * @param urlString Input parameters.
     * @return Result text.
     */
    private String callServer(String urlString) {
        try {
            URL url = new URL(urlString);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() == 0)
                  sb.append(line);
                else
                  sb.append(" "+line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Trace building on position.
     * @param pos Position of building.
     * @return Building border.
     */
    public RuianLandsRecord trace(LatLon pos, String url, double adjlat, double adjlon) {
        try {
            System.out.println("Request: "+ url + "/ruian-lands/beta/?lat=" + pos.lat() + "&lon=" + pos.lon());
            String content = callServer(url + "/ruian-lands/beta/?lat=" + pos.lat() + "&lon=" + pos.lon());
            System.out.println("Reply: " + content);
            RuianLandsRecord ruian = new RuianLandsRecord(adjlat, adjlon);
            ruian.parseJSON(content);
            return ruian;
        } catch (Exception e) {
            return new RuianLandsRecord(adjlat, adjlon);
        }
    }
}
