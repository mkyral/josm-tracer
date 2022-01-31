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

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.data.coor.LatLon;
// import org.openstreetmap.josm.plugins.tracer.xyCoor;

import org.locationtech.jts.geom.*;
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


  public krovak () {
    init();
  }

  private void init () {
    xy = new xyCoor();
    ll = new LatLon(0,0);

    try {
      LatLonCRS = DefaultGeographicCRS.WGS84;
      krovakCRS = CRS.decode("EPSG:5514", false);

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
      org.locationtech.jts.geom.GeometryFactory gf = new org.locationtech.jts.geom.GeometryFactory();
      org.locationtech.jts.geom.Coordinate c = new org.locationtech.jts.geom.Coordinate(xy.x(), xy.y());

      org.locationtech.jts.geom.Point p = gf.createPoint(c);

      MathTransform mathTransform = CRS.findMathTransform(krovakCRS, LatLonCRS);
      org.locationtech.jts.geom.Point p1 = (org.locationtech.jts.geom.Point) JTS.transform(p, mathTransform);

//       System.out.println(p1.getCoordinate());
      ll = new LatLon(LatLon.roundToOsmPrecision(p1.getY()),
                      LatLon.roundToOsmPrecision(p1.getX()));
    } catch (Exception e) {
      System.out.println("CRS conversion exception: " + e.getMessage());
    }

    return ll;
  }

  public xyCoor getXY () {

    xyCoor xy = new xyCoor();

    try {
      org.locationtech.jts.geom.GeometryFactory gf = new org.locationtech.jts.geom.GeometryFactory();
      org.locationtech.jts.geom.Coordinate c = new org.locationtech.jts.geom.Coordinate(ll.lon(), ll.lat());

      org.locationtech.jts.geom.Point p = gf.createPoint(c);

      MathTransform mathTransform = CRS.findMathTransform(LatLonCRS, krovakCRS);
      org.locationtech.jts.geom.Point p1 = (org.locationtech.jts.geom.Point) JTS.transform(p, mathTransform);

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

