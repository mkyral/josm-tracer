/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral
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
package org.openstreetmap.josm.plugins.tracer.modules.ruian;

import static org.openstreetmap.josm.tools.I18n.*;
import java.awt.Cursor;
import java.awt.Point;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.ConnectWays;


import org.xml.sax.SAXException;

public class RuianModule implements TracerModule {

    private boolean moduleEnabled;

    private final String source = "cuzk:ruian";
    private final String ruianUrl = "http://josm.poloha.net";


    public RuianModule(boolean enabled) {
        moduleEnabled = enabled;
    }

    public void init() {
    }

    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-ruian-sml");
    }

    public String getName() {
        return tr("RUIAN");
    }

    public boolean moduleIsEnabled() {
        return moduleEnabled;
    };

    public void setModuleIsEnabled(boolean enabled) {
        moduleEnabled = enabled;
    };



    public PleaseWaitRunnable trace(LatLon pos, boolean ctrl, boolean alt, boolean shift)
    {
        return new RuianTracerTask (pos, ctrl, alt, shift);
    }

    class RuianTracerTask extends PleaseWaitRunnable {

        private final LatLon m_pos;
        private final boolean m_ctrl;
        private final boolean m_alt;
        private final boolean m_shift;
        private final String m_url;

        private RuianRecord m_record;
        private Exception m_asyncException;
        private boolean m_cancelled;

        RuianTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
            this.m_record = null;
            this.m_asyncException = null;
            this.m_cancelled = false;

            TracerPreferences pref = TracerPreferences.getInstance();

            this.m_url = pref.isCustomRuainUrlEnabled() ? pref.getCustomRuainUrl() : ruianUrl;
        }

        protected void realRun() throws SAXException {
            ProgressMonitor pm = progressMonitor.createSubTaskMonitor(ProgressMonitor.ALL_TICKS, false);

            System.out.println("");
            System.out.println("-----------------");
            System.out.println("----- Trace -----");
            System.out.println("-----------------");
            System.out.println("");
            pm.beginTask(null, 3);

            try {
                RuianServer server = new RuianServer();
                m_record = server.trace(m_pos, m_url);
            }
            catch (Exception e) {
                m_asyncException = e;
            }
            finally {
                pm.finishTask ();
            }
        }

        protected void finish() {

            // Note: finish() is guaranteed to run on EDT, after realRun() succeeded.

            // Async download failed?
            if (m_asyncException != null) {
                m_asyncException.printStackTrace();
                TracerUtils.showNotification(tr("RUIAN download failed.") + "\n(" + m_pos.toDisplayString() + ")", "error");
                return;
            }

            // Cancelled by user?
            if (m_cancelled) {
                return;
            }

            // No data available?
            if (m_record.getCoorCount() == 0) {
                  TracerUtils.showNotification(tr("Data not available.")+ "\n(" + m_pos.toDisplayString() + ")", "warning");
                  return;
            }

            try {
                createTracedPolygon ();
            }
            catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        private void createTracedPolygon() {

            // Note: must be called from finish() only

            TracerPreferences pref = TracerPreferences.getInstance();
            double dAdjX = 0, dAdjY = 0;

            if (pref.isRuianAdjustPositionEnabled()) {
                dAdjX = pref.getRuianAdjustPositionLat();
                dAdjY = pref.getRuianAdjustPositionLon();
            }

            final List<Bounds> dsBounds = Main.main.getCurrentDataSet().getDataSourceBounds();
            final Collection<Command> commands = new LinkedList<Command>();

            // make nodes a way
            Way way = new Way();
            Node firstNode = null;
            // record.getCoorCount() - 1 - omit last node
            for (int i = 0; i < m_record.getCoorCount() - 1; i++) {
                Node node;

                // Apply corrections to node coordinates
                if (!pref.isRuianAdjustPositionEnabled()) {
                  node = new Node(m_record.getCoor(i));
                } else {
                  node = new Node(new LatLon(LatLon.roundToOsmPrecision(m_record.getCoor(i).lat()+dAdjX),
                                             LatLon.roundToOsmPrecision(m_record.getCoor(i).lon()+dAdjY)));
                }
                if (firstNode == null) {
                    firstNode = node;
                }
                // Check. whether traced node is inside downloaded area
                int insideCnt = 0;
                for (Bounds b: dsBounds) {
                    if (b.contains(node.getCoor())) {
                        insideCnt++;
                    }
                }
                if (insideCnt == 0) {
                    ExtendedDialog ed = new ExtendedDialog(
                        Main.parent, tr("Way is outside downloaded area"),
                            new String[] {tr("Ok")});
                    ed.setButtonIcons(new String[] {"ok"});
                    ed.setIcon(JOptionPane.ERROR_MESSAGE);
                    ed.setContent(tr("Sorry.\nThe traced way (or part of the way) is outside of the downloaded area.\nPlease download area around the way and try again."));
                    ed.showDialog();
                    return;
                }
                commands.add(new AddCommand(node));
                way.addNode(node);
            }

            // Insert first node again - close the polygon.
            way.addNode(firstNode);

            System.out.println("TracedWay: " + way.toString());
            tagBuilding(way);

            // connect to other buildings or modify existing building
            ConnectWays connectWays = new ConnectWays();
            Command connCmd = connectWays.connect(way, m_pos, m_ctrl, m_alt, source);

            // Nothing changed?
            String s[] = connCmd.getDescriptionText().split(": ");
            if (s[1].equals("Nothing")) {
                TracerUtils.showNotification(tr("Nothing changed."), "info");
                if (m_shift) {
                    Main.main.getCurrentDataSet().addSelected(connectWays.getWay());
                } else {
                    Main.main.getCurrentDataSet().setSelected(connectWays.getWay());
                }
                return;
            }

            commands.add(connCmd);

            if (!commands.isEmpty()) {
                String strCommand;
                if (connectWays.isNewWay()) {
                    strCommand = trn("Tracer(RUIAN): add a way with {0} point", "Tracer(RUIAN): add a way with {0} points", connectWays.getWay().getRealNodesCount(), connectWays.getWay().getRealNodesCount());
                } else {
                    strCommand = trn("Tracer(RUIAN): modify way to {0} point", "Tracer(RUIAN): modify way to {0} points", connectWays.getWay().getRealNodesCount(), connectWays.getWay().getRealNodesCount());
                }

                Main.main.undoRedo.add(new SequenceCommand(strCommand, commands));

                if (m_shift) {
                    Main.main.getCurrentDataSet().addSelected(connectWays.getWay());
                } else {
                    Main.main.getCurrentDataSet().setSelected(connectWays.getWay());
                }
            } else {
                System.out.println("Failed");
            }
        }

        protected void cancel() {
            m_cancelled = true;
            // #### TODO: break the connection to RUIAN server
        }

        private void tagBuilding(Way way) {

            if(!m_alt) {
                if (m_record.getBuildingTagKey().equals("building") &&
                    m_record.getBuildingTagValue().length() > 0) {
                    way.put("building", m_record.getBuildingTagValue());
                }
                else {
                    way.put("building", "yes");
                }
            }

            if (m_record.getBuildingID() > 0 ) {
                way.put("ref:ruian:building", Long.toString(m_record.getBuildingID()));
            }

            if (m_record.getBuildingUsageCode().length() > 0) {
                way.put("building:ruian:type", m_record.getBuildingUsageCode());
            }

            if (m_record.getBuildingLevels().length() > 0) {
                way.put("building:levels", m_record.getBuildingLevels());
            }

            if (m_record.getBuildingFlats().length() > 0) {
                way.put("building:flats", m_record.getBuildingFlats());
            }

            if (m_record.getBuildingFinished().length() > 0) {
                way.put("start_date", m_record.getBuildingFinished());
            }

            if (m_record.getSource().length() > 0) {
                way.put("source", m_record.getSource());
            }
            else {
                way.put("source", source);
            }
        }
    }
}

