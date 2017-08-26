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
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.KeyListener;

// import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Shortcut;

class TracerAction extends MapMode implements MouseListener, KeyListener {

    private static final long serialVersionUID = 1L;
    private final Modules m_modules = new Modules();
    private enum key_mode {KEY_PRESSED, KEY_RELEASED};

    public TracerAction() {
        super(tr("Tracer"), "tracer-sml", tr("Tracer."),
                Shortcut.registerShortcut("tools:tracer", tr("Tool: {0}", tr("Tracer")), KeyEvent.VK_T, Shortcut.DIRECT), null);
    }

    @Override
    public void enterMode() {
        if (!isEnabled()) {
            return;
        }

        m_modules.refreshModulesStatus();
        if (m_modules.getActiveModulesCount() == 0) {
            TracerUtils.showNotification(tr("Tracer: No active module found!\nPlease enable some in configuration."), "error");
            return;
        }

        super.enterMode();
        MainApplication.getMap().mapView.addMouseListener(this);
        MainApplication.getMap().mapView.addKeyListener(this);
        MainApplication.getMap().mapView.requestFocus();
        MainApplication.getMap().mapView.setCursor(m_modules.getActiveModule().getCursor());
        TracerUtils.showNotification(tr("Tracer: Module {0} activated.", m_modules.getActiveModuleName()), "info", 700);
    }

    @Override
    public void exitMode() {
        super.exitMode();
        MainApplication.getMap().mapView.removeMouseListener(this);
        MainApplication.getMap().mapView.removeKeyListener(this);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        checkKey(e, key_mode.KEY_RELEASED);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        checkKey(e, key_mode.KEY_PRESSED);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        checkKey(e, key_mode.KEY_PRESSED);
    }

    private void checkKey(KeyEvent e, key_mode mode) {
        String previousModule = m_modules.getActiveModuleName();

        switch (e.getKeyCode()) {
            case KeyEvent.VK_T: // T
                if (mode == key_mode.KEY_PRESSED){
                    m_modules.nextModule();
                    if (!previousModule.equals(m_modules.getActiveModuleName())) {
                        TracerUtils.showNotification(tr("Tracer: Switched to {0} module.", m_modules.getActiveModuleName()), "info", 700);
                        MainApplication.getMap().mapView.setCursor(m_modules.getActiveModule().getCursor());
                    }
                }
                break;
            case KeyEvent.VK_ALT: // alt
            case KeyEvent.VK_CONTROL: // control
            case KeyEvent.VK_SHIFT: // shift
                updateKeyModifiers(e);
                MainApplication.getMap().mapView.setCursor(m_modules.getActiveModule().getCursor(ctrl, alt, shift));
                break;
            default:
                MainApplication.getMap().mapView.setCursor(m_modules.getActiveModule().getCursor());
        }
    }

    protected void traceAsync(Point clickPoint) {
        final LatLon pos = MainApplication.getMap().mapView.getLatLon(clickPoint.x, clickPoint.y);
        TracerModule.AbstractTracerTask tracer_task = m_modules.getActiveModule().trace(pos, ctrl, alt, shift);
        tracer_task.run();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
        MainApplication.getMap().mapView.setCursor(m_modules.getActiveModule().getCursor());
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!MainApplication.getMap().mapView.isActiveLayerDrawable()) {
            return;
        }
        requestFocusInMapView();
        updateKeyModifiers(e);
        if (e.getButton() == MouseEvent.BUTTON1) {
            traceAsync(e.getPoint());
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        MainApplication.getMap().mapView.setCursor(m_modules.getActiveModule().getCursor());
    }
}
