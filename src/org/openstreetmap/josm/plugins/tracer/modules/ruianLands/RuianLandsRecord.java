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

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.tools.Utils;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.util.*;
import java.lang.StringBuilder;

import org.openstreetmap.josm.plugins.tracer.TracerUtils.*;

/**
 * The Tracer RUIAN record class
 *
 */

public class RuianLandsRecord {

    private static LatLon   m_coor;
    private static String   m_source;
    private static long     m_ruian_id;
    private static String   m_druh_pozemku;
    private static String   m_zpusob_vyuziti;
    private static String   m_plati_od;
    private static Map <String, String> m_keys;

    private static ArrayList <LatLon> m_geometry;

    /**
    * Constructor
    *
    */
    public void RuianLandsRecord () {
      init();
    }

    /**
    * Initialization
    *
    */
    private static void init () {

      m_coor = null;
      m_source = "";
      m_ruian_id = 0;
      m_druh_pozemku = "";
      m_zpusob_vyuziti = "";
      m_plati_od = "";
      m_keys = new HashMap <String, String>();
      m_geometry = new ArrayList<LatLon> ();

    }

    /**
    * Map RUIAN usage values to OSM landuse or
    * natural or leisure key
    *
    */
    private static void mapKeys () {
      if (m_druh_pozemku.equals("zahrada")) {
        m_keys.put("leisure", "garden");
      } else if (m_druh_pozemku.equals("zastavěná plocha a nádvoří")){
        m_keys.put("building", "yes");
      } else if (m_druh_pozemku.equals("orná půda")){
        m_keys.put("landuse", "farmland");
      } else if (m_druh_pozemku.equals("ostatní plocha")) {
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

    }

    /**
    * Parse given JSON string and fill variables with RUIAN data
    *
    */
    public static void parseJSON (String jsonStr) {


    init();

    JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(jsonStr.getBytes()));
    JsonObject obj = jsonReader.readObject();
    jsonReader.close();

      try {
        double lat, lon;

        JsonObject coorObjekt = obj.getJsonObject("coordinates");

        try {
          lat = Double.parseDouble(coorObjekt.getString("lat"));
          lon = Double.parseDouble(coorObjekt.getString("lon"));
          m_coor = new LatLon (lat, lon);
        } catch (Exception e) {
          System.out.println("coor: " + e.getMessage());
        }

        try {
          m_source = obj.getString("source");
        } catch (Exception e) {
          System.out.println("source: " + e.getMessage());
        }

      } catch (Exception e) {
        System.out.println("coordinates: " + e.getMessage());
      }

// =========================================================================
      try {
        JsonObject parcela = obj.getJsonObject("parcela");

        try {
          m_druh_pozemku = parcela.getString("druh_pozemku");
        } catch (Exception e) {
          System.out.println("parcela.druh_pozemku: " + e.getMessage());
        }

        try {
          m_zpusob_vyuziti = parcela.getString("zpusob_vyuziti");
        } catch (Exception e) {
          System.out.println("parcela.zpusob_vyuziti: " + e.getMessage());
        }

        try {
          m_plati_od = parcela.getString("plati_od");
        } catch (Exception e) {
          System.out.println("parcela.plati_od: " + e.getMessage());
        }

      } catch (Exception e) {
        System.out.println("parcela: " + e.getMessage());
      }
// =========================================================================
      try {
        JsonArray arr = obj.getJsonArray("geometry");

        for(int i = 0; i < arr.size(); i++)
        {
          System.out.println("i="+i);
          JsonArray node = arr.getJsonArray(i);

          try {
            LatLon coor = new LatLon(
              LatLon.roundToOsmPrecision(node.getJsonNumber(1).doubleValue()),
              LatLon.roundToOsmPrecision(node.getJsonNumber(0).doubleValue())
            );
            System.out.println("coor: " + coor.toString());
            m_geometry.add(coor);
          } catch (Exception e) {
          }

        }
      } catch (Exception e) {
      }

      mapKeys();
    }

  /**
   *  Return number of nodes in the building
   *  @return Count of nodes in building
   */
  public int getCoorCount() {
    if (m_geometry == null)
      return 0;
    else
      return m_geometry.size();
  }

  /**
   *  Return coordinates of node
   *  @return geometry node coordinates
   */
  public LatLon getCoor(int i) {
    return m_geometry.get(i);
  }

  /**
   *  Return RUIAN landusage code
   *  @return RUIAN land usage code
   */
  public String getLandUsageCode() {
    return m_zpusob_vyuziti;
  }

  /**
   *  Return keys
   *  @return Keys
   */
   public Map<String, String> getKeys() {
    return m_keys;
  }

  /**
   *  Return Ruian ID of the land
   *  @return Ruian land ID
   */
  public long getLandID() {
    return m_ruian_id;
  }

  /**
   *  Return data source
   *  @return Data source
   */
  public String getSource() {
    return m_source;
  }
}
