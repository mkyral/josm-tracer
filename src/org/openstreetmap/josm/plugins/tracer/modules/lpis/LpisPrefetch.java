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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.plugins.tracer.QuadCache;
import org.openstreetmap.josm.plugins.tracer.connectways.LatLonSize;

public class LpisPrefetch {

    private static final ExecutorService m_prefetchExecutor = Executors.newSingleThreadExecutor();

    private final LatLonSize m_quadSize;
    private final LpisServer m_lpisServer;
    private final Set<QuadCache.QuadIndex> m_prefetchedTiles = new HashSet<> ();

    private final Object m_lock = new Object ();
    private PrefetchQueue <QuadCache.QuadIndex> m_prefetchQueue = null;  // m_lock, null means prefetching task is not running

    public LpisPrefetch (LatLonSize quad_size, LpisServer server) {
        m_quadSize = quad_size;
        m_lpisServer = server;
    }

    public void schedulePrefetch (LatLon pos) {
        QuadCache.QuadIndex qi = QuadCache.QuadIndex.latLonToQuadIndex(m_quadSize, pos.lat(), pos.lon());
        QuadCache.QuadIndex[] list = new QuadCache.QuadIndex[9];
        int index = 0;
        list[index++] = new QuadCache.QuadIndex (qi.iLat() - 1, qi.iLon() - 1);
        list[index++] = new QuadCache.QuadIndex (qi.iLat() - 1, qi.iLon() + 1);
        list[index++] = new QuadCache.QuadIndex (qi.iLat() + 1, qi.iLon() - 1);
        list[index++] = new QuadCache.QuadIndex (qi.iLat() + 1, qi.iLon() + 1);
        list[index++] = new QuadCache.QuadIndex (qi.iLat(), qi.iLon() - 1);
        list[index++] = new QuadCache.QuadIndex (qi.iLat(), qi.iLon() + 1);
        list[index++] = new QuadCache.QuadIndex (qi.iLat() - 1, qi.iLon());
        list[index++] = new QuadCache.QuadIndex (qi.iLat() + 1, qi.iLon());
        list[index++] = qi;
        schedulePrefetchTiles (list);
    }

    private void schedulePrefetchTiles (QuadCache.QuadIndex[] list) {
        synchronized (m_lock) {

            PrefetchQueue <QuadCache.QuadIndex> new_queue = null;

            for (QuadCache.QuadIndex qi : list) {

                // already prefetched?
                if (m_prefetchedTiles.contains(qi)) {
                    System.out.println ("prefetch: already prefetched: " + qi.toString());
                    continue;
                }

                // prefetch task is running, add to existing prefetch queue
                if (m_prefetchQueue != null) {
                    System.out.println ("prefetch: adding to running queue: " + qi.toString());
                    m_prefetchQueue.add (qi);
                    continue;
                }

                // no prefetch task running, prepare add to new queue
                if (new_queue == null)
                    new_queue = new PrefetchQueue<> ();
                System.out.println ("prefetch: scheduling for new prefetch batch: " + qi.toString());
                new_queue.add (qi);
            }

            // launch prefetch task if there're tiles to prefetch
            if (new_queue != null) {
                m_prefetchQueue = new_queue;
                m_prefetchExecutor.submit(new Runnable () {
                    @Override
                    public void run() {
                        prefetchTask ();
                    }
                });
            }
        }
    }

    private void prefetchTask () {

        QuadCache.QuadIndex qi = null;
        boolean succeeded = false;

        System.out.println ("prefetch: starting prefetch task");

        while (true) {

            synchronized (m_lock) {

                // handle previously downloaded tile
                if (qi != null) {
                    m_prefetchQueue.remove (qi);
                    if (succeeded) {
                        m_prefetchedTiles.add (qi);
                    }
                }

                // get a non-prefetched tile from queue
                while (true) {
                    if (m_prefetchQueue.isEmpty()) {
                        System.out.println ("prefetch: queue drained, leaving prefetch task");
                        m_prefetchQueue = null;
                        return;
                    }
                    QuadCache.QuadIndex aqi = m_prefetchQueue.peek();
                    if (m_prefetchedTiles.contains(aqi)) {
                        System.out.println ("prefetch: queued tile already prefetched: " + aqi.toString());
                        m_prefetchQueue.remove(aqi);
                        continue;
                    }
                    qi = aqi;
                    break;
                }
            }

            succeeded = downloadLpisTile (qi);
        }
    }

    private boolean downloadLpisTile(QuadCache.QuadIndex qi) {
        System.out.println ("prefetch: downloading tile: " + qi.toString());

        try {
            BBox box = QuadCache.QuadIndex.quadIndexToBBox(m_quadSize, qi);
            m_lpisServer.prefetchRecords(box);
        }
        catch (Exception e) {
            return false;
        }

        return true;
    }

    class PrefetchQueue<Element> {
        private final ArrayList<Element> m_Queue = new ArrayList<>(9);

        public boolean isEmpty () {
            return m_Queue.isEmpty();
        }

        public Element peek () {
            return m_Queue.get(m_Queue.size() - 1);
        }

        public boolean remove (Element e) {
            int index = m_Queue.indexOf (e);
            if (index < 0) {
                return false;
            }
            m_Queue.remove (index);
            return true;
        }

        public void add (Element e) {
            int index = m_Queue.indexOf (e);
            if (index < 0) {
                m_Queue.add (e);
            }
            else if (index != m_Queue.size() - 1) {
                m_Queue.remove (index);
                m_Queue.add (e);
            }
        }
    }
}
