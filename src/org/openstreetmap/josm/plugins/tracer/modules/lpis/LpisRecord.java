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

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.tools.Utils;

import java.util.*;
import java.lang.StringBuilder;
import java.io.ByteArrayInputStream;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;

import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

// import org.openstreetmap.josm.plugins.tracer.TracerUtils.*;
// import org.openstreetmap.josm.plugins.tracer.modules.lpis.*;


/**
 * The Tracer LPIS record class
 *
 */

class geom {
  private ArrayList <LatLon> m_outer;
  private ArrayList <ArrayList<LatLon>> m_inners = new ArrayList<ArrayList<LatLon>>();

  public void setOuter (ArrayList <LatLon> o) {
    m_outer = o;
  }

  public void resetInners () {
    m_inners.clear();
  }

  public void addInner (ArrayList <LatLon> in) {
    if (in != null)
      m_inners.add(in);
  }


  public boolean isOuterSet() {
  if (m_outer == null && m_outer.size() > 0)
      return false;
    else
      return true;
  }

  public ArrayList <LatLon> getOuter() {
    return m_outer;
  }

  public int getInnersCount() {
    if (m_inners == null)
      return 0;
    else
      return m_inners.size();
  }

  public ArrayList <LatLon> getInner(int i) {
    return m_inners.get(i);
  }
}

public class LpisRecord {

    private long     m_lpis_id;
    private geom     m_geometry;
    private String   m_usage;
    private Map <String, String> m_usageOsm;


    /**
    * Constructor
    *
    */
    public void LpisRecord () {
      init();
    }

    /**
    * Initialization
    *
    */
    public void init () {

      m_lpis_id = -1;
      m_usage = "";
      m_usageOsm = new HashMap <String, String>();
      m_geometry = new geom();
      m_geometry.resetInners();

    }

    private void mapToOsm () {
      switch (m_usage) {
        case "orná půda": m_usageOsm.put("landuse", "farmland"); break;
        case "chmelnice": m_usageOsm.put("landuse", "farmland");
                          m_usageOsm.put("crop", "hop"); break;
        case "vinice": m_usageOsm.put("landuse", "vineyard"); break;
        case "ovocný sad": m_usageOsm.put("landuse", "orchard"); break;
        case "travní porost": m_usageOsm.put("landuse", "meadow");
                              m_usageOsm.put("meadow", "agricultural"); break;
        case "porost RRD": m_usageOsm.put("landuse", "forest");
                           m_usageOsm.put("crop", "fast_growing_wood"); break;
        case "RRD": m_usageOsm.put("landuse", "forest");
                    m_usageOsm.put("crop", "fast_growing_wood"); break;
        case "zalesněná půda": m_usageOsm.put("landuse", "forest"); break;
        case "rybník": m_usageOsm.put("natural", "water");
                       m_usageOsm.put("water", "pond"); break;
        case "jiná kultura": m_usageOsm.put("landuse", "farmland"); break;
        case "jiná kultura (školka)": m_usageOsm.put("landuse", "plant_nursery"); break;
        case "školka": m_usageOsm.put("landuse", "plant_nursery"); break;
        case "jiná kultura (zelinářská zahrada)": m_usageOsm.put("landuse", "farmland");
                                                  m_usageOsm.put("crop", "vegetables"); break;
        case "zelinářská zahrada": m_usageOsm.put("landuse", "farmland");
                                   m_usageOsm.put("crop", "vegetables"); break;
        default: System.out.println("  Warning: unknown value: " + m_usage);
                 TracerUtils.showNotification(tr("Tracer: Not mapped value found: ")+ m_usage + ".\n " + tr("Please report it to @talk-cz"), "error", 5000);
      }
    }

    private ArrayList<LatLon> parseGeometry (String geometry) {

      try {
        ArrayList<LatLon> arrList = new ArrayList<LatLon>();

        String[] coorVal = geometry.split(" ");
        for (int i = 0; i < coorVal.length; i = i + 2) {
          String x = coorVal[i];
          String y = coorVal[i+1];
          krovak k = new krovak();
          LatLon ll = k.krovak2LatLon(x, y);

          arrList.add(ll);
        }
        return arrList;
      } catch (Exception e) {
        e.printStackTrace();
        return new ArrayList<LatLon>();
      }
    }

    /**
    * Parse given XML string and fill variables with LPIS data
    * There are two modes:
    *   - basic - get LPIS ID and geometry
    *   - extra - get type (landuse) of the element
    *  @param action - basic or extra
    *  @param xmlStr - data for parsing
    *
    */
    public void parseXML (String action, String xmlStr) {

    System.out.println("");
    System.out.println("parseXML() - Start");

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();

      Document doc = builder.parse(new ByteArrayInputStream(xmlStr.getBytes("UTF-8")));
      doc.getDocumentElement().normalize();

      XPath xPath =  XPathFactory.newInstance().newXPath();
      if (action == "basic") {
      init();
  //       String expID = "/wfs:FeatureCollection/gml:featureMember/ms:LPIS_FB4_BBOX/ms:idPudnihoBloku";
        String expID = "//*[name()='ms:LPIS_FB4_BBOX'][1]/*[name()='ms:idPudnihoBloku']";
        String expOuter = "//*[name()='ms:LPIS_FB4_BBOX'][1]//*[name()='gml:exterior']//*[name()='gml:posList']";
        String expInner = "//*[name()='ms:LPIS_FB4_BBOX'][1]//*[name()='gml:interior']//*[name()='gml:posList']";

        NodeList nodeList;

        System.out.println("parseXML(basic) - expID: " + expID);
        nodeList = (NodeList) xPath.compile(expID).evaluate(doc, XPathConstants.NODESET);
        if (nodeList.getLength() > 0) {
          m_lpis_id = Long.parseLong(nodeList.item(0).getFirstChild().getNodeValue());
        } else {
          return;
        }

        System.out.println("parseXML(basic) - m_lpis_id: " + m_lpis_id);

        System.out.println("parseXML(nasic) - expOuter: " + expOuter);
        nodeList = (NodeList) xPath.compile(expOuter).evaluate(doc, XPathConstants.NODESET);
        String outer = nodeList.item(0).getFirstChild().getNodeValue();
        System.out.println("parseXML(basic) - outer: " + outer);
        m_geometry.setOuter(parseGeometry(outer));
        System.out.println("parseXML(basic) - outer list: " + m_geometry.getOuter());

        System.out.println("parseXML(basic) - expInner: " + expInner);
        nodeList = (NodeList) xPath.compile(expInner).evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            String inner = nodeList.item(i).getFirstChild().getNodeValue();
            System.out.println("Inner("+i+": "+ inner);
            m_geometry.addInner(parseGeometry(inner));
        }
        for (int i = 0; i < m_geometry.getInnersCount(); i++) {
          System.out.println("parseXML(basic) - Inner("+i+"): " + m_geometry.getInner(i));
        }
      } else {
        String expUsage = "//*[name()='ms:LPIS_FB4_01'][1]/*[name()='ms:kultura']";
        NodeList nodeList;

        System.out.println("parseXML(extra) - expUsage: " + expUsage);
        nodeList = (NodeList) xPath.compile(expUsage).evaluate(doc, XPathConstants.NODESET);
        m_usage = nodeList.item(0).getFirstChild().getNodeValue();
        mapToOsm();
        System.out.println("parseXML(extra) - m_usage: " + m_usage);
      }

    } catch (Exception e) {
        e.printStackTrace();
    }

    System.out.println("parseXML() - End");
  }

  /**
   *  Return outer polygon
   *  @return Outer polygon nodes
   */
  public ArrayList <LatLon> getOuter() {
    return m_geometry.getOuter();
  }

  /**
   *  Return whether there are inners
   *  @return True/False
   */
  public boolean hasInners() {
    if (m_geometry.getInnersCount() > 0)
      return true;
    return false;
  }

  /**
   *  Return number of inners
   *  @return Count of inners
   */
  public int getInnersCount() {
      return m_geometry.getInnersCount();
  }

  /**
   *  Return inner on given index
   *  @return Inner on given index
   */
  public ArrayList <LatLon> getInner(int i) {
      return m_geometry.getInner(i);
  }

  /**
   *  Return usage
   *  @return usage
   */
  public String getUsage() {
    return m_usage;
  }

  /**
   *  Return usage
   *  @return usage in Key/Value Map
   */
   public Map<String, String> getUsageOsm() {
    return m_usageOsm;
  }

  /**
   *  Return LPIS id
   *  @return LPIS id
   */
  public long getLpisID() {
    return m_lpis_id;
  }
}
