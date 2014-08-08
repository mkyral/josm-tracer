/**
 *  Tracer - plugin for JOSM
 *  Marian Kyral
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.data.coor.LatLon;
// import org.openstreetmap.josm.plugins.tracer.xyCoor;

import com.vividsolutions.jts.geom.*;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

public class krovak {

  private xyCoor xy;
  private LatLon ll;

  private CoordinateReferenceSystem LatLonCRS;
  private CoordinateReferenceSystem krovakCRS;


  /* Define Krovak projection */
  private String wkt = "PROJCS[\"S-JTSK / Krovak East North\","+
      "GEOGCS[\"S-JTSK\","+
      "  DATUM[\"System_Jednotne_Trigonometricke_Site_Katastralni\","+
      "    SPHEROID[\"Bessel 1841\",6377397.155,299.1528128,AUTHORITY[\"EPSG\",\"7004\"]],"+
      "    TOWGS84[570.8,85.7,462.8,4.998,1.587,5.261,3.56],AUTHORITY[\"EPSG\",\"6156\"]],"+
      "  PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],"+
      "  UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]],"+
      "  AUTHORITY[\"EPSG\",\"4156\"]],"+
      "  PROJECTION[\"Krovak\"],"+
      "PARAMETER[\"latitude_of_center\",49.5],"+
      "PARAMETER[\"longitude_of_center\",24.83333333333333],"+
      "PARAMETER[\"azimuth\",30.28813972222222],"+
      "PARAMETER[\"pseudo_standard_parallel_1\",78.5],"+
      "PARAMETER[\"scale_factor\",0.9999],"+
      "PARAMETER[\"false_easting\",0],"+
      "PARAMETER[\"false_northing\",0],"+
      "UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"+
      "AXIS[\"X\",EAST],"+
      "AXIS[\"Y\",NORTH],"+
      "AUTHORITY[\"EPSG\",\"5514\"]]";

  public krovak () {
    init();
  }

  private void init () {
    xy = new xyCoor();
    ll = new LatLon(0,0);

    try {
      LatLonCRS = DefaultGeographicCRS.WGS84;
      krovakCRS = CRS.parseWKT(wkt);

    } catch (Exception e) {
      System.out.println("krovak init() exception: " + e.getMessage());
    }
  }

  public void setXY (Double x, Double y) {
    xy = new xyCoor(x, y);
  }

  public void setLatLon (LatLon pll) {
    ll = pll;
  }

  public LatLon getLatLon () {

    LatLon ll = new LatLon(0,0);

    try {
      com.vividsolutions.jts.geom.GeometryFactory gf = new com.vividsolutions.jts.geom.GeometryFactory();
      com.vividsolutions.jts.geom.Coordinate c = new com.vividsolutions.jts.geom.Coordinate(xy.x(), xy.y());

      com.vividsolutions.jts.geom.Point p = gf.createPoint(c);

      MathTransform mathTransform = CRS.findMathTransform(krovakCRS, LatLonCRS, false);
      com.vividsolutions.jts.geom.Point p1 = (com.vividsolutions.jts.geom.Point) JTS.transform(p, mathTransform);

//       System.out.println(p1.getCoordinate());
      ll = new LatLon(p1.getY(), p1.getX());
    } catch (Exception e) {
      System.out.println("CRS conversion exception: " + e.getMessage());
    }

    return ll;
  }

  public xyCoor getXY () {

    xyCoor xy = new xyCoor();

    try {
      com.vividsolutions.jts.geom.GeometryFactory gf = new com.vividsolutions.jts.geom.GeometryFactory();
      com.vividsolutions.jts.geom.Coordinate c = new com.vividsolutions.jts.geom.Coordinate(ll.lon(), ll.lat());

      com.vividsolutions.jts.geom.Point p = gf.createPoint(c);

      MathTransform mathTransform = CRS.findMathTransform(LatLonCRS, krovakCRS, false);
      com.vividsolutions.jts.geom.Point p1 = (com.vividsolutions.jts.geom.Point) JTS.transform(p, mathTransform);

//       System.out.println(p1.getCoordinate());
      xy = new xyCoor(p1.getX(), p1.getY());
    } catch (Exception e) {
      System.out.println("CRS conversion exception: " + e.getMessage());
    }
    return xy;
  }

  /**
    * Convert coordinates from krovak to LatLon
    * @param x - the X coordinate
    * @param y - the Y coordinate
    * @return LatLon coordinates
    */
  public LatLon krovak2LatLon (String x, String y) {
    setXY(Double.parseDouble(x),Double.parseDouble(y));
    return getLatLon();
  }

  /**
    * Convert coordinates from krovak to LatLon
    * @param x - the X coordinate
    * @param y - the Y coordinate
    * @return LatLon coordinates
    */
  public LatLon krovak2LatLon (Double x, Double y) {
    setXY(x,y);
    return getLatLon();
  }

  /**
    * Convert coordinates from LatLon to krovak
    * @param ll - LatLon coordinates
    * @return Krovak coordinates
    */
  public xyCoor LatLon2krovak (LatLon ll) {
    setLatLon(ll);
    return getXY();
  }
}

