/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import org.openstreetmap.josm.data.coor.LatLon;

public class TracerServer {

//     static final String URL = "http://localhost:5050/";

    public TracerServer() {

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
                sb.append(line);
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
    public ArrayList<LatLon> trace(LatLon pos, String url, double adjX, double adjY) {
        try {
            String content = callServer(url + "/trace/simple/" + pos.lat() + ";" + pos.lon());
            ArrayList<LatLon> nodelist = new ArrayList<LatLon>();
            String[] lines = content.split("\\|");
            for (String line : lines) {
                String[] items = line.split(";");
                double x = Double.parseDouble(items[0]);
                double y = Double.parseDouble(items[1]);
                // Adjust point possition
                if (adjX != 0 || adjY != 0) {
                  x += adjX;
                  y += adjY;
                }
                nodelist.add(new LatLon(x, y));
            }
            return nodelist;
        } catch (Exception e) {
            return new ArrayList<LatLon>();
        }
    }

    /**
     * Log message to server.
     * @param message Message to log.
     */
    public void log(String message) {
        callServer("log/" + message);
    }

}
