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

import java.awt.Cursor;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * The main interface for Tracer modules
 *
 */

public interface TracerModule {
    /**
     *  Function for initialization
     */
    void init();

    /**
     *  Returns cursor image
     *  @return Module cursor image
     */
    Cursor getCursor();

    /**
     *  Returns module name
     *  @return the module name
     */
    String getName();

    /**
     *  Returns whether is module enabled or not
     *  @return True/False
     */
    boolean moduleIsEnabled();

    /**
     *  Sets module status to Enabled or Disabled
     *  @param enabled True/False
     */
    void setModuleIsEnabled(boolean enabled);

    /**
     *  Returns a tracer task that extracts the object for given position
     *  @param pos position to trase
     *  @param progressMonitor - the progressMonitor object
     */
    PleaseWaitRunnable trace(LatLon pos, boolean ctrl, boolean alt, boolean shift);
}

