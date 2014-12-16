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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;

public class RemoveNeedlessNodes {
    private final IEdAreaPredicate m_filter;
    private final IEdAreaPredicate m_negatedFilter;

    private Set<EdNode> m_requiredNodes = null;
    private Set<EdNode> m_validBindingsCache = null;
    private Set<EdNode> m_nodesToRemove = null;
    private Set<EdWay> m_removeInWays = null;

    private final GeomDeviation m_XteDeviation;
    private final double m_MinimalVertexAngle;
    private final BBox m_removeBBox; // can be null

    public RemoveNeedlessNodes (IEdAreaPredicate filter, GeomDeviation xte_deviation, double minimal_vertex_angle, BBox remove_bbox) {
        m_filter = filter;
        m_negatedFilter = new NegatedAreaPredicate(filter);
        m_XteDeviation = xte_deviation;
        m_MinimalVertexAngle = minimal_vertex_angle;
        m_removeBBox = remove_bbox;
    }

    public RemoveNeedlessNodes (IEdAreaPredicate filter, GeomDeviation xte_deviation, double minimal_vertex_angle) {
        this (filter, xte_deviation, minimal_vertex_angle, null);
    }


    public void removeNeedlessNodes(Set<EdWay> input_ways) {

        System.out.println("Removing needless nodes");

        m_requiredNodes = new HashSet<>();
        m_validBindingsCache = new HashSet<>();
        m_nodesToRemove = new HashSet<>();
        m_removeInWays = new HashSet<>();

        Set<EdWay> ways = new HashSet<>();
        for (EdWay way: input_ways) {
            if (m_filter.evaluate(way)) {
                ways.add(way);
                m_removeInWays.add(way);
            }
        }

        for (EdWay way: ways) {
            collectRequiredNodes(way);
        }

        for (EdWay way: ways) {
            selectNeedlessNodesInWay(way);
        }

        for (EdWay way: m_removeInWays) {
            removeNeedlessNodesInWay(way);
        }
    }

    private void removeNeedlessNodesInWay(EdWay way) {
        List<EdNode> nodes = way.getNodes();
        int ncount = nodes.size();
        boolean closed = way.isClosed();

        if (closed)
            ncount--;

        List<EdNode> result = new ArrayList<>();
        boolean modified = false;

        for (int i = 0; i < ncount; i++) {
            EdNode n = nodes.get(i);
            if (m_requiredNodes.contains(n) || !m_nodesToRemove.contains(n)) {
                result.add(n);
            }
            else {
                System.out.println(" + Removing needless node " + Long.toString(n.getUniqueId()) + " from way " + Long.toString(way.getUniqueId()));
                modified = true;
            }
        }

        if (!modified)
            return;

        if (closed)
            result.add(result.get(0));
        way.setNodes(result);
    }

    private void selectNeedlessNodesInWay(EdWay way) {

        List<EdNode> nodes = way.getNodes();
        int ncount = nodes.size();
        boolean closed = way.isClosed();

        if (closed)
            ncount--;

        // find first required node
        int first = -1;
        for (int i = 0; i < ncount; i++) {
            if (m_requiredNodes.contains(nodes.get(i))) {
                first = i;
                break;
            }
        }

        // no required nodes in way? #### maybe find node with the maximal vertex angle
        if (first < 0)
            first = 0;

        int last = closed ? first : ncount - 1;
        int start = first;
        do {
            // find next required node
            int i = (start + 1) % ncount;
            for (; i != last; i = (i + 1) % ncount) {
                if (m_requiredNodes.contains(nodes.get(i)))
                    break;
            }

            selectNeedlessNodesInSegment(nodes, start, i, ncount);
            start = i;

        } while (start != last);
    }

    private void selectNeedlessNodesInSegment(List<EdNode> nodes, int first, int last, int ncount) {

        if (first == last)
            return;
        int i = (first + 1) % ncount;
        if (i == last)
            return;

        EdNode n1 = nodes.get(first);
        EdNode n2 = nodes.get(last);

        int imaxd = -1;
        double maxd = -1;
        int imaxa = -1;
        double maxa = -1;

        for (; i != last; i = (i + 1) % ncount) {
            EdNode p = nodes.get(i);
            GeomDeviation dev = GeomUtils.pointDeviationFromSegment(p, n1, n2);
            System.out.println(" - Xte distance: " + Double.toString(dev.distanceMeters()) + ", angle: " + Double.toString(Math.toDegrees(dev.angleRad())) + ", p: " + Long.toString(p.getUniqueId()) + ", n1: " + Long.toString(n1.getUniqueId()) + ", n2: " + Long.toString(n2.getUniqueId()));
            if (!dev.inTolerance(m_XteDeviation)) {
                if (imaxd < 0 || dev.distanceMeters() > maxd) {
                    imaxd = i;
                    maxd = dev.distanceMeters();
                }
                if (imaxa < 0 || dev.angleRad() > maxa) {
                    imaxa = i;
                    maxa = dev.angleRad();
                }
            }
        }

        if (imaxd >= 0 || imaxa >= 0) {
            int imax = (imaxd >= 0) ? imaxd : imaxa;
            m_requiredNodes.add(nodes.get(imax));
            selectNeedlessNodesInSegment(nodes, first, imax, ncount);
            selectNeedlessNodesInSegment(nodes, imax, last, ncount);
            return;
        }

        for (i = (first + 1) % ncount; i != last; i = (i + 1) % ncount) {
            m_nodesToRemove.add(nodes.get(i));
        }
    }

    private void collectRequiredNodes(EdWay way) {

        List<EdNode> nodes = way.getNodes();
        int ncount = nodes.size();
        boolean closed = way.isClosed();

        if (closed)
            ncount--;

        if (ncount <= 2) {
            m_requiredNodes.addAll(nodes);
            return;
        }

        List<Bounds> bounds = way.getEditor().getDataSet().getDataSourceBounds();
        Set<EdNode> seen_nodes = new HashSet<>();

        for (int i = 0; i < ncount; i++) {
            EdNode cur_node = nodes.get(i);

            // already required?
            if (m_requiredNodes.contains(cur_node))
                continue;

            // node outside bbox?
            if (m_removeBBox != null && !m_removeBBox.bounds(cur_node.getCoor())) {
                m_requiredNodes.add(cur_node);
                continue;
            }

            // tagged, first/last node of non-closed way, 
            // or node occurring more than once in the way?
            if (cur_node.isTagged() ||
                    (!closed && (i == 0 || i == ncount-1)) ||
                    seen_nodes.contains(cur_node)) {
                m_requiredNodes.add(cur_node);
                continue;
            }

            seen_nodes.add(cur_node);

            // global node tests, cache them to avoid repeated checks
            if (!m_validBindingsCache.contains(cur_node)) {

                // filter out nodes outside downloaded area
                if (!cur_node.isInsideBounds(bounds, LatLonSize.Zero)) {
                    m_requiredNodes.add(cur_node);
                    System.out.println(" - Outside-bounds node " + Long.toString(cur_node.getUniqueId()));
                    continue;
                }

                List<EdWay> referrers = cur_node.getAllAreaWayReferrers(m_filter);

                // filter out non-matching and suspicious memberships
                boolean othref = false;
                for (EdWay refway: referrers) {
                    if (!m_filter.evaluate(refway) || refway.hasMatchingReferrers(m_negatedFilter)) {
                        othref = true;
                        break;
                    }
                }
                if (othref || cur_node.hasExternalReferrers()) {
                    m_requiredNodes.add(cur_node);
                    continue;
                }

                // node is a junction where different neighbors met
                EdNode prev_node = nodes.get((i + ncount - 1) % ncount);
                EdNode next_node = nodes.get((i + 1) % ncount);
                if (!nodeHasSameNeighborsInAllWays(cur_node, way, referrers, prev_node, next_node)) {
                    m_requiredNodes.add(cur_node);
                    System.out.println(" - Junction node " + Long.toString(cur_node.getUniqueId()));
                    continue;
                }

                // check minimal vertex angle
                if (m_MinimalVertexAngle > 0) {
                    double angle = GeomUtils.unorientedAngleBetween(prev_node, cur_node, next_node);
                    if (angle < m_MinimalVertexAngle) {
                        m_requiredNodes.add(cur_node);
                        System.out.println(" - Angle node " + Long.toString(cur_node.getUniqueId()));
                        continue;
                    }
                }

                m_removeInWays.addAll(referrers);
                m_validBindingsCache.add(cur_node);

                System.out.println(" - Needless candidate " + Long.toString(cur_node.getUniqueId()));
            }
        }
    }

    private boolean nodeHasSameNeighborsInAllWays(EdNode node, EdWay skip_way, List<EdWay> ways, EdNode node1, EdNode node2) {
        for (EdWay way: ways) {
            if (way == skip_way)
                continue;
            if (!nodeHasSameNeighborsInWay(node, way, node1, node2))
                return false;
        }
        return true;
    }

    private boolean nodeHasSameNeighborsInWay (EdNode node, EdWay way, EdNode node1, EdNode node2) {
        List<EdNode> nodes = way.getNodes();
        int ncount = nodes.size();
        boolean closed = way.isClosed();

        if (closed)
            ncount--;

        if (ncount <= 2)
            return false;

        for (int i = 0; i < ncount; i++) {
            EdNode cur_node = nodes.get(i);
            if (cur_node != node)
                continue;

            EdNode prev_node =
                    (!closed && i == 0) ? null :
                    nodes.get((i + ncount - 1) % ncount);
            EdNode next_node =
                    (!closed && i == ncount - 1) ? null :
                    nodes.get((i + 1) % ncount);

            boolean same_neighbors =
                    (prev_node == node1) && (next_node == node2) ||
                    (prev_node == node2) && (next_node == node1);
            if (!same_neighbors)
                return false;
        }

        return true;
    }

}
