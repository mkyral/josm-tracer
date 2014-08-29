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

import java.util.*;

/**
 * Private class to store modules
 *
 */

public class Modules {
    private static Map<String, TracerModule> m_modules; // map of all modules
    private static Map.Entry<String, TracerModule> m_active_module; // active module

    private static Iterator<Map.Entry<String, TracerModule>>  m_it; // holds current iterator

    private int m_activeModulesCount = 0;

    TracerPreferences pref = TracerPreferences.getInstance();

    private void init () {
      m_modules = new LinkedHashMap<String, TracerModule>();
      m_modules.put("classic", new ClassicModule(false));
      m_modules.put("ruian", new RuianModule(true));
      m_modules.put("ruian-lands", new RuianLandsModule(false));
      m_modules.put("lpis", new LpisModule(false));

      List<String> m_modules_array = new LinkedList<String>();
      Map.Entry<String, TracerModule> m_current_module;

      // Refresh modules list in configuration
      m_it = m_modules.entrySet().iterator();
      while (m_it.hasNext()) {
        m_current_module = m_it.next();
        m_modules_array.add(m_current_module.getKey());
      }
      pref.setAvailableModules(m_modules_array);

      // Set enabled modules
      for (String s: pref.getActiveModules()) {
        m_modules.get(s).setModuleIsEnabled(true);
      }

      // Reorder modules
      // First add enabled modules
      Map<String, TracerModule> m_bckModules = new LinkedHashMap<String, TracerModule>(m_modules);
      m_modules.clear();
      for (String s: pref.getActiveModules()) {
        m_modules.put(s, m_bckModules.get(s));
      }

      // Second: add remaining modules
      Iterator<Map.Entry<String, TracerModule>> it = m_bckModules.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, TracerModule> module = it.next();
        if (!m_modules.containsValue(module.getValue())) {
          m_modules.put(module.getKey(), module.getValue());
        }
      }

      countActiveModules();

      if (m_activeModulesCount == 0) {
        return;
      }

      m_it = m_modules.entrySet().iterator();
      m_active_module = m_it.next();
      while (!m_active_module.getValue().moduleIsEnabled()) {
        m_active_module = m_it.next();
      }

    }

    private void countActiveModules() {

      m_activeModulesCount = 0;

      Iterator<Map.Entry<String, TracerModule>> it = m_modules.entrySet().iterator();
      while (it.hasNext()) {
        if (it.next().getValue().moduleIsEnabled()) {
          m_activeModulesCount++;
        }
      }
    }

// -----------------------------------------------------------------

    public Modules () {
      init();
    }

    public void refreshModulesStatus() {

      pref.reloadSettings();

      // Reorder modules
      // First add enabled modules
      Map<String, TracerModule> m_bckModules = new LinkedHashMap<String, TracerModule>(m_modules);
      m_modules.clear();
      for (String s: pref.getActiveModules()) {
        m_modules.put(s, m_bckModules.get(s));
        m_modules.get(s).setModuleIsEnabled(true);
      }

      // Second: add remaining modules
      Iterator<Map.Entry<String, TracerModule>> it = m_bckModules.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, TracerModule> module = it.next();
        if (!m_modules.containsValue(module.getValue())) {
          m_modules.put(module.getKey(), module.getValue());
          m_modules.get(module.getKey()).setModuleIsEnabled(false);
        }
      }

      countActiveModules();

      if (m_activeModulesCount == 0) {
        return;
      }

      String m_bckKey= new String(m_active_module.getKey());
      m_it = m_modules.entrySet().iterator();
      m_active_module = m_it.next();
      if (m_modules.containsKey(m_bckKey) && pref.isModuleEnabled(m_bckKey)) {
        while (!m_active_module.getKey().equals(m_bckKey)) {
          m_active_module = m_it.next();
        }
      }

    }

    public String getActiveModuleName() {
      if (m_active_module == null) {
        return null;
      }
      return m_active_module.getValue().getName();
    }

    public TracerModule getActiveModule() {
      if (m_active_module == null) {
        return null;
      }
      return m_active_module.getValue();
    }

    public TracerModule nextModule() {
      while (true) {
        if (m_it.hasNext()) {
          m_active_module = m_it.next();
          if (m_active_module.getValue().moduleIsEnabled())
            break;
        } else {
          m_it = m_modules.entrySet().iterator();
          m_active_module = m_it.next();
          if (m_active_module.getValue().moduleIsEnabled())
            break;
        }
      }
      return m_active_module.getValue();
    }

    public void setActiveModule(String m) {
      m_it = m_modules.entrySet().iterator();
      m_active_module = null;

      Map.Entry<String, TracerModule> ent;

      while (m_it.hasNext()) {
        ent = m_it.next();
        if (ent.getKey() == m) {
          m_active_module = ent;
          break;
        }
      }
    }

    public int getActiveModulesCount() {
      return m_activeModulesCount;
    }
}