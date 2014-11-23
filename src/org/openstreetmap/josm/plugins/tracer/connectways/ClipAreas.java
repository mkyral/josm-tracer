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


package org.openstreetmap.josm.plugins.tracer.connectways;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.openstreetmap.josm.plugins.tracer.PostTraceNotifications;
import static org.openstreetmap.josm.tools.I18n.tr;
import org.openstreetmap.josm.tools.Pair;

public class ClipAreas {

    private final WayEditor m_editor;
    private final GeomConnector m_gconn;
    private final PostTraceNotifications m_postTraceNotifications;
    private final double m_DiscardCutoffsPercent;
    private final IDiscardableCutoffPredicate m_discardablePredicate;

    public ClipAreas (WayEditor editor, GeomConnector gconn, PostTraceNotifications notifications) {
        this (editor, gconn, 0.0, null, notifications);
    }

    public ClipAreas (WayEditor editor, GeomConnector gconn, double discard_cutoffs_percent, IDiscardableCutoffPredicate dispred, PostTraceNotifications notifications) {
        m_editor = editor;
        m_gconn = gconn;
        m_DiscardCutoffsPercent = discard_cutoffs_percent;
        m_discardablePredicate = dispred;
        m_postTraceNotifications = notifications;
    }

    public void clipAreas(EdWay clip_way, AreaPredicate filter) {

        Set<EdObject> areas = m_editor.useAllAreasInBBox(clip_way.getBBox(), filter);
        for (EdObject obj : areas) {
            if (obj instanceof EdMultipolygon) {
                EdMultipolygon subject_mp = (EdMultipolygon) obj;
                if (subject_mp.containsWay(clip_way)) {
                    continue;
                }

                clipSimpleMulti(clip_way, subject_mp);

            } else if (obj instanceof EdWay) {
                EdWay subject_way = (EdWay) obj;
                if (subject_way == clip_way) {
                    continue;
                }

                clipSimpleSimple(clip_way, subject_way);
            }
        }
    }

    private boolean canSilentlyDiscard(EdWay way, double cutoffs_percent) {
        if (m_discardablePredicate == null)
            return false;
        if (way.hasReferrers())
            return false;        
        return m_discardablePredicate.canSilentlyDiscard(way, cutoffs_percent);
    }

    private void clipSimpleSimple(EdWay clip_way, EdWay subject_way) {
        // First, connect touching nodes of subject_way to clip_way. This is necessary because
        // LPIS polygons series contain very small gaps that need to be elliminated before
        // clipping is performed. Also, there are false joint points on LPIS polygons' edges
        // where nodes must be added too.
        subject_way.connectNonIncludedTouchingNodes(m_gconn, clip_way);

        System.out.println("Computing difference: clip_way=" + Long.toString(clip_way.getUniqueId()) + ", subject_way=" + Long.toString(subject_way.getUniqueId()));

        AngPolygonClipper clipper = new AngPolygonClipper(m_editor, m_gconn, m_DiscardCutoffsPercent);
        clipper.polygonDifference(clip_way, subject_way);
        List<List<EdNode>> outers = clipper.outerPolygons();
        List<List<EdNode>> inners = clipper.innerPolygons();

        System.out.println("- result: outers=" + Long.toString(outers.size()) + ", inners=" + Long.toString(inners.size()));

        if (outers.isEmpty() && inners.isEmpty()) {
            if (canSilentlyDiscard(subject_way, clipper.discardedPercent())) {
                // #### fix EdObjects deletion support, instead of this hack
                subject_way.setKeys(new HashMap<String, String>());
                subject_way.setNodes(new ArrayList<EdNode>());
            }
            else {
                addPostTraceNotification(tr("Simple way {0} would be completely removed, ignoring, please check.", subject_way.getUniqueId()));
            }
        } else if (outers.size() == 1 && inners.isEmpty()) {
            handleSimpleSimpleSimple(clip_way, subject_way, outers.get(0));
        } else if ((outers.size() + inners.size()) > 1) {
            handleSimpleSimpleMulti(clip_way, subject_way, outers, inners);
        } else {
            throw new AssertionError(tr("PolygonClipper.polygonDifference returned nonsense!"));
        }
    }

    private void clipSimpleMulti(EdWay clip_way, EdMultipolygon subject_mp) {

        boolean subject_has_nonclosed_ways = subject_mp.containsNonClosedWays();

        // #### add support for multipolygons with non-closed ways
        if (subject_has_nonclosed_ways) {
            addPostTraceNotification(tr("Ignoring multipolygon {0}, it contains non-closed ways.", subject_mp.getUniqueId()));
            return;
        }

        // First, connect all touching nodes of all subject ways to clip_way. This is necessary because
        // LPIS polygons series contain very small gaps that need to be elliminated before
        // clipping is performed. Also, there are false joint points on LPIS polygons' edges
        // where nodes must be added too.
        for (EdWay way : subject_mp.allWays()) {
            way.connectNonIncludedTouchingNodes(m_gconn, clip_way);
        }

        System.out.println("Computing difference: clip_way=" + Long.toString(clip_way.getUniqueId()) + ", subject_relation=" + Long.toString(subject_mp.getUniqueId()));

        AngPolygonClipper clipper = new AngPolygonClipper(m_editor, m_gconn, m_DiscardCutoffsPercent);
        clipper.polygonDifference(clip_way, subject_mp);
        List<List<EdNode>> unmapped_new_outers = new ArrayList<>(clipper.outerPolygons());
        List<List<EdNode>> unmapped_new_inners = new ArrayList<>(clipper.innerPolygons());

        System.out.println("- result: outers=" + Long.toString(unmapped_new_outers.size()) + ", inners=" + Long.toString(unmapped_new_inners.size()));

        // Whole multipolygon disappeared
        if (unmapped_new_outers.isEmpty() && unmapped_new_inners.isEmpty()) {
            // #### add handling of discarded cutoffs, but be careful
            addPostTraceNotification(tr("Multipolygon {0} would be completely removed, ignoring, please check.", subject_mp.getUniqueId()));
            return;
        }

        // Create bi-directional mapping of identical geometries
        List<EdWay> unmapped_old_outers = new ArrayList<>(subject_mp.outerWays());
        List<EdWay> unmapped_old_inners = new ArrayList<>(subject_mp.innerWays());
        mapIdenticalWays(unmapped_old_outers, unmapped_new_outers);
        mapIdenticalWays(unmapped_old_inners, unmapped_new_inners);

        // All new ways were successfully mapped to old ways?
        if (unmapped_old_outers.isEmpty() && unmapped_old_inners.isEmpty() && unmapped_new_outers.isEmpty() && unmapped_new_inners.isEmpty()) {
            System.out.println(" o subject unchanged");
            return;
        }

        System.out.println("- unmapped_outers: old=" + Long.toString(unmapped_old_outers.size()) + ", new=" + Long.toString(unmapped_new_outers.size()));
        System.out.println("- unmapped_inners: old=" + Long.toString(unmapped_old_inners.size()) + ", new=" + Long.toString(unmapped_new_inners.size()));

        // Handle the easiest and most common case, only one outer way of a multipolygon was clipped
        // (Maybe, I should test that the old and new outer ways have non-empty intersection. Otherwise,
        // it means that one brand new outer way was created and one old outer way was completely deleted.
        // But as long as clip is a simple way, it should never happen, because new way can be created only
        // by clipping another existing way.)
        if (!subject_has_nonclosed_ways && unmapped_old_outers.size() == 1 && unmapped_new_outers.size() == 1
                && unmapped_old_inners.isEmpty() && unmapped_new_inners.isEmpty()) {
            EdWay old_outer_way = unmapped_old_outers.get(0);
            List<EdNode> new_outer_way = unmapped_new_outers.get(0);
            handleSimpleMultiOneOuterModified(clip_way, subject_mp, old_outer_way, new_outer_way);
            return;
        }

        // If modified ways have no (interesting) tags and no other referrers, we can
        // replace multipolygon's geometry quite agressively.
        if (!subject_has_nonclosed_ways
                && untaggedSingleReferrerWays(unmapped_old_outers, subject_mp)
                && untaggedSingleReferrerWays(unmapped_old_inners, subject_mp)) {
            handleSimpleMultiAgressiveUpdate(clip_way, subject_mp,
                    unmapped_old_outers, unmapped_old_inners, unmapped_new_outers, unmapped_new_inners);
            return;
        }

        addPostTraceNotification(tr("Multipolygon clipping result of {0} is too complex.", subject_mp.getUniqueId()));
    }

    /**
     * Returns true if all ways in the list have no (interesting) tag,
     * have the given multipolygon referrer and no other referrers.
     * @param list list of ways to check
     * @param referrer expected single referrer
     * @return true if all ways satisfy the requirements
     */
    private static boolean untaggedSingleReferrerWays(List<EdWay> list, EdMultipolygon referrer) {
        for (EdWay way: list) {
            if (way.isTagged())
                return false;
            if (!way.hasSingleReferrer(referrer))
                return false;
        }
        return true;
    }

    private static void mapIdenticalWays(List<EdWay> unmapped_old, List<List<EdNode>> unmapped_new) {
        int iold = 0;
        while (iold < unmapped_old.size()) {
            EdWay old_way = unmapped_old.get(iold);
            List<EdNode> new_way = null;
            int inew = 0;
            for (; inew < unmapped_new.size(); inew++) {
                List<EdNode> test_way = unmapped_new.get(inew);
                if (old_way.hasIdenticalEdNodeGeometry(test_way, true)) {
                    new_way = test_way;
                    break;
                }
            }
            if (new_way != null) {
                //mapped_old_new.put(old_way, new_way);
                //mapped_new_old.put(new_way, old_way);
                unmapped_old.remove(iold);
                unmapped_new.remove(inew);
            }
            else {
                iold++;
            }
        }
    }

    class SimilarWaysPair implements Comparable<SimilarWaysPair> {
        public final EdWay src;
        public final List<EdNode> dst;
        public final double similarity;

        public SimilarWaysPair(EdWay s, List<EdNode> d, double sim) {
            src = s;
            dst = d;
            similarity = sim;
        }

        @Override
        public int compareTo(SimilarWaysPair t) {
            int x = -Double.compare(this.similarity, t.similarity);
            if (x != 0)
                return x;
            return -Integer.compare(this.src.getNodesCount(), t.src.getNodesCount());
        }
    }

    /**
     * For every source EdWay, it tries to find the most similar dest way;
     * for every dest way, it tries to find the most similar source EdWay.
     * Ways with no suitable similar ways are excluded from the results.
     * Forward mapping is guaranteed to be a bijection (suitable for
     * way updates), reverse mapping can contain duplicate values (surjection).
     *
     * @param srcs list of source EdWays
     * @param dsts list of dest ways
     * @return a pair, first element is a map of source EdWays to most similar
     * dest ways, second element is a map of dest ways to most similar source EdWay
     */
    private Pair<Map<EdWay, List<EdNode>>, Map<List<EdNode>, EdWay>> pairSimilarWays (List<EdWay> srcs, List<List<EdNode>> dsts) {
        // Create Cartesian product of ways and sort pairs according to their similarity
        // (For simplicity, we estimate similarity from percentage of shared nodes.
        // It would be better to base the similarity on areas of polygon intersections.)
        PriorityQueue<SimilarWaysPair> queue = new PriorityQueue<>();
        for(EdWay w: srcs) {
            for (List<EdNode> nw: dsts) {
                int shared_nodes = w.getSharedNodesCount(nw);
                if (shared_nodes > 0) {
                    double similarity = (double)shared_nodes / w.getNodesCount();
                    queue.add(new SimilarWaysPair(w, nw, similarity));
                }
            }
        }

        // Map ways...
        Map<EdWay, List<EdNode>> res1 = new HashMap<>();
        Map<List<EdNode>, EdWay> res2 = new HashMap<>();
        Set<List<EdNode>> res1used = new HashSet<>();
        SimilarWaysPair swp;
        while ((swp = queue.poll()) != null) {

            // forward mapping
            if (!res1.containsKey(swp.src) && !res1used.contains(swp.dst)) {
                res1.put(swp.src, swp.dst);
                res1used.add(swp.dst);
            }

            // reverse mapping
            if (!res2.containsKey(swp.dst)) {
                res2.put(swp.dst, swp.src);
            }
        }

        return new Pair<>(res1, res2);
    }

    private void handleSimpleSimpleSimple(EdWay clip_way, EdWay subject_way, List<EdNode> result) {
        // ** Easiest case - simple way clipped by a simple way produced a single polygon **

        System.out.println("Clip result: simple");

        // Subject way unchanged?
        if (subject_way.hasIdenticalEdNodeGeometry(result, true)) {
            System.out.println(" o subject unchanged");
            return;
        }

        System.out.println(" ! CLIPPING subject " + Long.toString(subject_way.getUniqueId()));

        // Subject way changed, change its geometry
        subject_way.setNodes(result);

        // Connect clip_way to subject_way, this step guarantees that all newly created
        // intersection nodes will be included in both ways.
        clip_way.connectNonIncludedTouchingNodes(m_gconn, subject_way);
    }

    private void handleSimpleMultiOneOuterModified (EdWay clip_way, EdMultipolygon subject_mp, EdWay old_outer_way, List<EdNode> result) {
        // ** Easy case - clip of a multipolygon modified exactly one outer way and nothing else **

        System.out.println(" ! CLIPPING subject " + Long.toString(subject_mp.getUniqueId()) + ", outer way modified: " + Long.toString(old_outer_way.getUniqueId()));

        // Change geometry of the changed outer way
        old_outer_way.setNodes(result);

        // Connect clip_way to subject_way, this step guarantees that all newly created
        // intersection nodes will be included in both ways.
        clip_way.connectNonIncludedTouchingNodes(m_gconn, old_outer_way);
    }

    private void handleSimpleSimpleMulti(EdWay clip_way, EdWay subject_way, List<List<EdNode>> outers, List<List<EdNode>> inners) {
        // ** Simple way clipped by a simple way produced multiple polygons **

        if (inners.isEmpty()) {
            System.out.println("Clip result: multi outers");
            handleSimpleSimpleMultiOuters(clip_way, subject_way, outers);
        }
        else {
            System.out.println("Clip result: multi mixed");
            // #### not completed
            addPostTraceNotification(tr("Clipping changes simple way {0} to multipolygon, not supported yet.", subject_way.getUniqueId()));
        }
    }

    private void handleSimpleSimpleMultiOuters(EdWay clip_way, EdWay subject_way, List<List<EdNode>> outers) {
        // ** Simple subject clipped by a simple way produced multiple simple (outer) polygons **

        // Don't clip subject which is a member of a (non-multipolygon) relation.
        // It's questionable if all pieces should be added to the relation, or only some of them, etc...
        if (subject_way.hasEditorReferrers() || subject_way.hasExternalReferrers()) {
            addPostTraceNotification(tr("Clipped way {0} is a member of non-multipolygon relation, not supported yet.", subject_way.getUniqueId()));
            return;
        }

        System.out.println(" ! CLIPPING subject " + Long.toString(subject_way.getUniqueId()) + " to multiple simple ways");

        // #### Generally, it's better to create multiple simple ways than combine them to a new multipolygon.
        // But in some cases, maybe it would make sense to create a multipolygon... E.g. named landuse areas??

        // find the largest polygon, which will be used to update subject_way's geometry
        List<EdNode> maxnodes = null;
        double maxarea = Double.NEGATIVE_INFINITY;
        for (List<EdNode> nodes: outers) {
            double area = GeomConnector.getEastNorthArea(nodes);
            if (area < maxarea)
                continue;
            maxnodes = nodes;
            maxarea = area;
        }

        // update subject_way geometry and create new ways for the other pieces
        for (List<EdNode> nodes: outers) {
            if (nodes == maxnodes) {
                subject_way.setNodes(nodes);
                clip_way.connectNonIncludedTouchingNodes(m_gconn, subject_way);
            }
            else {
                EdWay new_way = m_editor.newWay(nodes);
                new_way.setKeys(subject_way.getKeys());
                clip_way.connectNonIncludedTouchingNodes(m_gconn, new_way);
            }
        }
    }

    private void handleSimpleMultiAgressiveUpdate(EdWay clip_way, EdMultipolygon subject_mp, List<EdWay> unmapped_old_outers, List<EdWay> unmapped_old_inners, List<List<EdNode>> unmapped_new_outers, List<List<EdNode>> unmapped_new_inners) {
        // ** Agressive update of a multipolygon clipped by a simple way

        if (!unmapped_old_outers.isEmpty() || !unmapped_new_outers.isEmpty()) {

            // Update geometry of outer ways that can be paired together as similar ones
            Pair<Map<EdWay, List<EdNode>>, Map<List<EdNode>, EdWay>> outer_pair_maps = pairSimilarWays (unmapped_old_outers, unmapped_new_outers);
            Map<EdWay, List<EdNode>> outer_pairs = outer_pair_maps.a;
            Map<List<EdNode>, EdWay> outer_revs = outer_pair_maps.b;
            for (Map.Entry<EdWay, List<EdNode>> pair: outer_pairs.entrySet()) {
                EdWay old_way = pair.getKey();
                List<EdNode> new_nodes = pair.getValue();
                old_way.setNodes(new_nodes);
                clip_way.connectNonIncludedTouchingNodes(m_gconn, old_way);
                unmapped_old_outers.remove(old_way);
                unmapped_new_outers.remove(new_nodes);
                System.out.println("Changing outer geometry " + Long.toString(old_way.getUniqueId()));
            }

            // Create new outer ways with tagging based on reverse similarity mapping
            // (If no reverse mapping is available, leave way untagged)
            for (List<EdNode> new_nodes: unmapped_new_outers) {
                EdWay new_way = m_editor.newWay(new_nodes);
                EdWay old_way = outer_revs.get(new_nodes);
                if (old_way != null)
                    new_way.setKeys(old_way.getKeys());
                clip_way.connectNonIncludedTouchingNodes(m_gconn, new_way);
                subject_mp.addOuterWay(new_way);
                System.out.println("Adding outer way " + Long.toString(new_way.getUniqueId()));
            }

            // Remove old outer ways that weren't mapped to new ways
            // (We assume that the ways have no interesting tags and referrers,
            // and will be automatically deleted by WayEditor.)
            for (EdWay old_way: unmapped_old_outers) {
                subject_mp.removeOuterWay(old_way);
                System.out.println("Removing outer way " + Long.toString(old_way.getUniqueId()));
            }
        }

        if (!unmapped_old_inners.isEmpty() || !unmapped_new_inners.isEmpty()) {

            // Update geometry of inner ways that can be paired together as similar ones
            Pair<Map<EdWay, List<EdNode>>, Map<List<EdNode>, EdWay>> inner_pair_maps = pairSimilarWays (unmapped_old_inners, unmapped_new_inners);
            Map<EdWay, List<EdNode>> inner_pairs = inner_pair_maps.a;
            Map<List<EdNode>, EdWay> inner_revs = inner_pair_maps.b;
            for (Map.Entry<EdWay, List<EdNode>> pair: inner_pairs.entrySet()) {
                EdWay old_way = pair.getKey();
                List<EdNode> new_nodes = pair.getValue();
                old_way.setNodes(new_nodes);
                clip_way.connectNonIncludedTouchingNodes(m_gconn, old_way);
                unmapped_old_inners.remove(old_way);
                unmapped_new_inners.remove(new_nodes);
                System.out.println("Changing inner geometry " + Long.toString(old_way.getUniqueId()));
            }

            // Create new inner ways
            for (List<EdNode> new_nodes: unmapped_new_inners) {
                // use tagging of the most similar reverse mapped way, if any
                EdWay new_way = m_editor.newWay(new_nodes);
                EdWay old_way = inner_revs.get(new_nodes);
                if (old_way != null)
                    new_way.setKeys(old_way.getKeys());
                clip_way.connectNonIncludedTouchingNodes(m_gconn, new_way);
                subject_mp.addInnerWay(new_way);
                System.out.println("Adding inner way " + Long.toString(new_way.getUniqueId()));
            }

            // Remove old inner ways that weren't mapped to new ways
            // (We assume that the ways have no interesting tags and referrers,
            // and will be automatically deleted by WayEditor.)
            for (EdWay old_way: unmapped_old_inners) {
                subject_mp.removeInnerWay(old_way);
                System.out.println("Removing inner way " + Long.toString(old_way.getUniqueId()));
            }
        }
    }

    private void addPostTraceNotification(String msg) {
        if (m_postTraceNotifications == null)
            return;
        m_postTraceNotifications.add(msg);
    }

}


