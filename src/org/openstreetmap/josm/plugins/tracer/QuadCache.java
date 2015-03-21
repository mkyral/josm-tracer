/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.openstreetmap.josm.plugins.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.plugins.tracer.connectways.LatLonSize;
import org.openstreetmap.josm.tools.Pair;

public class QuadCache<T extends IQuadCacheObject> {

    private final LatLonSize m_quadSize;
    private final Map<QuadIndex, Bucket<T>> m_buckets;

    public QuadCache (LatLonSize quad_size) {
        m_quadSize = quad_size;
        m_buckets = new HashMap<> ();
    }

    public void add (T object) {
        BBox bbox = object.getBBox();
        Pair<QuadIndex, QuadIndex> qibox = bboxToQuadIndexBox (bbox);
        QuadIndex qi1 = qibox.a;
        QuadIndex qi2 = qibox.b;

        // #### fixme: doesn't handle longitude corner cases for objects crossing Pacific Ocean meridian!!
        // maybe restrict usage of this class to Czech republic latlons only

        for (long ilat = qi1.iLat(); ilat <= qi2.iLat(); ilat++) {
            for (long ilon = qi1.iLon(); ilon <= qi2.iLon(); ilon++) {
                QuadIndex qi = new QuadIndex (ilat, ilon);
                Bucket<T> bucket = m_buckets.get(qi);
                if (bucket == null) {
                    bucket = new Bucket<>();
                    m_buckets.put(qi, bucket);
                }
                System.out.println ("QuadCache: adding to bucket: " + qi.toString());
                bucket.add (object);
            }
        }
    }

    public List<T> search (LatLon latlon) {
        QuadIndex qi = latLonToQuadIndex(latlon);

        Bucket<T> bucket = m_buckets.get (qi);
        if (bucket == null)
            return null;
        return bucket.search (latlon, qi);
    }

    public static final class QuadIndex {
        private final long m_ilat;
        private final long m_ilon;
        private final int m_hashCode;

        public QuadIndex (long ilat, long ilon) {
            m_ilat = ilat;
            m_ilon = ilon;
            m_hashCode = Long.valueOf(m_ilat).hashCode() ^ Long.valueOf(m_ilon).hashCode();
        }

        public long iLat () {
            return m_ilat;
        }

        public long iLon () {
            return m_ilon;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final QuadIndex other = (QuadIndex) obj;
            return other.m_ilat == m_ilat && other.m_ilon == m_ilon;
        }

        @Override
        public int hashCode() {
            return m_hashCode;
        }

        @Override
        public String toString () {
            return Long.toString(m_ilat) + "+" + Long.toString (m_ilon);
        }
    }

    private class Bucket<T extends IQuadCacheObject> {
        private final Set<T> m_contents;

        Bucket () {
            m_contents = new HashSet<>();
        }

        private void add(T object) {
            m_contents.add(object);
        }

        private List<T> search(LatLon ll, QuadIndex qi) {
            System.out.println ("QuadCache: searching in bucket: " + qi.toString() + ", total: " + Integer.toString (m_contents.size()));
            List<T> list = null;
            for (T object: m_contents) {
                if (object.containsPoint (ll)) {
                    if (list == null) {
                        list = new ArrayList<> (5);
                    }
                    list.add(object);
                }
            }
            return list;
        }
    }

    public QuadIndex latLonToQuadIndex (LatLon latlon) {
        return latLonToQuadIndex (latlon.lat(), latlon.lon());
    }

    public QuadIndex latLonToQuadIndex (double alat, double alon) {
        double lat = alat + 90;
        double lon = alon + 180;
        long ilat = (long) Math.floor (lat / m_quadSize.latSize());
        long ilon = (long) Math.floor (lon / m_quadSize.lonSize());
        return new QuadIndex (ilat, ilon);
    }

    public Pair<QuadIndex, QuadIndex> bboxToQuadIndexBox(BBox bbox) {
        double lat1 = bbox.getTopLeftLat();
        double lon1 = bbox.getTopLeftLon();
        double lat2 = bbox.getBottomRightLat();
        double lon2 = bbox.getBottomRightLon();
        QuadIndex qi1 = latLonToQuadIndex (lat1, lon1);
        QuadIndex qi2 = latLonToQuadIndex (lat1, lon2);
        QuadIndex qi3 = latLonToQuadIndex (lat2, lon1);
        QuadIndex qi4 = latLonToQuadIndex (lat2, lon2);
        long ilat1 = Math.min(Math.min (qi1.iLat(), qi2.iLat()), Math.min (qi3.iLat(), qi4.iLat()));
        long ilon1 = Math.min(Math.min (qi1.iLon(), qi2.iLon()), Math.min (qi3.iLon(), qi4.iLon()));
        long ilat2 = Math.max(Math.max (qi1.iLat(), qi2.iLat()), Math.max (qi3.iLat(), qi4.iLat()));
        long ilon2 = Math.max(Math.max (qi1.iLon(), qi2.iLon()), Math.max (qi3.iLon(), qi4.iLon()));
        return new Pair<> (new QuadIndex (ilat1, ilon1), new QuadIndex (ilat2, ilon2));
    }
}
