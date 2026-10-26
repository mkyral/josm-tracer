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

package org.openstreetmap.josm.plugins.tracer.modules.ruianLandsCuzk;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.*;
import jakarta.json.JsonObject;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.CuzkRuian;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;


/**
 * The Tracer RUIAN record class
 *
 */

public final class RuianLandsCuzkRecord extends TracerRecord {

    private String               m_source;
    private long                 m_ruian_id;
    private Map <String, String> m_keys;

    public RuianLandsCuzkRecord (double adjlat, double adjlon) {
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
     * Map RUIAN parcel kind (SC_D_POZEMKU) and use (SC_ZP_VYUZITI_POZ) codes to OSM keys
     *
     * @return False when the combination has no OSM tagging
     */
    private boolean mapKeys (long kind, long use) {
      switch ((int) kind) {
        case 2: // orná půda
          m_keys.put("landuse", "farmland"); return true;
        case 3: // chmelnice
          m_keys.put("landuse", "farmland");
          m_keys.put("crop", "hop"); return true;
        case 4: // vinice
          m_keys.put("landuse", "vineyard"); return true;
        case 5: // zahrada
          m_keys.put("leisure", "garden");
          m_keys.put("garden:type", "residential"); return true;
        case 6: // ovocný sad
          m_keys.put("landuse", "orchard"); return true;
        case 7: case 8: // trvalý travní porost
          m_keys.put("landuse", "meadow");
          m_keys.put("meadow", "agricultural"); return true;
        case 10: // lesní pozemek
          m_keys.put("landuse", "forest"); return true;
        case 13: // zastavěná plocha a nádvoří
          m_keys.put("building", "yes"); return true;
        case 11: case 14: // vodní plocha, ostatní plocha
          break;
        default:
          return false;
      }

      switch ((int) use) {
        case 1: // skleník, pařeniště
          m_keys.put("landuse", "common"); return true;
        case 2: // školka
          m_keys.put("landuse", "plant_nursery"); return true;
        case 3: // plantáž dřevin
          m_keys.put("natural", "wood");
          m_keys.put("crop", "fast_growing_wood"); return true;
        case 4: // les jiný než hospodářský
          m_keys.put("natural", "wood"); return true;
        case 5: // lesní pozemek, na kterém je budova
          m_keys.put("landuse", "forest"); return true;
        case 6: // rybník
          m_keys.put("natural", "water");
          m_keys.put("water", "pond"); return true;
        case 7: case 8: // koryto vodního toku
          m_keys.put("landuse", "river"); return true;
        case 9: // vodní nádrž přírodní
          m_keys.put("natural", "water");
          m_keys.put("water", "lake"); return true;
        case 10: // vodní nádrž umělá
          m_keys.put("natural", "water");
          m_keys.put("water", "reservoir"); return true;
        case 11: // zamokřená plocha
          m_keys.put("natural", "wetland"); return true;
        case 13: // zbořeniště
          m_keys.put("landuse", "brownfield"); return true;
        case 14: // dráha
          m_keys.put("landuse", "railway"); return true;
        case 15: case 16: case 17: // dálnice, silnice, ostatní komunikace
          m_keys.put("landuse", "highway"); return true;
        case 19: // zeleň
          m_keys.put("leisure", "common"); return true;
        case 20: // sportoviště a rekreační plocha
          m_keys.put("landuse", "recreation_ground"); return true;
        case 21: // pohřebiště
          m_keys.put("landuse", "cemetery"); return true;
        case 23: // manipulační plocha
          m_keys.put("highway", "service");
          m_keys.put("area", "yes"); return true;
        case 24: // dobývací prostor
          m_keys.put("landuse", "quarry"); return true;
        case 25: // skládka
          m_keys.put("landuse", "landfill"); return true;
        case 28: // vodní plocha, na které je budova
          m_keys.put("natural", "water"); return true;
        case 29: // fotovoltaická elektrárna
          m_keys.put("power", "generator");
          m_keys.put("generator:source", "solar");
          m_keys.put("generator:output:electricity", "yes");
          m_keys.put("generator:method", "photovoltaic"); return true;
        default: // 12 společný dvůr, 18, 22, 26, 27, 30 and unset
          return false;
      }
    }

    /**
     * Fill record from a Parcela feature of the ČÚZK RÚIAN service
     *
     * @param feature Feature with attributes and geometry
     * @param pos Traced position
     * @throws IOException Geometry without outer ring, or a parcel type without OSM tagging
     */
    public void parseCuzk(JsonObject feature, LatLon pos) throws IOException {

        init();
        m_source = "cuzk:ruian";

        JsonObject attrs = feature.getJsonObject("attributes");
        m_ruian_id = CuzkRuian.number(attrs, "id");
        long kind = CuzkRuian.number(attrs, "druhpozemkukod");
        long use = CuzkRuian.number(attrs, "zpusobyvyuzitipozemku");
        if (!mapKeys(kind, use))
            throw new IOException(tr("RUIAN parcel {0} (land type {1}, use {2}) has no OSM tagging", Long.toString(m_ruian_id), kind, use));
        m_keys.put("source", m_source);

        super.setOuter(CuzkRuian.partAt(feature.getJsonObject("geometry"), pos).outer);
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
