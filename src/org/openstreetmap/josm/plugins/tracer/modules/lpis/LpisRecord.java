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
import org.openstreetmap.josm.plugins.tracer.TracerRecord;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import static org.openstreetmap.josm.tools.I18n.tr;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * The Tracer LPIS record class
 *
 */

public final class LpisRecord extends TracerRecord {

    private long     m_lpis_id;
    private String   m_usage;
    private Map <String, String> m_usageOsm;

    /**
    * Constructor
    *
    */
    public LpisRecord (double adjlat, double adjlon) {
        super (adjlat, adjlon);
        init();
    }

    /**
    * Initialization
    *
    */
    @Override
    public void init () {
        super.init();
        m_lpis_id = -1;
        m_usage = "";
        m_usageOsm = new HashMap <>();
    }

    private void mapToOsm () {
        switch (m_usage) {
            case "orná půda":
                m_usageOsm.put("landuse", "farmland");
                break;
            case "chmelnice":
                m_usageOsm.put("landuse", "farmland");
                m_usageOsm.put("crop", "hop");
                break;
            case "vinice":
                m_usageOsm.put("landuse", "vineyard");
                break;
            case "ovocný sad":
                m_usageOsm.put("landuse", "orchard");
                break;
            case "travní porost":
                m_usageOsm.put("landuse", "meadow");
                m_usageOsm.put("meadow", "agricultural");
                break;
            case "porost RRD":
            case "RRD":
                m_usageOsm.put("landuse", "forest");
                m_usageOsm.put("crop", "fast_growing_wood");
                break;
            case "zalesněná půda":
                m_usageOsm.put("landuse", "forest");
                break;
            case "rybník":
                m_usageOsm.put("natural", "water");
                m_usageOsm.put("water", "pond");
                break;
            case "jiná kultura":
            case "jiná kultura neoprávněná pro dotace":
                m_usageOsm.put("landuse", "farmland");
                break;
            case "jiná kultura (školka)":
                m_usageOsm.put("landuse", "plant_nursery");
                break;
            case "školka":
                m_usageOsm.put("landuse", "plant_nursery");
                break;
            case "jiná kultura (zelinářská zahrada)":
                m_usageOsm.put("landuse", "farmland");
                m_usageOsm.put("crop", "vegetables");
                break;
            case "zelinářská zahrada":
                m_usageOsm.put("landuse", "farmland");
                m_usageOsm.put("crop", "vegetables");
                break;
            case "tráva na orné":
                m_usageOsm.put("landuse", "farmland");
                m_usageOsm.put("crop", "grass");
                break;
            case "úhor":
                m_usageOsm.put("landuse", "farmland");
                m_usageOsm.put("crop", "no");
                break;
            case "jiná trvalá kultura":
                m_usageOsm.put("landuse", "farmland");
                break;
            default:
                System.out.println("  Warning: unknown value: " + m_usage);
                TracerUtils.showNotification(tr("Tracer: Not mapped value found: ") + m_usage + ".\n " + tr("Please report it to @talk-cz"), "error", 5000);
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
            //       String expID = "/wfs:FeatureCollection/gml:featureMember/ms:LPIS_DPB_UCINNE_BBOX/ms:idPudnihoBloku";
            String expID = "//*[name()='ms:LPIS_DPB_UCINNE_BBOX'][1]/*[name()='ms:idPudnihoBloku']";
            String expOuter = "//*[name()='ms:LPIS_DPB_UCINNE_BBOX'][1]//*[name()='gml:exterior']//*[name()='gml:posList']";
            String expInner = "//*[name()='ms:LPIS_DPB_UCINNE_BBOX'][1]//*[name()='gml:interior']//*[name()='gml:posList']";

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
            List<LatLon> way = parseGeometry(outer);
            System.out.println("parseXML(basic) - outer list: " + way);
            super.setOuter(way);

            System.out.println("parseXML(basic) - expInner: " + expInner);
            nodeList = (NodeList) xPath.compile(expInner).evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                String inner = nodeList.item(i).getFirstChild().getNodeValue();
                System.out.println("Inner("+i+": "+ inner);
                super.addInner(parseGeometry(inner));
            }
            List<List<LatLon>> inner_ways = super.getInners();
            for (int i = 0; i < inner_ways.size(); i++) {
                System.out.println("parseXML(basic) - Inner("+i+"): " + inner_ways.get(i));
            }
        } else {
            String expUsage = "//*[name()='ms:LPIS_DPB_UCINNE'][1]/*[name()='ms:kultura']";
            NodeList nodeList;

            System.out.println("parseXML(extra) - expUsage: " + expUsage);
            nodeList = (NodeList) xPath.compile(expUsage).evaluate(doc, XPathConstants.NODESET);
            if (nodeList != null && nodeList.getLength() > 0 && nodeList.item(0).hasChildNodes()) {
                m_usage = nodeList.item(0).getFirstChild().getNodeValue();
                mapToOsm();
            }
            System.out.println("parseXML(extra) - m_usage: " + m_usage);
        }

        System.out.println("parseXML() - End");
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
    @Override
    public Map<String, String> getKeys(boolean alt) {
        Map <String, String> keys = new HashMap <> (m_usageOsm);

        keys.put("source", "lpis");
        keys.put("ref", Long.toString(m_lpis_id));
        return keys;
    }

    /**
     *  Return LPIS id
     *  @return LPIS id
     */
    public long getLpisID() {
        return m_lpis_id;
    }

    @Override
    public boolean hasData() {
        return m_lpis_id > 0 && super.hasOuter();
    }

    static List<LpisRecord> parseBasicXML(String content, double adjlat, double adjlon) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse (new ByteArrayInputStream(content.getBytes("utf-8")));
        doc.getDocumentElement().normalize();

        XPath xPath =  XPathFactory.newInstance().newXPath();
        String expID = "//*[name()='ms:LPIS_DPB_UCINNE_BBOX']/*[name()='ms:idPudnihoBloku']";

        System.out.println("parseXML(basic) - expID: " + expID);
        NodeList expids = (NodeList) xPath.compile(expID).evaluate(doc, XPathConstants.NODESET);

        List<LpisRecord> list = new ArrayList<> (expids.getLength());

        for (int i = 0; i < expids.getLength(); i++) {
            final long exp_id = Long.parseLong(expids.item(i).getFirstChild().getNodeValue());

            LpisRecord lpis = new LpisRecord (adjlat, adjlon);
            lpis.m_lpis_id = exp_id;

            String expOuter = "//*[name()='ms:LPIS_DPB_UCINNE_BBOX' and @*='LPIS_DPB_UCINNE_BBOX." + Long.toString(exp_id) + "']//*[name()='gml:exterior']//*[name()='gml:posList']";
            NodeList outernl = (NodeList) xPath.compile(expOuter).evaluate(doc, XPathConstants.NODESET);
            if (outernl.getLength() > 0) {
                String outer = outernl.item(0).getFirstChild().getNodeValue();
                List<LatLon> way = lpis.parseGeometry(outer);
                lpis.setOuter(way);
            } else {
                continue;
            }

            String expInner = "//*[name()='ms:LPIS_DPB_UCINNE_BBOX' and @*='LPIS_DPB_UCINNE_BBOX." + Long.toString(exp_id) + "']//*[name()='gml:interior']//*[name()='gml:posList']";
            NodeList innersnl = (NodeList) xPath.compile(expInner).evaluate(doc, XPathConstants.NODESET);

            for (int j = 0; j < innersnl.getLength(); j++) {
                String inner = innersnl.item(j).getFirstChild().getNodeValue();
                lpis.addInner(lpis.parseGeometry(inner));
            }

            list.add (lpis);
        }

        return list;
    }

}
