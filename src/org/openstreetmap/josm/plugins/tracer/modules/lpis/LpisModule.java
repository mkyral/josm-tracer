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

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import static org.openstreetmap.josm.tools.I18n.*;
import java.awt.Cursor;
import java.awt.Point;
import java.util.*;
import java.lang.StringBuilder;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;

import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerModule;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.openstreetmap.josm.plugins.tracer.connectways.*;

// import org.openstreetmap.josm.plugins.tracer.modules.lpis.LpisRecord;

import org.xml.sax.SAXException;

public class LpisModule implements TracerModule  {

    private boolean moduleEnabled;

    private static final String source = "lpis";
    private static final String lpisUrl = "http://eagri.cz/public/app/wms/plpis_wfs.fcgi";
    private static final String reuseExistingLanduseNodePattern =
        "((landuse=* -landuse=no) | natural=scrub | natural=wood | natural=grassland | natural=wood | leisure=garden)";

    private static volatile Match m_reuseExistingLanduseNodeMatch;

    static {
        try {
            m_reuseExistingLanduseNodeMatch = SearchCompiler.compile(reuseExistingLanduseNodePattern, false, false);
        } 
        catch (ParseError e) {
            throw new AssertionError(tr("Unable to compile pattern"));            
        }
    }

    public LpisModule(boolean enabled) {
        moduleEnabled = enabled;
    }

    public void init() {
    }

    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-lpis-sml");
    }

    public String getName() {
        return tr("LPIS");
    }

    public boolean moduleIsEnabled() {
        return moduleEnabled;
    }

    public void setModuleIsEnabled(boolean enabled) {
        moduleEnabled = enabled;
    }

    public PleaseWaitRunnable trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new LpisTracerTask (pos, ctrl, alt, shift);
    }

    class LpisTracerTask extends PleaseWaitRunnable {

        private final LatLon m_pos;
        private final boolean m_ctrl;
        private final boolean m_alt;
        private final boolean m_shift;

        private LpisRecord m_record;
        private Exception m_asyncException;
        private boolean m_cancelled;

        LpisTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (tr("Tracing"));
            this.m_pos = pos;
            this.m_ctrl = ctrl;
            this.m_alt = alt;
            this.m_shift = shift;
            this.m_record = null;
            this.m_asyncException = null;
            this.m_cancelled = false;
        }

        protected void realRun() throws SAXException {

            System.out.println("");
            System.out.println("-----------------");
            System.out.println("----- Trace -----");
            System.out.println("-----------------");
            System.out.println("");

            progressMonitor.indeterminateSubTask(tr("Downloading LPIS data..."));

            try {
                LpisServer server = new LpisServer();
                m_record = server.getElementBasicData(m_pos, lpisUrl);
                progressMonitor.subTask(tr("Creating LPIS polygon..."));
            }
            catch (Exception e) {
                m_asyncException = e;
            }
        }

        protected void finish() {

            // Note: finish() is guaranteed to run on EDT, after realRun() suceeded.

            // Async download failed?
            if (m_asyncException != null) {
                m_asyncException.printStackTrace();
                TracerUtils.showNotification(tr("LPIS download failed.") + "\n(" + m_pos.toDisplayString() + ")", "error");
                return;
            }

            // Cancelled by user?
            if (m_cancelled) {
                return;
            }

            // No data available?
            if (m_record.getLpisID() == -1) {
                TracerUtils.showNotification(tr("Data not available.")+ "\n(" + m_pos.toDisplayString() + ")", "warning");
                return;
            }

            Main.main.getCurrentDataSet().beginUpdate();
            try {
                createTracedPolygon ();
            }
            catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
            finally {
                Main.main.getCurrentDataSet().endUpdate();
            }
        }

        private void wayIsOutsideDownloadedAreaDialog() {
            ExtendedDialog ed = new ExtendedDialog(
                Main.parent, tr("Way is outside downloaded area"),
                    new String[] {tr("Ok")});
            ed.setButtonIcons(new String[] {"ok"});
            ed.setIcon(JOptionPane.ERROR_MESSAGE);
            ed.setContent(tr("Sorry.\nThe traced way (or part of the way) is outside of the downloaded area.\nPlease download area around the way and try again."));
            ed.showDialog();
        }

        private void createTracedPolygon() {

            // Note: must be called from finish() only

            System.out.println("  LPIS ID: " + m_record.getLpisID());
            System.out.println("  LPIS usage: " + m_record.getUsage());

            WayEditor editor = new WayEditor (Main.main.getCurrentDataSet());

            // Create outer way
            List<EdNode> outer_nodes = new ArrayList<EdNode> ();
            // m_record.getCoorCount() - 1 - omit last node
            for (int i = 0; i < m_record.getOuter().size() - 1; i++) {
                EdNode node = editor.newNode(m_record.getOuter().get(i));

                if (!editor.insideDataSourceBounds(node)) {
                    wayIsOutsideDownloadedAreaDialog();
                    return;
                }
                outer_nodes.add(node);
            }

            // close way
            if (outer_nodes.size() > 0)
                outer_nodes.add(outer_nodes.get(0));
            EdWay outer_way = editor.newWay(outer_nodes);

            IEdNodePredicate reuse_filter = new AreaBoundaryWayNodePredicate(m_reuseExistingLanduseNodeMatch);
            outer_way.reuseExistingNodes(reuse_filter);

            // #### If outer way is identical to an existing way, and this way is an untagged inner way of a
            // landuse-matching multipolygon, and it isn't a member of other relations, then drop new outer_way
            // and use the existing one.

            if (!m_record.hasInners())
                tagOuterWay(outer_way);

            // Create multipolygon?
            EdMultipolygon multipolygon = null;
            if (m_record.hasInners()) {
                multipolygon = editor.newMultipolygon();
                tagMultipolygon(multipolygon);
                multipolygon.addOuterWay(outer_way);

                for (int i = 0; i < m_record.getInnersCount(); i++) {
                    ArrayList<LatLon> in = m_record.getInner(i);
                    List<EdNode> inner_nodes = new ArrayList<EdNode>(in.size());
                    for (int j = 0; j < in.size() - 1; j++) {
                        inner_nodes.add(editor.newNode(in.get(j)));
                    }

                    // close way
                    if (inner_nodes.size() > 0)
                        inner_nodes.add(inner_nodes.get(0));
                    EdWay way = editor.newWay(inner_nodes);

                    way.reuseExistingNodes(reuse_filter);

                    // #### If inner way is identical to an existing way, and this way is a landuse-matching
                    // (outer) way, then drop new inner_way and use the existing one.

                    multipolygon.addInnerWay(way);
                }
            }

            List<Command> commands = editor.finalizeEdit();

            if (!commands.isEmpty()) {
                Main.main.undoRedo.add(new SequenceCommand(tr("Trace object"), commands));

                OsmPrimitive sel = (multipolygon != null ?
                    multipolygon.finalMultipolygon() : outer_way.finalWay());

                if (m_shift) {
                    editor.getDataSet().addSelected(sel);
                } else {
                    editor.getDataSet().setSelected(sel);
                }
            } else {
                System.out.println("Failed");
            }
        }

        protected void cancel() {
            m_cancelled = true;
            // #### TODO: break the connection to remote LPIS server
        }

        private void tagMultipolygon (EdMultipolygon multipolygon) {
            Map <String, String> map = new HashMap <String, String> (m_record.getUsageOsm());
            map.put("source", source);
            map.put("ref", Long.toString(m_record.getLpisID()));
            multipolygon.setKeys(map);
        }

        private void tagOuterWay (EdWay way) {
            Map <String, String> map = new HashMap <String, String> (m_record.getUsageOsm());
            map.put("source", source);
            map.put("ref", Long.toString(m_record.getLpisID()));
            way.setKeys(map);
        }
    }
}

