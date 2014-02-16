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

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.*;
import java.lang.StringBuilder;

import org.openstreetmap.josm.plugins.tracer.Address;

/**
 * The Tracer RUIAN record class
 *
 */

public class RuianRecord {

    private static double   m_coor_lat, m_coor_lon;
    private static String   m_source;
    private static long     m_ruian_id;
    private static int      m_levels;
    private static int      m_flats;
    private static String   m_usage;
    private static String   m_usage_key;
    private static String   m_usage_val;
    private static String   m_finished;
    private static String   m_valid_from;

    private static ArrayList <LatLon> m_geometry;
    private static ArrayList <Address> m_address_places;

    /**
    * Constructor
    *
    */
    public void RuianRecord () {
      init();
    }

    /**
    * Initialization
    *
    */
    private static void init () {

      m_coor_lat = 0;
      m_coor_lon = 0;
      m_source = "";
      m_ruian_id = 0;
      m_levels = 0;
      m_flats = 0;
      m_usage = "";
      m_usage_key = "";
      m_usage_val = "";
      m_finished = "";
      m_valid_from = "";
      m_geometry = new ArrayList<LatLon> ();
      m_address_places = new ArrayList<Address> ();

    }

    /**
    * Parse given JSON string and fill variables with RUIAN data
    *
    */
    public static void parseJSON (String jsonStr) {


    init();

    long     m_adr_id = 0;
    String   m_house_number = "";
    String   m_house_number_typ = "";
    String   m_street_number = "";
    String   m_street = "";
    String   m_place = "";
    String   m_suburb = "";
    String   m_city = "";
    String   m_district = "";
    String   m_region = "";
    String   m_postcode = "";


    try {
      JSONObject obj = new JSONObject(jsonStr);

      try {
        m_coor_lat = obj.getJSONObject("coordinates").getDouble("lat");
        System.out.println("lat: " + Double.toString(m_coor_lat));
      } catch (Exception e) {
      }

      try {
        m_coor_lon = obj.getJSONObject("coordinates").getDouble("lon");
        System.out.println("lat: " + Double.toString(m_coor_lon));
      } catch (Exception e) {
      }

      try {
        m_source = obj.getString("source");
      } catch (Exception e) {
      }

// =========================================================================
      try {
        JSONObject building = obj.getJSONObject("stavebni_objekt");

        try {
          m_ruian_id = building.getLong("ruian_id");
        } catch (Exception e) {
        }

        try {
          m_house_number = building.getString("cislo_domovni");
        } catch (Exception e) {
        }

        try {
          m_house_number_typ = building.getString("cislo_domovni_typ");
        } catch (Exception e) {
        }

        try {
          m_street_number = building.getString("cislo_orientacni");
        } catch (Exception e) {
        }

        try {
          m_adr_id = building.getLong("adresni_misto_kod");
        } catch (Exception e) {
        }

        try {
          m_street = building.getString("ulice");
        } catch (Exception e) {
        }

        try {
          m_place = building.getString("cast_obce");
        } catch (Exception e) {
        }

        try {
          m_suburb = building.getString("mestska_cast");
        } catch (Exception e) {
        }

        try {
          m_city = building.getString("obec");
        } catch (Exception e) {
        }

        try {
          m_district = building.getString("okres");
        } catch (Exception e) {
        }

        try {
          m_region = building.getString("kraj");
        } catch (Exception e) {
        }

        try {
          m_postcode = building.getString("psc");
        } catch (Exception e) {
        }

        try {
          m_levels = building.getInt("pocet_podlazi");
        } catch (Exception e) {
        }

        try {
          m_flats = building.getInt("pocet_bytu");
        } catch (Exception e) {
        }

        try {
          m_usage = building.getString("zpusob_vyuziti");
        } catch (Exception e) {
        }

        try {
          m_usage_key = building.getString("zpusob_vyuziti_key");
        } catch (Exception e) {
        }

        try {
          m_usage_val = building.getString("zpusob_vyuziti_val");
        } catch (Exception e) {
        }

        try {
          m_valid_from = building.getString("plati_od");
        } catch (Exception e) {
        }

        try {
          m_finished = building.getString("dokonceni");
        } catch (Exception e) {
        }
      } catch (Exception e) {
      }

// =========================================================================
      try {
        JSONArray arr = obj.getJSONArray("geometry");

        for(int i = 0; i < arr.length(); i++)
        {
          System.out.println("i="+i);
          JSONArray node = arr.getJSONArray(i);

          try {
            LatLon coor = new LatLon(node.getDouble(1), node.getDouble(0));
            System.out.println("coor: " + coor.toString());
            m_geometry.add(coor);
          } catch (Exception e) {
          }

        }
      } catch (Exception e) {
      }

// =========================================================================
      try {
        JSONArray arr = obj.getJSONArray("adresni_mista");

        for(int i = 0; i < arr.length(); i++)
        {
          JSONObject addrPlace = arr.getJSONObject(i);
          Address addr = new Address();

          try {
            m_adr_id = addrPlace.getLong("ruian_id");
          } catch (Exception e) {
          }

          try {
            m_house_number = addrPlace.getString("cislo_domovni");
          } catch (Exception e) {
          }

          try {
            m_street_number = addrPlace.getString("cislo_orientacni");
          } catch (Exception e) {
          }

          try {
            m_street = addrPlace.getString("ulice");
          } catch (Exception e) {
          }

          addr.setRuianID(m_adr_id);

          if (m_house_number_typ.equals("číslo popisné"))
            addr.setConscriptionNumber(m_house_number);
          else if (m_house_number_typ.equals("číslo evidenční"))
            addr.setProvisionalNumber(m_house_number);

          addr.setStreetNumber(m_street_number);
          addr.setStreet(m_street);
          addr.setPlace(m_place);
          addr.setSuburb(m_suburb);
          addr.setCity(m_city);
          addr.setDistrict(m_district);
          addr.setRegion(m_region);
          addr.setCountryCode("CZ");
          addr.setPostCode(m_postcode);

          m_address_places.add(addr);
        }
      } catch (Exception e) {
      }

      if (m_address_places.size() == 0 &&
            m_house_number.length() > 0) {

        Address addr = new Address();
        addr.setRuianID(m_adr_id);

        if (m_house_number_typ.equals("číslo popisné"))
          addr.setConscriptionNumber(m_house_number);
        else if (m_house_number_typ.equals("číslo evidenční"))
          addr.setProvisionalNumber(m_house_number);

        addr.setStreetNumber(m_street_number);
        addr.setStreet(m_street);
        addr.setPlace(m_place);
        addr.setSuburb(m_suburb);
        addr.setCity(m_city);
        addr.setDistrict(m_district);
        addr.setRegion(m_region);
        addr.setCountryCode("CZ");
        addr.setPostCode(m_postcode);

        m_address_places.add(addr);
      }
    } catch (Exception e) {
    }

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
   *  Return number of levels in the building
   *  @return Number of levels
   */
  public String getBuildingLevels() {
    if (m_levels > 0)
      return Integer.toString(m_levels);
    else
      return "";
  }

  /**
   *  Return number of flats in the building
   *  @return Number of flats
   */
  public String getBuildingFlats() {
    if (m_flats > 0)
      return Integer.toString(m_flats);
    else
      return "";
  }

  /**
   *  Return date of finish of building
   *  @return Date of finish
   */
  public String getBuildingFinished() {
    return m_finished;
  }

  /**
   *  Return key of building type
   *  @return Key of building type
   */
  public String getBuildingTagKey() {
    return m_usage_key;
  }

  /**
   *  Return type of building
   *  @return Type of building
   */
  public String getBuildingTagValue() {
    return m_usage_val;
  }

  /**
   *  Return type of building
   *  @return Type of building
   */
  public long getBuildingID() {
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
