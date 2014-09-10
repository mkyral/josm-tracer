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

package org.openstreetmap.josm.plugins.tracer.modules.ruian;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.tools.Utils;

import java.util.*;

/**
 * Private class to store address places
 *
 */

public class Address {
    private long    m_ruian_id;
    private String  m_conscriptionnumber; // cislo popisne
    private String  m_provisionalnumber; // cislo popisne
    private String  m_streetnumber; // cislo orientacni
    private String  m_street; // ulice
    private String  m_place; // Mistni cast
    private String  m_suburb; // Mestska cas
    private String  m_city; // Obec nebo mesto
    private String  m_district; // okres
    private String  m_region; // kraj
    private String  m_postcode; // PSC
    private String  m_countrycode; // kod zeme

    public Address () {
      init();
    }

    private void init () {
      m_ruian_id = 0;
      m_conscriptionnumber = "";
      m_provisionalnumber= "";
      m_streetnumber= "";
      m_street= "";
      m_place= "";
      m_suburb= "";
      m_city= "";
      m_district= "";
      m_region= "";
      m_postcode= "";
      m_countrycode= "";
    }

// ----------------------------------------------------
    public void setRuianID (long v) {
      m_ruian_id = v;
    }

    public void setConscriptionNumber (String v) {
      m_conscriptionnumber = v;
    }

    public void setProvisionalNumber (String v) {
      m_provisionalnumber = v;
    }

    public void setStreetNumber (String v) {
      m_streetnumber = v;
    }

    public void setStreet (String v) {
      m_street = v;
    }

    public void setPlace (String v) {
      m_place = v;
    }

    public void setSuburb (String v) {
      m_suburb = v;
    }

    public void setCity (String v) {
      m_city = v;
    }

    public void setDistrict (String v) {
      m_district = v;
    }

    public void setRegion (String v) {
      m_region = v;
    }

    public void setCountryCode (String v) {
      m_countrycode = v;
    }

    public void setPostCode (String v) {
      m_countrycode = v;
    }
// ----------------------------------------------------
    public long getRuianID () {
      return m_ruian_id;
    }

    public String getConscriptionNumber () {
      return m_conscriptionnumber;
    }


    public String getProvisionalNumber () {
      return m_provisionalnumber;
    }


    public String getStreetNumber () {
      return m_streetnumber;
    }


    public String getStreet () {
      return m_street;
    }


    public String getPlace () {
      return m_place;
    }


    public String getSuburb () {
      return m_suburb;
    }


    public String getCity () {
      return m_city;
    }


    public String getDistrict () {
      return m_district;
    }


    public String getRegion () {
      return m_region;
    }


    public String getPostCode () {
      return m_postcode;
    }


    public String getCountryCode () {
      return m_countrycode;
    }

    public String getHouseNumber() {
      String housenumber;
      if (m_conscriptionnumber.length() > 0 && m_streetnumber.length() > 0) {
        housenumber = m_conscriptionnumber + "/" + m_streetnumber;
      } else if (m_provisionalnumber.length() > 0 && m_streetnumber.length() > 0) {
        housenumber = "ev." + m_provisionalnumber + "/" + m_streetnumber;
      } else if (m_conscriptionnumber.length() > 0) {
        housenumber = m_conscriptionnumber;
      } else if (m_provisionalnumber.length() > 0) {
        housenumber = "ev." + m_provisionalnumber;
      } else {
        housenumber = "";
      }

      return housenumber;
    }

}