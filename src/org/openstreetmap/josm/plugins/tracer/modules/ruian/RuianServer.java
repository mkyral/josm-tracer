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

package org.openstreetmap.josm.plugins.tracer.modules.ruian;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;

public final class RuianServer {

    public RuianServer() {
    }

    /**
     * Call Trace server.
     *
     * @param urlString Input parameters.
     * @return Result text.
     * @throws java.net.MalformedURLException Wrong url
     * @throws java.io.IOException Input/Output issue
     */
    private String callServer(String urlString) throws MalformedURLException, IOException {
        try (BufferedReader reader = TracerUtils.openUrlStream (urlString)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Trace building on given position.
     *
     * @param pos Position of building.
     * @param url Url of RUIAN server
     * @param adjlat Latitude correction to be applied to building geometry.
     * @param adjlon Longitude correction to be applied to building geometry.
     * @return Building data.
     * @throws java.io.IOException Input/Output issue
     */
    public RuianRecord trace(LatLon pos, String url, double adjlat, double adjlon) throws IOException {
        String call_url = url + "/ruian-buildings/?req=full&lat=" + pos.lat() + "&lon=" + pos.lon();
        System.out.println("Request: " + call_url);
        String content = callServer(call_url);
        System.out.println("Reply: " + content);
        RuianRecord ruian = new RuianRecord(adjlat, adjlon);
        ruian.parseJSON(content);
        return ruian;
    }
}
