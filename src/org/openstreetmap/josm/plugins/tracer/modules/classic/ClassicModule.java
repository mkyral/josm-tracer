/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral, Martin Svec
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
package org.openstreetmap.josm.plugins.tracer.modules.classic;

import java.awt.Cursor;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.plugins.tracer.BuildingTracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;

public final class ClassicModule extends BuildingTracerModule {

    protected boolean cancel;
    private boolean moduleEnabled;

    private final String classicUrl = "http://localhost:5050/";

    public ClassicModule(boolean enabled) {
        moduleEnabled = enabled;
    }

    @Override
    public void init() {
    }

    @Override
    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-sml");
    }

    @Override
    public String getName() {
        return tr("Classic");
    }

    @Override
    public boolean moduleIsEnabled() {
      return moduleEnabled;
    };

    @Override
    public void setModuleIsEnabled(boolean enabled){
      moduleEnabled = enabled;
    };

    @Override
    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new ClassicTracerTask (pos, ctrl, alt, shift);
    }

    class ClassicTracerTask extends BuildingTracerTask {

        ClassicTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (pos, ctrl, alt ,shift);
        }

        @Override
        protected TracerRecord downloadRecord(LatLon pos) throws Exception {
            TracerPreferences pref = TracerPreferences.getInstance();
            String sUrl = classicUrl;
            if (pref.isCustomTracerUrlEnabled()) {
                sUrl = pref.getCustomTracerUrl();
            }

            // Get coordinate corrections
            double adjlat = 0, adjlon = 0;
            if (pref.isTracerAdjustPositionEnabled()) {
                adjlat = pref.getTracerAdjustPositionLat();
                adjlon = pref.getTracerAdjustPositionLon();
            }

            ClassicServer server = new ClassicServer();
            return server.trace(pos, sUrl, adjlat, adjlon);
        }
    }
}
