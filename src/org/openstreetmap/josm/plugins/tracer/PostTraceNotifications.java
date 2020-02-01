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

package org.openstreetmap.josm.plugins.tracer;

import java.util.ArrayList;
import java.util.List;

public class PostTraceNotifications {
    private final List<String> m_list = new ArrayList<> ();

    public void clear() {
        synchronized(m_list) {
            m_list.clear();
        }
    }

    public void add(String s) {
        System.out.println("Notify: " + s);
        synchronized(m_list) {
            m_list.add(s);
        }
    }

    public void show () {
        StringBuilder sb = new StringBuilder();
        synchronized(m_list) {
            if (m_list.isEmpty())
                return;
            for (String s: m_list) {
                if (sb.length() != 0)
                    sb.append("\n");
                sb.append(s);
            }
            m_list.clear();
        }
        TracerUtils.showNotification(sb.toString(), "warning");
    }
}
