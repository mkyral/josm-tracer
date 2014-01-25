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

package org.openstreetmap.josm.plugins.tracer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import org.openstreetmap.josm.data.coor.LatLon;

public class TracerServerRuian {

//     static final String URL = "http://ruian.poloha.net/";
//     static final String URL = "http://pedro.propsychology.cz/mapa/";

    private String m_object = "", m_id = "";

    public TracerServerRuian() {

    }

    /**
     * Return traced object type
     * @return Object type
     */
    public String getObjectType() {
      return m_object;
    }

    /**
     * Return traced object id
     * @return Object id
     */
    public String getObjectId() {
      return m_id;
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
                  sb.append("|"+line);
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
            System.out.println("Request: "+ url + "/trace/" + pos.lat() + ";" + pos.lon());
            String content = callServer(url + "/trace/" + pos.lat() + ";" + pos.lon());
            System.out.println("Reply: " + content);
            ArrayList<LatLon> nodelist = new ArrayList<LatLon>();
            String[] lines = content.split("\\|");
            for (String line : lines) {
                System.out.println("Line: " + line);
                if (line.matches("(.*);(.*)")) {
                  String[] items = line.split(";");
                  double x = Double.parseDouble(items[0]);
                  double y = Double.parseDouble(items[1]);
                  if (adjX != 0 || adjY != 0) {
                    // Adjust point possition (-0.0000015 / -0.0000031)
                    x += adjX;
                    y += adjY;
                  }
                  nodelist.add(new LatLon(x, y));
                }
                else if (line.matches("(.*)=(.*)")) {
                  String[] items = line.split("=");
                  String key = items[0];
                  String value = items[1];
                  if (key.equals("building")) {
                    m_object = value;
                  } else if (key.equals("ruian_id")) {
                    m_id = value;
                  }
                }

            }
            return nodelist;
        } catch (Exception e) {
            return new ArrayList<LatLon>();
        }
    }

//     /**
//      * Log message to server.
//      * @param message Message to log.
//      */
//     public void log(String message) {
//         callServer("log/" + message);
//     }

}
