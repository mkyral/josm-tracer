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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * Point queries against the RÚIAN map service of ČÚZK (ArcGIS REST, layers in WGS84).
 */
public final class CuzkRuian {

    public static final String DEFAULT_URL =
        "https://ags.cuzk.gov.cz/arcgis/rest/services/RUIAN/Prohlizeci_sluzba_nad_daty_RUIAN/MapServer";

    public static final int LAYER_BUILDING = 3;
    public static final int LAYER_PARCEL = 5;

    /** A polygon part: its outer ring and the holes inside it. */
    public static final class Polygon {
        public final List<LatLon> outer;
        public final List<List<LatLon>> inners = new ArrayList<>();

        Polygon(List<LatLon> outer) {
            this.outer = outer;
        }
    }

    private CuzkRuian() {
    }

    /**
     * Returns the feature of the given layer that contains pos, or null when there is none.
     *
     * @param baseUrl MapServer url
     * @param layer Layer id
     * @param pos Position
     * @param outFields Comma separated attribute names
     * @return Feature with "attributes" and "geometry", or null
     * @throws IOException Network failure or an error reported by the service
     */
    public static JsonObject featureAt(String baseUrl, int layer, LatLon pos, String outFields) throws IOException {
        JsonObject point = Json.createObjectBuilder()
            .add("x", pos.lon())
            .add("y", pos.lat())
            .add("spatialReference", Json.createObjectBuilder().add("wkid", 4326))
            .build();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("geometry", point.toString());
        params.put("geometryType", "esriGeometryPoint");
        params.put("inSR", "4326");
        params.put("outSR", "4326");
        params.put("spatialRel", "esriSpatialRelIntersects");
        params.put("outFields", outFields);
        params.put("returnGeometry", "true");
        params.put("f", "json");

        StringBuilder url = new StringBuilder(baseUrl.replaceAll("/+$", ""));
        url.append('/').append(layer).append("/query");
        char sep = '?';
        for (Map.Entry<String, String> e : params.entrySet()) {
            url.append(sep).append(e.getKey()).append('=')
               .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            sep = '&';
        }

        System.out.println("Request: " + url);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = TracerUtils.openUrlStream(url.toString(), "UTF-8")) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }

        JsonObject reply;
        try (JsonReader jsonReader = Json.createReader(new StringReader(sb.toString()))) {
            reply = jsonReader.readObject();
        } catch (RuntimeException e) {
            throw new IOException("ČÚZK RÚIAN service returned an unreadable reply: " + e.getMessage(), e);
        }

        // ArcGIS reports failures with HTTP 200 and an "error" object
        JsonValue err = reply.get("error");
        if (err != null && err.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject eo = (JsonObject) err;
            throw new IOException("ČÚZK RÚIAN service error " + eo.getInt("code", 0) + ": " + eo.getString("message", ""));
        }

        JsonArray features = reply.getJsonArray("features");
        if (features == null)
            throw new IOException("ČÚZK RÚIAN service reply has no features list");
        if (features.isEmpty())
            return null;
        return features.getJsonObject(0);
    }

    /**
     * Returns the polygon part of an Esri polygon geometry that contains pos.
     *
     * @param geometry Esri JSON polygon (rings)
     * @param pos Position
     * @return The part with its holes
     * @throws IOException Geometry without any outer ring
     */
    public static Polygon partAt(JsonObject geometry, LatLon pos) throws IOException {
        JsonArray rings = geometry.getJsonArray("rings");
        List<Polygon> parts = new ArrayList<>();
        List<List<LatLon>> holes = new ArrayList<>();
        for (int i = 0; i < rings.size(); i++) {
            List<LatLon> ring = readRing(rings.getJsonArray(i));
            if (ring.size() < 4)
                continue;
            // Esri outer rings run clockwise (negative shoelace area), holes counter-clockwise
            if (signedArea(ring) < 0)
                parts.add(new Polygon(ring));
            else
                holes.add(ring);
        }
        if (parts.isEmpty())
            throw new IOException("ČÚZK RÚIAN geometry has no outer ring");

        for (List<LatLon> hole : holes) {
            for (Polygon p : parts) {
                if (contains(p.outer, hole.get(0))) {
                    p.inners.add(hole);
                    break;
                }
            }
        }

        for (Polygon p : parts) {
            if (contains(p.outer, pos))
                return p;
        }
        return parts.get(0);
    }

    /**
     * Converts an ArcGIS date attribute (epoch ms of the day's UTC midnight) to yyyy-MM-dd.
     *
     * @param attrs Feature attributes
     * @param key Attribute name
     * @return Date or "" when unset
     */
    public static String date(JsonObject attrs, String key) {
        JsonValue v = attrs.get(key);
        if (v == null || v.getValueType() != JsonValue.ValueType.NUMBER)
            return "";
        return Instant.ofEpochMilli(((JsonNumber) v).longValue()).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /**
     * @param attrs Feature attributes
     * @param key Attribute name
     * @return Integer attribute, or -1 when unset
     */
    public static long number(JsonObject attrs, String key) {
        JsonValue v = attrs.get(key);
        if (v == null || v.getValueType() != JsonValue.ValueType.NUMBER)
            return -1;
        return ((JsonNumber) v).longValue();
    }

    private static List<LatLon> readRing(JsonArray arr) {
        List<LatLon> ring = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonArray node = arr.getJsonArray(i);
            LatLon coor = new LatLon(
                LatLon.roundToOsmPrecision(node.getJsonNumber(1).doubleValue()),
                LatLon.roundToOsmPrecision(node.getJsonNumber(0).doubleValue()));
            if (ring.isEmpty() || !ring.get(ring.size() - 1).equals(coor))
                ring.add(coor);
        }
        return ring;
    }

    private static double signedArea(List<LatLon> ring) {
        double a = 0;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            a += (ring.get(j).lon() * ring.get(i).lat()) - (ring.get(i).lon() * ring.get(j).lat());
        }
        return a / 2;
    }

    private static boolean contains(List<LatLon> ring, LatLon p) {
        boolean in = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            LatLon a = ring.get(i), b = ring.get(j);
            if ((a.lat() > p.lat()) != (b.lat() > p.lat())
                && p.lon() < (b.lon() - a.lon()) * (p.lat() - a.lat()) / (b.lat() - a.lat()) + a.lon())
                in = !in;
        }
        return in;
    }
}
