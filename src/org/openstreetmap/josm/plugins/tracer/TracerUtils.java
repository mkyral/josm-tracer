/**
 *  PointInfo - plugin for JOSM
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


import com.vividsolutions.jts.geom.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import javax.swing.JOptionPane;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.util.GuiHelper;


public abstract class TracerUtils {

    /**
     * Show notification for 3 seconds. Runs in EDT thread.
     * @param message Message to shown.
     * @param type Type of message (info, warning, error, plain).
     */
    public static void showNotification (String message, String type) {
      showNotification(message, type, Notification.TIME_SHORT);
    }

    /**
     * Show notification. Runs in EDT thread.
     * @param message Message to shown.
     * @param type Type of message (info, warning, error, plain).
     * @param time How long will be the message displayed.
     */
    public static void showNotification (final String message, final String type, final int time) {
        GuiHelper.runInEDT(new Runnable() {
            @Override
            public void run() {

                Notification note = new Notification(message);

                if (type.equals("info"))
                    note.setIcon(JOptionPane.INFORMATION_MESSAGE);
                else if (type.equals("warning"))
                    note.setIcon(JOptionPane.WARNING_MESSAGE);
                else if (type.equals("error"))
                    note.setIcon(JOptionPane.ERROR_MESSAGE);
                else
                    note.setIcon(JOptionPane.PLAIN_MESSAGE);

                note.setDuration(time);
                note.show();
            }
        });
    }

    /**
     * Return text representation of coordinates.
     # @param  lat Lat coordinate
     # @param  lon Lon coordinate
     * @return String coordinatesText
     */
    public static String formatCoordinates (double lat, double lon) {

      String r = "";
      DecimalFormatSymbols symbols = new DecimalFormatSymbols();
      symbols.setDecimalSeparator('.');
      symbols.setGroupingSeparator(' ');
      DecimalFormat df = new DecimalFormat("#.00000", symbols);

      r = "(" + df.format(lat) + ", " +
                df.format(lon) + ")";
      return r;
    }

    /**
     * Convert date from Czech to OSM format
     * @param ruianDate Date in RUIAN (Czech) format DD.MM.YYYY
     * @return String with date converted to OSM data format YYYY-MM-DD
     */
    public static String convertDate (String ruianDate) {
      String r = new String();
      String[] parts = ruianDate.split("\\.");
      try {
        int day =   Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year =  Integer.parseInt(parts[2]);
        r = new Integer(year).toString() + "-" + String.format("%02d", month) + "-" + String.format("%02d", day);
      } catch (Exception e) {
      }

      return r;
    }


    public static BufferedReader openUrlStream (String url) throws IOException {
        return openUrlStream (url, null);
    }

    public static BufferedReader openUrlStream (String url, String charset) throws MalformedURLException, IOException {
        URLConnection conn = null;
        boolean succeeded = false;
        try {
             conn = new URL(url).openConnection();

             // set hardcoded 10 sec timeouts
             conn.setConnectTimeout(10000);
             conn.setReadTimeout(10000);

             InputStreamReader isr = charset != null ?
                     new InputStreamReader(conn.getInputStream(), charset) : new InputStreamReader(conn.getInputStream());
             BufferedReader reader = new BufferedReader(isr);
             succeeded = true;
             return reader;
        }
        finally {
            if (!succeeded && conn != null) {
                try {
                    conn.getInputStream().close();
                }
                catch (IOException e) {
                }
            }
        }
    }

 }
