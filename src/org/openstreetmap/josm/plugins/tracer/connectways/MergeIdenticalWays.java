package org.openstreetmap.josm.plugins.tracer.connectways;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.tools.Pair;

public class MergeIdenticalWays {
    private final WayEditor m_editor;
    private final IEdAreaPredicate m_filter;
    private final IEdAreaPredicate m_negatedFilter;
    
    public MergeIdenticalWays (WayEditor editor, IEdAreaPredicate filter) {
        m_editor = editor;
        m_filter = filter;
        m_negatedFilter = new NegatedAreaPredicate(filter);
    }

    public EdWay mergeWays(Set<EdWay> ways, boolean allow_inverted_orientation, EdWay watch_way) {
        Set<EdWay> inserted = new HashSet<>();
        List<List<EdWay>> bundles = new ArrayList<>(ways.size());
        
        for (EdWay way: ways) {
            // skip already included ways and ways that don't match given predicate
            if (inserted.contains(way) || !m_filter.evaluate(way))
                continue;
            
            // get all matching referrers of a way's node (mergeable ways are expected
            // to have EdNode-identical geometry, so all of them must obviously be referrers
            // of any way's node).
            EdNode node = way.getNode(0);
            List<EdWay> referrers = node.getAllAreaWayReferrers(m_filter);
            
            List<EdWay> bundle = new ArrayList<>();
            for (EdWay refway: referrers) {
                if (refway == way)
                    continue;
                // ignore ways that are also members of other relations than matching areas
                if (refway.hasMatchingReferrers(m_negatedFilter))
                    continue;
                // ignore ways with non-identical geometry
                if (!refway.hasIdenticalEdNodeGeometry(way.getNodes(), true))
                    continue;
                bundle.add(refway);
                inserted.add(refway);
            }
            if (!bundle.isEmpty()) {
                bundle.add(way);
                inserted.add(way);
                bundles.add(bundle);
            }
        }
        
        // For every bundle of identical ways, try to merge them into minimal
        // possible number of ways.
        for (List<EdWay> bundle: bundles) {
            boolean[] merged = new boolean[bundle.size()];
            for (int i = 0; (i < bundle.size()) && !merged[i]; i++) {
                for (int j = i+1; (j < bundle.size()) && !merged[j]; j++) {
                    EdWay wi = bundle.get(i);
                    EdWay wj = bundle.get(j);
                    int m = mergeTwoIdenticalWays(wi, wj);
                    if (m < 0) {
                        merged[j] = true;
                        if (watch_way != null && wj == watch_way)
                            watch_way = wi;
                    }
                    else if (m > 0) {
                        merged[i] = true;
                        if (watch_way != null && wi == watch_way)
                            watch_way = wj;
                        break;
                    }
                }
            }
        }
        
        return watch_way;
    }
        
    /**
     * @param way1 first way to merge
     * @param way2 second way to merge
     * @return negative value if second way was merged into first,
     * positive value if first way was merged into second,
     * zero if merge attempt failed.
     */
    private int mergeTwoIdenticalWays(EdWay way1, EdWay way2) {
        if (selectMergeDestWay(way1, way2))
            return mergeIdenticalWayTo(way2, way1) ? -1 : 0;
        else
            return mergeIdenticalWayTo(way1, way2) ? 1 : 0;
    }
    
    /**
     * Selects which of the given ways should be the destination way of merge.
     * It prefers original ways over newly added ways and tagged ways over
     * untagged.
     * @param way1 first way
     * @param way2 second way
     * @return true if way1 should be used as destination way, false otherwise
     */
    private boolean selectMergeDestWay(EdWay way1, EdWay way2) {
        if (way1.hasOriginal() && !way2.hasOriginal())
            return true;
        if (way2.hasOriginal() && !way1.hasOriginal())
            return false;
        return way1.isTagged();
    }
    
    private boolean mergeIdenticalWayTo(EdWay src, EdWay dst) {
        // Merge all interesting tags into destination EdWay.
        // If there's a collision, give up and don't merge ways.
        Map<String, String> tags = new HashMap<>();
        Map<String, String> srctags = src.getKeys();
        Map<String, String> dsttags = dst.getKeys();
        Set<String> keys = new HashSet<>(srctags.keySet());
        keys.addAll(dsttags.keySet());
        for (String key: keys) {
            Pair<Boolean, String> tm = mergeTagValues(key, srctags, dsttags);
            if (!tm.a)
                return false;
            tags.put(key, tm.b);
        }
        dst.setKeys(tags);

        System.out.println("Merging identical ways: " + Long.toString(src.getUniqueId()) + " => " + Long.toString(dst.getUniqueId()));
        
        // load all external multipolygons
        // (we've already checked that all referrers match the area filter)
        List<Relation> rels = src.getExternalReferrers(Relation.class);
        for (Relation rel: rels)
            m_editor.useMultipolygon(rel);        
        
        // replace all relation memberships of the source EdWay with destination EdWay memberships
        List<EdMultipolygon> mps = src.getEditorReferrers(EdMultipolygon.class);
        for (EdMultipolygon mp: mps)
            mp.replaceWay (src, dst);
        
        // remove all tags of the source EdWay
        src.setKeys(new HashMap<String,String>());
                
        return true;
    }
    
    private Pair<Boolean, String> mergeTagValues(String key, Map<String, String> set1, Map<String, String> set2) {
        String val1 = set1.get(key);
        String val2 = set2.get(key);
        if (val2 == null)
            return new Pair<>(true, val1);
        if (val1 == null)
            return new Pair<>(true, val2);
        if (val1.equals(val2))
            return new Pair<>(true, val1);
        if (!OsmPrimitive.isUninterestingKey(key)) {
            System.out.println("Cannot merge interesting tags: " + key + "=" + val1 + ", " + key + "=" + val2);
            return new Pair<>(false, null);
        }
        
        if (key.equals("source") || key.equals("fixme"))
            return new Pair<>(true, combineTagValues(val1, val2));
        
        return new Pair<>(true, val1);
    }
    
    private String combineTagValues(String val1, String val2) {
        String[] list1 = val1.split(";");
        String[] list2 = val2.split(";");        
        Set<String> set = new HashSet<>();
        for (String s: list1) {
            s = s.trim();
            if (s.isEmpty())
                continue;
            set.add(s);
        }
        for (String s: list2) {
            s = s.trim();
            if (s.isEmpty())
                continue;
            set.add(s);
        }        
        StringBuilder sb = new StringBuilder();
        for (String s: set) {
            if (sb.length() > 0)
                sb.append(";");
            sb.append(s);
        }
        return sb.toString();
    }
}
