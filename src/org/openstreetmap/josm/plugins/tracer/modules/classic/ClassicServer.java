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

import java.io.BufferedReader;
import java.io.IOException;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;

public final class ClassicServer {

    public ClassicServer() {
    }

    private final static int classicServerTimeout = 60000;

    private String callServer(String urlString) throws IOException {
        try (BufferedReader reader = TracerUtils.openUrlStream (urlString, classicServerTimeout)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    public ClassicRecord trace(LatLon pos, String url, double adjlat, double adjlon) throws IOException {
        String content = callServer(url + "/trace/simple/" + pos.lat() + ";" + pos.lon());
        ClassicRecord record = new ClassicRecord(adjlat, adjlon);
        record.parseOutput(content);
        return record;
    }
}
