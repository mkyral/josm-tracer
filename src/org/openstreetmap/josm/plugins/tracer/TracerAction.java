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
// import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

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
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

class TracerAction extends MapMode implements MouseListener {

    private static final long serialVersionUID = 1L;

    protected boolean cancel;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;
    private String source = "cuzk:km";
    protected TracerServer server = new TracerServer();

    public TracerAction(MapFrame mapFrame) {
        super(tr("Tracer"), "tracer-sml", tr("Tracer."), Shortcut.registerShortcut("tools:tracer", tr("Tool: {0}", tr("Tracer")), KeyEvent.VK_T, Shortcut.DIRECT), mapFrame, getCursor());
    }

//     @Override
//     public void keyPressed(KeyEvent e) {
//       System.out.println("keyPressed: key:" + e.getKeyChar() + " code" + e.getKeyCode() + " Loc" + e.getKeyLocation()+ " ID" + KeyEvent.getKeyText(e.getKeyCode()));
//
//       int kCode = e.getKeyCode();
//       int kMod = e.getModifiersEx();
//       String kModText = e.getKeyModifiersText(kMod);
// //       System.out.println("Modifiers: " + kModtext);
//
//       if (kCode == KeyEvent.VK_T && kModText == "Ctrl+Shift")
//       {
//         t_mode = "ruian";
//       }
//
//       if (kCode == KeyEvent.VK_T && kModText == "")
//       {
//         t_mode = "km";
//       }
//
//       System.out.println("Mode: " + t_mode);
//     }
//
//     @Override
//     public void keyReleased ( KeyEvent e ) {
//       //System.out.println("keyReleased: key:" + e.getKeyChar() + " code" + e.getKeyCode() + " Loc" + e.getKeyLocation()+ " chra" + KeyEvent.getKeyText(e.getKeyCode()));
//     }
//
//     @Override
//     public void keyTyped ( KeyEvent e ) {
//       //System.out.println("keyTyped: key:" + e.getKeyChar() + " code" + e.getKeyCode() + " Loc" + e.getKeyLocation()+ " ID" + KeyEvent.getKeyText(e.getKeyCode()));
//     }

    @Override
    public void enterMode() {
        if (!isEnabled()) {
            return;
        }
        super.enterMode();
        Main.map.mapView.setCursor(getCursor());
        Main.map.mapView.addMouseListener(this);
//         Main.map.mapView.addKeyListener(this);

    }

    @Override
    public void exitMode() {
        super.exitMode();
        Main.map.mapView.removeMouseListener(this);
//         Main.map.mapView.removeKeyListener(this);
    }

    private static Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-sml");
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
                    TracerAction.this.cancel();
                }
            };
            Thread executeTraceThread = new Thread(tracerTask);
            executeTraceThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tagBuilding(Way way) {
        if(!alt) way.put("building", "yes");
        way.put("source", source);
    }

    private void traceSync(LatLon pos, ProgressMonitor progressMonitor) {
        Collection<Command> commands = new LinkedList<Command>();
        TracerPreferences pref = TracerPreferences.getInstance();

        String sUrl = "http://localhost:5050/";
        double dAdjX = 0, dAdjY = 0;

        if (pref.m_customTracerUrl)
          sUrl = pref.m_customTracerUrlText;

        if (pref.m_tracerAdjustPosition) {
          dAdjX = pref.m_tracerAdjustPositionLat;
          dAdjY = pref.m_tracerAdjustPositionLon;
        }

        progressMonitor.beginTask(null, 3);
        try {
              ArrayList<LatLon> coordList = server.trace(pos, sUrl, dAdjX, dAdjY);

            if (coordList.size() == 0) {
                return;
            }

            // make nodes a way
            Way way = new Way();
            Node firstNode = null;
            for (LatLon coord : coordList) {
                Node node = new Node(coord);
                if (firstNode == null) {
                    firstNode = node;
                }
                commands.add(new AddCommand(node));
                way.addNode(node);
            }
            way.addNode(firstNode);

            tagBuilding(way);

            // connect to other buildings or modify existing building
            commands.add(ConnectWays.connect(way, pos, ctrl, alt, source));

            if (!commands.isEmpty()) {
              String strCommand;
              if (ConnectWays.s_bAddNewWay == true) {
                strCommand = trn("Tracer: add a way with {0} point", "Tracer: add a way with {0} points", coordList.size(), coordList.size());
              } else {
                strCommand = trn("Tracer: modify way to {0} point", "Tracer: modify way to {0} points", coordList.size(), coordList.size());
              }
              Main.main.undoRedo.add(new SequenceCommand(strCommand, commands));

              TracerUtils.showNotification(strCommand, "info");

              if (shift) {
                Main.main.getCurrentDataSet().addSelected(ConnectWays.s_oWay);
              } else {
                Main.main.getCurrentDataSet().setSelected(ConnectWays.s_oWay);
              }
            } else {
                System.out.println("Failed");
            }

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

