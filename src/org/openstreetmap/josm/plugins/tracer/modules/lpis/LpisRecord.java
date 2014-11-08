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

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import static org.openstreetmap.josm.tools.I18n.tr;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * The Tracer LPIS record class
 *
 */

public class LpisRecord {

    private long     m_lpis_id;
    private String   m_usage;
    private Map <String, String> m_usageOsm;

    private List<LatLon> m_outer;
    private List<List<LatLon>> m_inners;

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
        m_usageOsm = new HashMap <>();
        m_outer = null;
        m_inners = new ArrayList<>();
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

        ArrayList<LatLon> arrList = new ArrayList<>();
        LatLon prevCoor = null;

        String[] coorVal = geometry.split(" ");
        for (int i = 0; i < coorVal.length; i = i + 2) {
            String x = coorVal[i];
            String y = coorVal[i+1];
            krovak k = new krovak();
            LatLon ll = k.krovak2LatLon(x, y);

            // Sometimes, after rouding, two nodes could have the same LatLon coordinates
            // Skip duplicated coordinate
            if (prevCoor == null || !ll.equalsEpsilon(prevCoor)) {
                arrList.add(ll);
                prevCoor = ll;
            }
        }
        return arrList;
    }

    /**
    * Parse given XML string and fill variables with LPIS data
    * There are two modes:
    *   - basic - get LPIS ID and geometry
    *   - extra - get type (landuse) of the element
    *  @param action - basic or extra
    *  @param xmlStr - data for parsing
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     * @throws javax.xml.xpath.XPathExpressionException
    *
    */
    public void parseXML (String action, String xmlStr) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {

        System.out.println("");
        System.out.println("parseXML() - Start");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.parse(new ByteArrayInputStream(xmlStr.getBytes("UTF-8")));
        doc.getDocumentElement().normalize();

        XPath xPath =  XPathFactory.newInstance().newXPath();
        if ("basic".equals(action)) {
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

            System.out.println("parseXML(basic) - expOuter: " + expOuter);
            nodeList = (NodeList) xPath.compile(expOuter).evaluate(doc, XPathConstants.NODESET);
            String outer = nodeList.item(0).getFirstChild().getNodeValue();
            System.out.println("parseXML(basic) - outer: " + outer);
            m_outer = parseGeometry(outer);
            System.out.println("parseXML(basic) - outer list: " + m_outer);

            System.out.println("parseXML(basic) - expInner: " + expInner);
            nodeList = (NodeList) xPath.compile(expInner).evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                String inner = nodeList.item(i).getFirstChild().getNodeValue();
                System.out.println("Inner("+i+": "+ inner);
                m_inners.add(parseGeometry(inner));
            }
            for (int i = 0; i < m_inners.size(); i++) {
                System.out.println("parseXML(basic) - Inner("+i+"): " + m_inners.get(i));
            }
        } else {
            String expUsage = "//*[name()='ms:LPIS_FB4'][1]/*[name()='ms:kultura']";
            NodeList nodeList;

            System.out.println("parseXML(extra) - expUsage: " + expUsage);
            nodeList = (NodeList) xPath.compile(expUsage).evaluate(doc, XPathConstants.NODESET);
            m_usage = nodeList.item(0).getFirstChild().getNodeValue();
            mapToOsm();
            System.out.println("parseXML(extra) - m_usage: " + m_usage);
        }

        System.out.println("parseXML() - End");
    }

    /**
     *  Return outer polygon
     *  @return Outer polygon nodes
     */
    public List <LatLon> getOuter() {
        if (m_outer == null)
            throw new IllegalStateException("No outer geometry available");
        return Collections.unmodifiableList(m_outer);
    }

    /**
     *  Return whether there are inners
     *  @return True/False
     */
    public boolean hasInners() {
        return m_inners.size() > 0;
    }

    /**
     *  Return number of inners
     *  @return Count of inners
     */
    public List<List<LatLon>> getInners() {
        return Collections.unmodifiableList(m_inners);
    }

    /**
     * Returns BBox of LPIS (multi)polygon
     * @return BBox of LPIS (multi)polygon
     */
    public BBox getBBox() {
        List<LatLon> outer = getOuter();
        LatLon p0 = outer.get(0);
      
        BBox bbox = new BBox(p0.lon(), p0.lat());
        for (int i = 1; i < outer.size(); i++) {
            LatLon p = outer.get(i);
            bbox.add(p.lon(), p.lat());
        }
 
        return bbox;
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
