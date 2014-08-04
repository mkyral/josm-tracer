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
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.lang.StringBuilder;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;

import org.openstreetmap.josm.plugins.tracer.LpisRecord;


import org.xml.sax.SAXException;

class TracerActionLpis extends MapMode implements MouseListener {

    private static final long serialVersionUID = 1L;

    protected boolean cancel;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;
    private String source = "lpis";
    private static LpisRecord record;

    private static StringBuilder msg = new StringBuilder();

    protected TracerServerLpis server = new TracerServerLpis();

    public TracerActionLpis(MapFrame mapFrame) {
        super(tr("Tracer - LPIS"), "tracer-lpis-sml", tr("Get agricultural geometry and some properties from LPIS."), Shortcut.registerShortcut("tools:tracerLPIS", tr("Tool: {0}", tr("Tracer - LPIS")), KeyEvent.VK_T, Shortcut.ALT_CTRL_SHIFT), mapFrame, getCursor());
    }

    @Override
    public void enterMode() {
        if (!isEnabled()) {
            return;
        }
        super.enterMode();
        Main.map.mapView.setCursor(getCursor());
        Main.map.mapView.addMouseListener(this);

    }

    @Override
    public void exitMode() {
        super.exitMode();
        Main.map.mapView.removeMouseListener(this);
    }

    private static Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-lpis-sml");
    }

    protected void traceAsync(Point clickPoint) {
        cancel = false;
        /**
         * Positional data
         */
        final LatLon pos = Main.map.mapView.getLatLon(clickPoint.x, clickPoint.y);

        try {
            PleaseWaitRunnable tracerTask = new PleaseWaitRunnable(tr("Tracing")) {

                @Override
                protected void realRun() throws SAXException {
                    traceSync(pos, progressMonitor.createSubTaskMonitor(ProgressMonitor.ALL_TICKS, false));
                }

                @Override
                protected void finish() {
                }

                @Override
                protected void cancel() {
                    TracerActionLpis.this.cancel();
                }
            };
            Thread executeTraceThread = new Thread(tracerTask);
            executeTraceThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
/*
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
    }*/

    private void tagMultipolygon (Relation rel) {
      Map <String, String> map = new HashMap <String, String> (record.getUsageOsm());
      map.put("type", "multipolygon");
      map.put("source", "lpis");
      map.put("ref", Long.toString(record.getLpisID()));
      rel.setKeys(map);
    }

    private void tagOuterWay (Way way) {
      Map <String, String> map = new HashMap <String, String> (record.getUsageOsm());
      map.put("source", "lpis");
      map.put("ref", Long.toString(record.getLpisID()));
      way.setKeys(map);
    }

    private void traceSync(LatLon pos, ProgressMonitor progressMonitor) {
        Collection<Command> commands = new LinkedList<Command>();
        TracerPreferences pref = TracerPreferences.getInstance();

        String sUrl = "http://eagri.cz/public/app/wms/plpis_wfs.fcgi";
        double dAdjX = 0, dAdjY = 0;

//         if (pref.m_customRuianUrl)
//           sUrl = pref.m_customRuianUrlText;
//
//         if (pref.m_ruianAdjustPosition) {
//           dAdjX = pref.m_ruianAdjustPositionLat;
//           dAdjY = pref.m_ruianAdjustPositionLon;
//         }

        System.out.println("");
        System.out.println("-----------------");
        System.out.println("----- Trace -----");
        System.out.println("-----------------");
        System.out.println("");
        progressMonitor.beginTask(null, 3);
        try {
              record = server.getElementBasicData(pos, sUrl);
              System.out.println("  LPIS ID: " + record.getLpisID());
              System.out.println("  LPIS usage: " + record.getUsage());
              if (record.getLpisID() == -1) {
                  TracerUtils.showNotification(tr("Data not available.")+ "\n(" + pos.toDisplayString() + ")", "warning");
                  return;
            }

            Way outer = new Way();
            Relation rel = new Relation();

            // Create Outer way
            Node firstNode = null;
            // record.getCoorCount() - 1 - ommit last node
            for (int i = 0; i < record.getOuter().size() - 1; i++) {
                Node node;
//                 // Apply corrections to node coordinates
//                 if (!pref.m_ruianAdjustPosition) {
                  node = new Node(record.getOuter().get(i));
//                 } else {
//                   node = new Node(new LatLon(LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lat()+dAdjX),
//                                              LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lon()+dAdjY)));
//                 }
                if (firstNode == null) {
                    firstNode = node;
                }
                commands.add(new AddCommand(node));
                outer.addNode(node);
            }
            // Insert first node again - close the polygon.
            outer.addNode(firstNode);

            System.out.println("TracedWay: " + outer.toString());
            if (! record.hasInners()) {
              tagOuterWay(outer);
            }

            commands.add(new AddCommand(outer));

            if (record.hasInners()) {
              // Inners found - create multipolygon

              rel.addMember(new RelationMember("outer", outer));

              for (int i = 0; i < record.getInnersCount(); i++) {
                ArrayList<LatLon> in = record.getInner(i);
                Way inner = new Way();
                firstNode = null;
                for (int j = 0; j < in.size() -1 ; j++) {
                  Node node;
  //                 // Apply corrections to node coordinates
  //                 if (!pref.m_ruianAdjustPosition) {
                    node = new Node(in.get(j));
  //                 } else {
  //                   node = new Node(new LatLon(LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lat()+dAdjX),
  //                                              LatLon.roundToOsmPrecisionStrict(record.getCoor(i).lon()+dAdjY)));
  //                 }
                  if (firstNode == null) {
                      firstNode = node;
                  }
                  commands.add(new AddCommand(node));
                  inner.addNode(node);
                }
                // Insert first node again - close the polygon.
                inner.addNode(firstNode);

                System.out.println("Traced Inner Way: " + inner.toString());

                commands.add(new AddCommand(inner));
                rel.addMember(new RelationMember("inner", inner));

                }
                // Add relation
                tagMultipolygon(rel);
                commands.add(new AddCommand(rel));
              }

            Main.main.undoRedo.add(new SequenceCommand(tr("Trace object"), commands));
            String msg = tr("Tracer(LPIS): add an area") + " \"" + record.getUsage() + "\"";
            if (record.hasInners()) {
              msg = msg + trn(" with {0} inner.", " with {0} inners.", record.getInnersCount(), record.getInnersCount());
            } else {
              msg = msg + ".";
            }

            TracerUtils.showNotification(msg, "info");

            if (shift) {
              Main.main.getCurrentDataSet().addSelected(record.hasInners()?rel:outer);
            } else {
              Main.main.getCurrentDataSet().setSelected(record.hasInners()?rel:outer);
            }


//             // connect to other buildings or modify existing building
//             Command connCmd = ConnectWays.connect(way, pos, ctrl, alt, source);
//
//             String s[] = connCmd.getDescriptionText().split(": ");
//
//             if (s[1].equals("Nothing")) {
//               TracerUtils.showNotification(tr("Nothing changed."), "info");
//               if (shift) {
//                 Main.main.getCurrentDataSet().addSelected(ConnectWays.s_oWay);
//               } else {
//                 Main.main.getCurrentDataSet().setSelected(ConnectWays.s_oWay);
//               }
//             } else {
//                 commands.add(connCmd);
//
//                 if (!commands.isEmpty()) {
//                   if (shift) {
//                     Main.main.getCurrentDataSet().addSelected(ConnectWays.s_oWay);
//                   } else {
//                     Main.main.getCurrentDataSet().setSelected(ConnectWays.s_oWay);
//                   }
//                 } else {
//                     System.out.println("Failed");
//                 }
//             }
        } finally {
            progressMonitor.finishTask();
        }
    }

    public void cancel() {
        cancel = true;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!Main.map.mapView.isActiveLayerDrawable()) {
            return;
        }
        requestFocusInMapView();
        updateKeyModifiers(e);
        if (e.getButton() == MouseEvent.BUTTON1) {
            traceAsync(e.getPoint());
        }
    }

    @Override
    protected void updateKeyModifiers(MouseEvent e) {
        ctrl = (e.getModifiers() & ActionEvent.CTRL_MASK) != 0;
        alt = (e.getModifiers() & (ActionEvent.ALT_MASK | InputEvent.ALT_GRAPH_MASK)) != 0;
        shift = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }
}

