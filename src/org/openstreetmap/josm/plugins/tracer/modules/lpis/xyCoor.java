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

 /**
  * Class to store krovak XY coordinates
  */
public class xyCoor {
  private Double m_x;
  private Double m_y;

/**
  * Constructor
  */
  public xyCoor () {
    m_x = 0.;
    m_y = 0.;
  }

/**
  * Constructor
  * @param x - the X coordinate
  * @param y - the Y coordinate
  */
  public xyCoor (Double x, Double y) {
    m_x = x;
    m_y = y;
  }

/**
  * Set X and Y coordinates
  * @param x - the X coordinate
  * @param y - the Y coordinate
  */
  public void set (Double x, Double y) {
    m_x = x;
    m_y = y;
  }

/**
  * Return X coordinate
  * @return The X coordinate
  */
  public Double x () {
    return m_x;
  }

/**
  * Return Y coordinate
  * @return The Y coordinate
  */
  public Double y () {
    return m_y;
  }
}
