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

package org.openstreetmap.josm.plugins.tracer.modules.ruianLands;

import java.io.ByteArrayInputStream;

import java.util.*;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;


/**
 * The Tracer RUIAN record class
 *
 */

public final class RuianLandsRecord extends TracerRecord {

    private String               m_source;
    private long                 m_ruian_id;
    private Map <String, String> m_keys;

    public RuianLandsRecord (double adjlat, double adjlon) {
        super(adjlat, adjlon);
        init();
    }

    @Override
    protected void init() {

        super.init();

        m_source = "";
        m_ruian_id = 0;
        m_keys = new HashMap<>();
    }

    /**
    * Map RUIAN usage values to OSM landuse or
    * natural or leisure key
    *
    */
    /*
    private void mapKeys () {
      if (m_druh_pozemku.equals("orná půda")){
        m_keys.put("landuse", "farmland");

      } else if (m_druh_pozemku.equals("chmelnice")){
        m_keys.put("landuse", "farmland");
        m_keys.put("crop", "hop");

      } else if (m_druh_pozemku.equals("vinice")){
        m_keys.put("landuse", "vineyard");

      } else if (m_druh_pozemku.equals("ovocný sad")){
        m_keys.put("orchard", "orchard");

      } else if (m_druh_pozemku.equals("trvalý travní porost")){
        m_keys.put("landuse", "meadow");
        m_keys.put("meadow", "agricultural");

      } else if (m_druh_pozemku.equals("lesní pozemek")){
        m_keys.put("landuse", "forest");

      } else if (m_druh_pozemku.equals("zahrada")) {
        m_keys.put("leisure", "garden");
        m_keys.put("garden:type", "residental");

      } else if (m_druh_pozemku.equals("zastavěná plocha a nádvoří")){
        m_keys.put("building", "yes");

      } else if (m_druh_pozemku.equals("ostatní plocha") ||
                 m_druh_pozemku.equals("vodní plocha")) {
        if (m_zpusob_vyuziti.equals("skleník, pařeniště")) {
          m_keys.put("landuse", "common");
        }

        else if (m_zpusob_vyuziti.equals("školka")) {
          m_keys.put("landuse", "plant_nursery");
        }

        else if (m_zpusob_vyuziti.equals("plantáž dřevin")) {
          m_keys.put("natural", "wood");
          m_keys.put("crop", "fast_growing_wood");
        }

        else if (m_zpusob_vyuziti.equals("les jiný než hospodářský")) {
          m_keys.put("natural", "wood");
        }

        else if (m_zpusob_vyuziti.equals("lesní pozemek, na kterém je budova")) {
          m_keys.put("landuse", "forest");
        }

        else if (m_zpusob_vyuziti.equals("rybník")) {
          m_keys.put("natural", "water");
          m_keys.put("water", "pond");
        }

        else if (m_zpusob_vyuziti.equals("koryto vodního toku přirozené nebo upravené")) {
          m_keys.put("landuse", "river");
        }

        else if (m_zpusob_vyuziti.equals("koryto vodního toku umělé")) {
          m_keys.put("landuse", "river");
        }

        else if (m_zpusob_vyuziti.equals("vodní nádrž přírodní")) {
          m_keys.put("natural", "water");
          m_keys.put("water", "lake");
        }

        else if (m_zpusob_vyuziti.equals("vodní nádrž umělá")) {
          m_keys.put("natural", "water");
          m_keys.put("water", "reservoir");
        }

        else if (m_zpusob_vyuziti.equals("zamokřená plocha")) {
          m_keys.put("natural", "wetland");
        }

        else if (m_zpusob_vyuziti.equals("společný dvůr")) {
          m_keys.put("", "");
        }

        else if (m_zpusob_vyuziti.equals("zbořeniště")) {
          m_keys.put("landuse", "brownfield");
        }

        else if (m_zpusob_vyuziti.equals("dráha")) {
          m_keys.put("landuse", "railway");
        }

        else if (m_zpusob_vyuziti.equals("dálnice")) {
          m_keys.put("landuse", "highway");
        }

        else if (m_zpusob_vyuziti.equals("silnice")) {
          m_keys.put("landuse", "highway");
        }

        else if (m_zpusob_vyuziti.equals("ostatní komunikace")) {
          m_keys.put("landuse", "highway");
        }

        else if (m_zpusob_vyuziti.equals("ostatní dopravní plocha")) {
//           m_keys.put("", "");
        }

        else if (m_zpusob_vyuziti.equals("zeleň")) {
          m_keys.put("leisure", "common");
        }

        else if (m_zpusob_vyuziti.equals("sportoviště a rekreační plocha")) {
          m_keys.put("landuse", "recreation_ground");
        }

        else if (m_zpusob_vyuziti.equals("hřbitov, urnový háj")) {
          m_keys.put("landuse", "cemetery");
        }

        else if (m_zpusob_vyuziti.equals("kulturní a osvětová plocha")) {
//           m_keys.put("", "");
        }

        else if (m_zpusob_vyuziti.equals("manipulační plocha")) {
          m_keys.put("highway", "service");
          m_keys.put("area", "yes");
        }

        else if (m_zpusob_vyuziti.equals("dobývací prostor")) {
          m_keys.put("landuse", "quarry");
        }

        else if (m_zpusob_vyuziti.equals("skládka")) {
          m_keys.put("landuse", "landfill");
        }

        else if (m_zpusob_vyuziti.equals("jiná plocha")) {
//           m_keys.put("", "");
        }

        else if (m_zpusob_vyuziti.equals("neplodná půda")) {
//           m_keys.put("", "");
        }

        else if (m_zpusob_vyuziti.equals("vodní plocha, na které je budova")) {
          m_keys.put("natural", "water");
        }

        else if (m_zpusob_vyuziti.equals("fotovoltaická elektrárna")) {
          m_keys.put("power", "generator");
          m_keys.put("generator:source", "solar");
          m_keys.put("generator:output:electricity", "yes");
          m_keys.put("generator:method", "photovoltaic");
        } else {
          System.out.println("Unsuported values combination: " + m_druh_pozemku + "/" + m_zpusob_vyuziti);
        }
}
    }*/

    /**
     * Parse given JSON string and fill variables with RUIAN data
     *
     * @param jsonStr JSON string with RUIAN data
     */
    public void parseJSON(String jsonStr) {

        init();

        JsonObject obj;
        try (JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(jsonStr.getBytes()))) {
            obj = jsonReader.readObject();
        }

        m_source = obj.getString("source");
        if (m_source.length() == 0)
            m_source = "cuzk:ruian";

        String keys = retrieveJsonString(obj, "keys");
        if (keys != null) {
            String[] kv = keys.replace("\"", "").replace(",{", "").replace("{", "").replace("}}", "}").split("}");
            System.out.println("keys: " + Arrays.toString(kv));
            for (int i = 0; i < kv.length; i++) {
                System.out.println("key[" + i + "]: " + kv[i]);
                String[] x = kv[i].split(",");
                m_keys.put(x[0], x[1]);
            }
        }

        JsonArray arr = retrieveJsonArray(obj, "geometry");
        if (arr != null && arr.size() > 0) {
            List<LatLon> way = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                System.out.println("i=" + i);
                JsonArray node = arr.getJsonArray(i);

                LatLon coor = new LatLon(
                        node.getJsonNumber(1).doubleValue(),
                        node.getJsonNumber(0).doubleValue()
                );
                System.out.println("coor: " + coor.toString());
                way.add(coor);
            }
            super.setOuter(way);
        }
    }

    /**
     * Return keys
     *
     * @return Keys
     */
    @Override
    public Map<String, String> getKeys(boolean alt) {
        return m_keys;
    }

    /**
     * Return Ruian ID of the land
     *
     * @return Ruian land ID
     */
    public long getLandID() {
        return m_ruian_id;
    }

    /**
     * Return data source
     *
     * @return Data source
     */
    public String getSource() {
        return m_source;
    }

    /**
     * Return whether traced object is building (has building key)
     *
     * @return True/False - is building or not
     */
    public boolean isBuilding() {
        return m_keys.containsKey("building");
    }

    /**
     * Returns whether traced object is a land (has landuse, natural or leisure
     * key)
     *
     * @return True/False - is land or not
     */
    public boolean isLand() {
        return m_keys.containsKey("landuse")
                || m_keys.containsKey("natural")
                || m_keys.containsKey("leisure");
    }

    /**
     * Returns whether traced object is a garden (has leisure=garden key)
     *
     * @return True/False - is garden or not
     */
    public boolean isGarden() {
        return m_keys.containsKey("leisure") && m_keys.get("leisure").equals("garden");
    }

    @Override
    public boolean hasData() {
        return super.hasOuter() && !m_keys.isEmpty();
    }
}
