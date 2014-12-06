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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import static org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.Functions.tr;
import org.openstreetmap.josm.plugins.tracer.connectways.MultipolygonMatch;
import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Base class for Tracer modules
 *
 */

public abstract class TracerModule {
    /**
     *  Function for initialization
     */
    public abstract void init();

    /**
     *  Returns cursor image
     *  @return Module cursor image
     */
    public abstract Cursor getCursor();

    /**
     *  Returns module name
     *  @return the module name
     */
    public abstract String getName();

    /**
     *  Returns whether is module enabled or not
     *  @return True/False
     */
    public abstract boolean moduleIsEnabled();

    /**
     *  Sets module status to Enabled or Disabled
     *  @param enabled True/False
     */
    public abstract void setModuleIsEnabled(boolean enabled);

    /**
     *  Returns a tracer task that extracts the object for given position
     *  @param pos position to trase
     */
    public abstract PleaseWaitRunnable trace(LatLon pos, boolean ctrl, boolean alt, boolean shift);

    public abstract class AbstractTracerTask extends PleaseWaitRunnable {

        protected final LatLon m_pos;
        protected final boolean m_ctrl;
        protected final boolean m_alt;
        protected final boolean m_shift;

        protected AbstractTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
        }

        /**
         * Returns list of all existing incomplete multipolygons that might participate in
         * traced polygon clipping. These relations must be downloaded before clipping,
         * clipping doesn't support incomplete multipolygons.
         * @param box BBox of the traced geometry
         * @return List of incomplete multipolygon relations
         */
        protected List<Relation> getIncompleteMultipolygonsForDownload(BBox box) {
            DataSet ds = Main.main.getCurrentDataSet();
            ds.getReadLock().lock();
            try {
                List<Relation> list = new ArrayList<>();
                for (Relation rel : ds.searchRelations(box)) {
                    if (!MultipolygonMatch.match(rel))
                        continue;
                    if (rel.isIncomplete() || rel.hasIncompleteMembers())
                        list.add(rel);
                }
                return list;
            } finally {
                ds.getReadLock().unlock();
            }
        }

        protected void wayIsOutsideDownloadedAreaDialog() {
            ExtendedDialog ed = new ExtendedDialog(
                Main.parent, tr("Way is outside downloaded area"),
                new String[] {tr("Ok")});
            ed.setButtonIcons(new String[] {"ok"});
            ed.setIcon(JOptionPane.ERROR_MESSAGE);
            ed.setContent(tr("Sorry.\nThe traced way (or part of the way) is outside of the downloaded area.\nPlease download area around the way and try again."));
            ed.showDialog();
        }
    }
}

