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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.PostTraceNotifications;
import static org.openstreetmap.josm.tools.I18n.tr;

public class ClipObjectArea {

    private final WayEditor m_editor;
    private final ClipAreasSettings m_settings;
    private final PostTraceNotifications m_postTraceNotifications;

    public ClipObjectArea (WayEditor editor, ClipAreasSettings settings, PostTraceNotifications notifications) {
        m_editor = editor;
        m_settings = settings;
        m_postTraceNotifications = notifications;
    }

    public EdObject clipObject(EdObject subject, AreaPredicate filter, LatLon anchor) {

        // Note: only new objects with a proper tagging are clipped now. The algorithms below are too
        // primitive and agressive for modification of existing OSM objects...

        if (subject.hasOriginal()) {
            addPostTraceNotification(tr("Subject way has original object, object clip is not supported yet."));
            return subject;
        }

        if (subject.isMultipolygon()) {
            EdMultipolygon mp = (EdMultipolygon) subject;
            if (mp.containsTaggedWays()) {
                addPostTraceNotification(tr("Subject multipolygon contains tagged ways, object clip is not supported yet."));
                return subject;
            }
            for (EdWay way: mp.allWays()) {
                if (way.hasOriginal ()) {
                    addPostTraceNotification(tr("Subject way has original object, object clip is not supported yet."));
                    return subject;
                }
            }
        }
        else if (!subject.isWay ()) {
            throw new AssertionError ("EdObject is not a way or multipolygon");
        }

        Set<EdObject> areas1 = m_editor.useAllAreasInBBox(subject.getBBox(), filter);
        Set<EdObject> areas = new HashSet<> ();

        // (Subject must always be removed from clips set before clipping starts,
        // because clipSubject operations transform the subject into new EdObject instances.)
        for (EdObject obj : areas1) {
            if (obj == subject)
                continue;
            if (obj.isMultipolygon() && subject.isWay() && ((EdMultipolygon)obj).containsWay((EdWay)subject))
                continue;
            areas.add (obj);
        }

        for (EdObject obj : areas) {
            subject = clipSubject (obj, subject, anchor);
        }

        return subject;
    }

    private EdObject clipSubject (EdObject clip, EdObject subject, LatLon anchor) {

        System.out.println ("Clipping subject id=" + Long.toString (subject.getUniqueId()) + " by clip id=" + Long.toString (clip.getUniqueId()));

        AngPolygonClipper clipper = new AngPolygonClipper(m_editor, m_settings.clipperWayCleanupsTolerance(), m_settings.discardCutoffsPercent());
        clipper.polygonDifference(clip, subject);
        if (clipper.changesOutsideDataBounds()) {
            addPostTraceNotification(tr("Subject {0} would be modified outside downloaded area, ignoring.", subject.getUniqueId()));
            return subject;
        }

        List<List<EdNode>> outers = clipper.outerPolygons();
        List<List<EdNode>> inners = clipper.innerPolygons();

        if (outers.isEmpty() && inners.isEmpty()) {
            // Never completely discard whole subject
            System.out.println ("Subject would be removed...");
            return subject;
        }

        else if (outers.size() == 1 && inners.isEmpty()) {
            return handleSimpleResult(clip, subject, outers.get(0), anchor);
        } else if ((outers.size() + inners.size()) > 1) {
            return handleMultiResult(clip, subject, outers, inners, anchor);
        } else {
            throw new AssertionError(tr("PolygonClipper.polygonDifference returned nonsense!"));
        }
    }

    private void addPostTraceNotification(String msg) {
        if (m_postTraceNotifications == null)
            return;
        m_postTraceNotifications.add(msg);
    }

    private EdObject handleSimpleResult(EdObject clip, EdObject subject, List<EdNode> way, LatLon anchor) {

        // Check anchor point, if specified
        if (anchor != null && !isPointInsideWay (way, anchor)) {
            return subject;
        }

        // Way -> Way
        if (subject.isWay()) {
            EdWay wsubj = (EdWay) subject;
            wsubj.setNodes (way);
            connectSubjectIntersectionsToClip (clip, wsubj);
            return wsubj;
        }

        // Multipolygon -> Way
        if (subject.isMultipolygon()) {
            EdMultipolygon mpsubj = (EdMultipolygon) subject;
            if (mpsubj.containsTaggedWays())
                return mpsubj;
            EdWay wsubj = m_editor.newWay(way);
            Map<String, String> keys = mpsubj.getKeys();
            keys.remove("type");
            wsubj.setKeys(keys);
            mpsubj.deleteShallow();
            connectSubjectIntersectionsToClip (clip, wsubj);
            return wsubj;
        }

        throw new AssertionError ("EdObject is not a way or multipolygon");
    }

    private EdObject handleMultiResult(EdObject clip, EdObject subject, List<List<EdNode>> outers, List<List<EdNode>> inners, LatLon anchor) {

        // Check anchor point, if specified
        if (anchor != null && !isPointInsideMultipolygon (outers, inners, anchor)) {
            return subject;
        }

        // Way -> Multipolygon
        if (subject.isWay ()) {
            EdMultipolygon mpsubj = m_editor.newMultipolygon();
            mpsubj.setKeys(subject.getKeys());
            subject.deleteShallow();
            for (List<EdNode> outer: outers) {
                mpsubj.addOuterWay(m_editor.newWay (outer));
            }
            for (List<EdNode> inner: inners) {
                mpsubj.addInnerWay(m_editor.newWay (inner));
            }
            connectSubjectIntersectionsToClip (clip, mpsubj);
            return mpsubj;
        }

        // Multipolygon -> Multipolygon
        if (subject.isMultipolygon()) {
            EdMultipolygon mpsubj = (EdMultipolygon)subject;
            mpsubj.removeAllWays();
            for (List<EdNode> outer: outers) {
                mpsubj.addOuterWay(m_editor.newWay (outer));
            }
            for (List<EdNode> inner: inners) {
                mpsubj.addInnerWay(m_editor.newWay (inner));
            }
            connectSubjectIntersectionsToClip (clip, mpsubj);
            return mpsubj;
        }

        throw new AssertionError ("EdObject is not a way or multipolygon");
    }

    private void connectSubjectIntersectionsToClip (EdObject clip, EdObject newsubject) {
        // Connect clip to subject, this step guarantees that all newly created
        // intersection nodes will be included in both ways.
        clip.connectNonIncludedTouchingNodes(m_settings.reconnectIntersectionNodesTolerance(), newsubject);
    }

    private boolean isPointInsideWay(List<EdNode> way, LatLon anchor) {
        // #### fixme
        return true;
    }

    private boolean isPointInsideMultipolygon(List<List<EdNode>> outers, List<List<EdNode>> inners, LatLon anchor) {
        // #### fixme
        return true;
    }


}
