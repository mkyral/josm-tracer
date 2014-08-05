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
package org.openstreetmap.josm.plugins.tracer;

import static org.openstreetmap.josm.tools.I18n.*;
import java.awt.Cursor;
import java.awt.Point;
// import java.awt.event.ActionEvent;
// import java.awt.event.InputEvent;
// import java.awt.event.KeyEvent;
// import java.awt.event.MouseEvent;
// import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.lang.StringBuilder;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;

// import org.openstreetmap.josm.plugins.tracer.modules.ruian.Address;
// import org.openstreetmap.josm.plugins.tracer.modules.ruian.RuianRecord;


import org.xml.sax.SAXException;

class RuianModule implements TracerModule {

    private static final long serialVersionUID = 1L;

    protected boolean cancel;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;
    private boolean moduleEnabled;
    private String source = "cuzk:ruian";
    private static RuianRecord record;

    private static StringBuilder msg = new StringBuilder();

    protected RuianServer server = new RuianServer();

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

    public void setModuleIsEnabled(boolean enabled){
      moduleEnabled = enabled;
    };

    public void trace(LatLon pos, ProgressMonitor progressMonitor) {
        Collection<Command> commands = new LinkedList<Command>();
        TracerPreferences pref = TracerPreferences.getInstance();

        String sUrl = "http://josm.poloha.net/tracer/";
        double dAdjX = 0, dAdjY = 0;

        if (pref.m_customRuianUrl)
          sUrl = pref.m_customRuianUrlText;

        if (pref.m_ruianAdjustPosition) {
          dAdjX = pref.m_ruianAdjustPositionLat;
          dAdjY = pref.m_ruianAdjustPositionLon;
        }

        System.out.println("");
        System.out.println("-----------------");
        System.out.println("----- Trace -----");
        System.out.println("-----------------");
        System.out.println("");
        progressMonitor.beginTask(null, 3);
        try {
              record = server.trace(pos, sUrl);

              if (record.getCoorCount() == 0) {
                  TracerUtils.showNotification(tr("Data not available.")+ "\n(" + pos.toDisplayString() + ")", "warning");
                  return;
            }

            // make nodes a way
            Way way = new Way();
            Node firstNode = null;
            // record.getCoorCount() - 1 - ommit last node
            for (int i = 0; i < record.getCoorCount() - 1; i++) {
                Node node;
                // Apply corrections to node coordinates
                if (!pref.m_ruianAdjustPosition) {
                  node = new Node(record.getCoor(i));
                } else {
                  node = new Node(new LatLon(LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lat()+dAdjX),
                                             LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lon()+dAdjY)));
                }
                if (firstNode == null) {
                    firstNode = node;
                }
                commands.add(new AddCommand(node));
                way.addNode(node);
            }
            // Insert first node again - close the polygon.
            way.addNode(firstNode);

            System.out.println("TracedWay: " + way.toString());
            tagBuilding(way);
            // connect to other buildings or modify existing building
            Command connCmd = ConnectWays.connect(way, pos, ctrl, alt, source);

            String s[] = connCmd.getDescriptionText().split(": ");

            if (s[1].equals("Nothing")) {
              TracerUtils.showNotification(tr("Nothing changed."), "info");
              if (shift) {
                Main.main.getCurrentDataSet().addSelected(ConnectWays.getWay());
              } else {
                Main.main.getCurrentDataSet().setSelected(ConnectWays.getWay());
              }
            } else {
                commands.add(connCmd);

                if (!commands.isEmpty()) {
                  String strCommand;
                  if (ConnectWays.s_bAddNewWay == true) {
                    strCommand = trn("Tracer(RUIAN): add a way with {0} point", "Tracer(RUIAN): add a way with {0} points", ConnectWays.getWay().getRealNodesCount(), ConnectWays.getWay().getRealNodesCount()) + "\n" + msg;
                  } else {
                    strCommand = trn("Tracer(RUIAN): modify way to {0} point", "Tracer(RUIAN): modify way to {0} points", ConnectWays.getWay().getRealNodesCount(), ConnectWays.getWay().getRealNodesCount());
                  }
                  Main.main.undoRedo.add(new SequenceCommand(strCommand, commands));

//                   TracerUtils.showNotification(strCommand, "info");

                  if (shift) {
                    Main.main.getCurrentDataSet().addSelected(ConnectWays.getWay());
                  } else {
                    Main.main.getCurrentDataSet().setSelected(ConnectWays.getWay());
                  }
                } else {
                    System.out.println("Failed");
                }
            }
        } finally {
            progressMonitor.finishTask();
        }
    }

// ---------------------------------------------------------------------------
    private void tagBuilding(Way way) {
        msg.setLength(0);
        msg.append(tr("\nFollowing tags added:\n"));
        if(!alt) {
          if ( record.getBuildingTagKey().equals("building") &&
               record.getBuildingTagValue().length() > 0) {
            way.put("building", record.getBuildingTagValue());
            msg.append("building: " + record.getBuildingTagValue() + "\n");
          }
          else {
            way.put("building", "yes");
            msg.append("building: yes\n");
          }
        }

        if (record.getBuildingID() > 0 ) {
          way.put("ref:ruian:building", Long.toString(record.getBuildingID()));
          msg.append("ref:ruian:building: " + Long.toString(record.getBuildingID()) + "\n");
        }

        if (record.getBuildingUsageCode().length() > 0) {
          way.put("building:ruian:type", record.getBuildingUsageCode());
          msg.append("building:ruian:type: " + record.getBuildingUsageCode() + "\n");
        }

        if (record.getBuildingLevels().length() > 0) {
          way.put("building:levels", record.getBuildingLevels());
          msg.append("building:levels: " + record.getBuildingLevels() + "\n");
        }

        if (record.getBuildingFlats().length() > 0) {
          way.put("building:flats", record.getBuildingFlats());
          msg.append("building:flats: " + record.getBuildingFlats() + "\n");
        }

        if (record.getBuildingFinished().length() > 0) {
          way.put("start_date", record.getBuildingFinished());
          msg.append("start_date: " + record.getBuildingFinished() + "\n");
        }

        if (record.getSource().length() > 0) {
          way.put("source", record.getSource());
          msg.append("source: " + record.getSource() + "\n");
        }
        else {
          way.put("source", source);
          msg.append("source: " + source + "\n");
        }
    }
}

