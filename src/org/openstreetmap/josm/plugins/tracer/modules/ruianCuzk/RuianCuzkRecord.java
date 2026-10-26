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

package org.openstreetmap.josm.plugins.tracer.modules.ruianCuzk;

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

public final class RuianCuzkRecord extends TracerRecord {

    private String   m_source;
    private long     m_ruian_id;
    private int      m_levels;
    private int      m_flats;
    private String   m_usage_code;
    private String   m_usage_val;
    private String   m_finished;

    /** building=* per SC_ZP_VYUZITI_BUD code as poloha.net tagged it: wiki Cs:RÚIAN, column "Současný stav dle Petra" */
    private static final Map<Integer, String> BUILDING_BY_USAGE = Map.ofEntries(
        Map.entry(1, "industrial"), Map.entry(2, "farm_auxiliary"), Map.entry(3, "residential"),
        Map.entry(4, "yes"), Map.entry(5, "civic"), Map.entry(6, "residential"),
        Map.entry(7, "house"), Map.entry(8, "yes"), Map.entry(9, "civic"),
        Map.entry(10, "commercial"), Map.entry(11, "hotel"), Map.entry(12, "warehouse"),
        Map.entry(13, "barn"), Map.entry(14, "commercial"), Map.entry(15, "civic"),
        Map.entry(16, "industrial"), Map.entry(17, "transportation"), Map.entry(18, "garage"),
        Map.entry(19, "yes"), Map.entry(20, "yes"), Map.entry(21, "greenhouse"),
        Map.entry(27, "transportation"), Map.entry(28, "industrial"), Map.entry(29, "yes"));

    public RuianCuzkRecord (double adjlat, double adjlon) {
        super (adjlat, adjlon);
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        m_source = "";
        m_ruian_id = -1;
        m_levels = 0;
        m_flats = 0;
        m_usage_code = "";
        m_usage_val = "";
        m_finished = "";
    }

    /**
     * Fill record from a StavebniObjekt feature of the ČÚZK RÚIAN service
     *
     * @param feature Feature with attributes and geometry
     * @param pos Traced position, selects the part of a multi-part building
     * @throws IOException Geometry without any outer ring
     */
    public void parseCuzk(JsonObject feature, LatLon pos) throws IOException {

        init();
        m_source = "cuzk:ruian";

        CuzkRuian.Polygon part = CuzkRuian.partAt(feature.getJsonObject("geometry"), pos);
        super.setOuter(part.outer);
        for (List<LatLon> inner : part.inners)
            super.addInner(inner);

        JsonObject attrs = feature.getJsonObject("attributes");
        m_ruian_id = CuzkRuian.number(attrs, "kod");
        m_levels = (int) Math.max(0, CuzkRuian.number(attrs, "pocetpodlazi"));
        m_flats = (int) Math.max(0, CuzkRuian.number(attrs, "pocetbytu"));
        long usage = CuzkRuian.number(attrs, "zpusobvyuzitikod");
        if (usage > 0) {
            m_usage_code = Long.toString(usage);
            m_usage_val = BUILDING_BY_USAGE.getOrDefault((int) usage, "");
        }
        m_finished = CuzkRuian.date(attrs, "dokonceni");
    }

    /**
     * Returns the number of levels in the building
     *
     * @return Number of levels
     */
    public String getBuildingLevels() {
        if (m_levels > 0) {
            return Integer.toString(m_levels);
        } else {
            return "";
        }
    }

    /**
     * Returns the number of flats in the building
     *
     * @return Number of flats
     */
    public String getBuildingFlats() {
        if (m_flats > 0) {
            return Integer.toString(m_flats);
        } else {
            return "";
        }
    }

    /**
     * Returns building's date of finish
     *
     * @return Date of finish
     */
    public String getBuildingFinished() {
        return m_finished;
    }

    /**
     * Returns RUIAN building usage code
     *
     * @return RUIAN building usage code
     */
    public String getBuildingUsageCode() {
        return m_usage_code;
    }

    /**
     * Returns type of building
     *
     * @return Type of building
     */
    public String getBuildingTagValue() {
        return m_usage_val;
    }

    /**
     * Returns Ruian ID of the building
     *
     * @return Building Ruian ID
     */
    public long getBuildingID() {
        return m_ruian_id;
    }

    /**
     * Returns data source
     *
     * @return Data source
     */
    public String getSource() {
        return m_source;
    }

    @Override
    public Map<String, String> getKeys(boolean m_alt) {

        Map<String, String> tags = new HashMap<>();

        if (!m_alt) {
            if (getBuildingTagValue().length() > 0) {
                tags.put("building", getBuildingTagValue());
            } else {
                tags.put("building", "yes");
            }
        }

        if (getBuildingID() > 0) {
            tags.put("ref:ruian:building", Long.toString(getBuildingID()));
        }

        if (getBuildingUsageCode().length() > 0) {
            tags.put("building:ruian:type", getBuildingUsageCode());
        }

        if (getBuildingLevels().length() > 0) {
            tags.put("building:levels", getBuildingLevels());
        }

        if (getBuildingFlats().length() > 0) {
            tags.put("building:flats", getBuildingFlats());
        }

        if (getBuildingFinished().length() > 0) {
            tags.put("start_date", getBuildingFinished());
        }

        if (getSource().length() > 0) {
            tags.put("source", getSource());
        } else {
            tags.put("source", "cuzk:ruian");
        }

        return tags;
    }

    @Override
    public boolean hasData() {
        return this.getBuildingID() > 0 && super.hasOuter();
    }
}
