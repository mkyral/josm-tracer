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
package org.openstreetmap.josm.plugins.tracer.modules.classic;

import java.awt.Cursor;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.ConnectWays;
import org.openstreetmap.josm.plugins.tracer.connectways.EdObject;
import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;
import org.xml.sax.SAXException;


public class ClassicModule extends TracerModule {

    protected boolean cancel;
    private boolean moduleEnabled;
    private final ConnectWays connectWays = new ConnectWays();
    protected ClassicServer server = new ClassicServer();

    private final String source = "cuzk:km";

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
    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift)
    {
        return new PleaseWaitRunnable(tr("Tracing")) {
            @Override
            protected void realRun() throws SAXException {
                ClassicModule.this.traceImpl(pos, ctrl, alt, shift,
                    progressMonitor.createSubTaskMonitor(ProgressMonitor.ALL_TICKS, false));
            }

            @Override
            protected void finish() {
            }

            @Override
            protected void cancel() {
                // #### FIXME - resolve cancellation
            }
        };
    }

    private void tagTracedObject (TracerRecord record, Way obj) {

        Map <String, String> map = obj.getKeys();
        Map <String, String> new_keys = new HashMap <> (record.getKeys());

        for (Map.Entry<String, String> new_key: new_keys.entrySet()) {
            map.put(new_key.getKey(), new_key.getValue());
        }
        obj.setKeys(map);
    }

    private void traceImpl(LatLon pos, boolean ctrl, boolean alt, boolean shift, ProgressMonitor progressMonitor) {
        final Collection<Command> commands = new LinkedList<>();
        TracerPreferences pref = TracerPreferences.getInstance();

        String sUrl = "http://localhost:5050/";
        double adjlat = 0, adjlon = 0;

        if (pref.isCustomTracerUrlEnabled())
          sUrl = pref.getCustomTracerUrl();

        if (pref.isTracerAdjustPositionEnabled()) {
          adjlat = pref.getTracerAdjustPositionLat();
          adjlon = pref.getTracerAdjustPositionLon();
        }

        progressMonitor.beginTask(null, 3);
        try {
            ClassicRecord record = server.trace(pos, sUrl, adjlat, adjlon);

            if (!record.hasData()) {
                return;
            }

            // make nodes a way
            List<Bounds> dsBounds = Main.main.getCurrentDataSet().getDataSourceBounds();
            Way way = new Way();
            Node firstNode = null;
            List<LatLon> outer = record.getOuter();
            for (int i = 0; i < outer.size() - 1; i++) {
                LatLon coord = outer.get(i);
                Node node = new Node(coord);
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
            way.addNode(firstNode);

            tagTracedObject(record, way);

            // connect to other buildings or modify existing building
            commands.add(connectWays.connect(way, pos, ctrl, alt, source));

            if (!commands.isEmpty()) {
              final String strCommand;
              if (connectWays.isNewWay()) {
                strCommand = trn("Tracer: add a way with {0} point", "Tracer: add a way with {0} points", outer.size(), outer.size());
              } else {
                strCommand = trn("Tracer: modify way to {0} point", "Tracer: modify way to {0} points", outer.size(), outer.size());
              }
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  Main.main.undoRedo.add(new SequenceCommand(strCommand, commands));
                }
              } );


              TracerUtils.showNotification(strCommand, "info");

              if (shift) {
                Main.main.getCurrentDataSet().addSelected(connectWays.getWay());
              } else {
                Main.main.getCurrentDataSet().setSelected(connectWays.getWay());
              }
            } else {
                System.out.println("Failed");
            }

        }
        catch (IOException e) {
            return;
        }
        finally {
            progressMonitor.finishTask();
        }
    }
}


