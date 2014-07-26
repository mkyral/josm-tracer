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

import org.openstreetmap.josm.plugins.tracer.pLpisRecord;
import org.openstreetmap.josm.plugins.tracer.krovak;
import org.openstreetmap.josm.plugins.tracer.xyCoor;

public class TracerServerLpis {


    public TracerServerLpis() {

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
     * Get element ID and geometry of the land on the position.
     * @param pos Position of the land.
     * @return Land ID and geometry.
     */
    public pLpisRecord getElementBasicData(LatLon pos, String url) {
        try {
            krovak k = new krovak();
            xyCoor xy = k.LatLon2krovak(pos);

            System.out.println ("LatLon: "+pos+" <-> XY: "+xy.x()+" "+xy.y());
            String bbox = xy.x()+","+xy.y()+","+xy.x()+","+xy.y();

            String request = url + "?VERSION=1.1.0&SERVICE=WFS&REQUEST=GetFeature&TYPENAME=LPIS_FB4_BBOX&bbox="+bbox+"&SRSNAME=EPSG:102067";

            System.out.println("Request: " + request);
            String content = callServer(request);
            System.out.println("Reply: " + content);
            pLpisRecord plpis = new pLpisRecord();
            plpis.parseXML("basic", content);
            plpis = getElementExtraData(plpis, url);
            return plpis;
        } catch (Exception e) {
            return new pLpisRecord();
        }
    }

    /**
     * Get additional information for given ID.
     * @param pLpisRecord Object of the pLPIS element.
     * @return Updated pLPIS element.
     */
    public pLpisRecord getElementExtraData(pLpisRecord plpisElement, String url) {
        try {
            String request = url + "?VERSION=1.1.0&SERVICE=WFS&REQUEST=GetFeature&TYPENAME=LPIS_FB4&&featureID=LPIS_FB4."+plpisElement.getpLpisID()+"&SRSNAME=EPSG:102067";

            System.out.println("Request: " + request);
            String content = callServer(request);
            System.out.println("Reply: " + content);
            plpisElement.parseXML("extra", content);
            return plpisElement;
        } catch (Exception e) {
            return plpisElement;
        }
    }
}
