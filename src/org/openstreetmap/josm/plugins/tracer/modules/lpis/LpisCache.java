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

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.QuadCache;
import org.openstreetmap.josm.plugins.tracer.connectways.LatLonSize;

public class LpisCache {
    private final Object m_lock;
    private final Map<Long, LpisRecord> m_records;
    private final QuadCache<LpisRecord> m_cache;

    public LpisCache (LatLonSize llsize) {
        m_lock = new Object ();
        m_records = new HashMap<> ();
        m_cache = new QuadCache<> (llsize);
    }

    public boolean add (LpisRecord record) {

        synchronized (m_lock) {
            // already in cache?
            if (m_records.containsKey(record.getLpisID()))
                return false;

            m_records.put(record.getLpisID(), record);
            m_cache.add(record);
            return true;
        }
    }

    public LpisRecord get (LatLon latlon) {
        synchronized (m_lock) {
            List<LpisRecord> list = m_cache.search(latlon);
            if (list == null) {
                System.out.println ("LpisCache: miss");
                return null;
            }
            if (list.size() == 1) {
                LpisRecord record = list.get(0);
                System.out.println ("LpisCache: hit, id=" + Long.toString(record.getLpisID()));
                return record;
            }
            if (list.size() > 1)
                System.out.println("LpisCache: OVERLAPPING OBJECTS IN CACHE!");
            return null;
        }
    }
}
