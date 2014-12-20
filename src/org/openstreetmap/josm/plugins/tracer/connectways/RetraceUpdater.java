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

import java.util.List;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.plugins.tracer.PostTraceNotifications;
import static org.openstreetmap.josm.tools.I18n.tr;

public class RetraceUpdater {

    private final boolean m_convertOldStyleMultipolygons;
    private final PostTraceNotifications m_postTraceNotifications;

    public RetraceUpdater(boolean convert_oldstyle_multipolygons, PostTraceNotifications posttrace_notifications) {
        m_convertOldStyleMultipolygons = convert_oldstyle_multipolygons;
        m_postTraceNotifications = posttrace_notifications;
    }

    public EdObject updateRetracedObjects(EdObject new_object, EdObject retrace_object) {

        boolean retrace_is_simple_way = retrace_object.isWay() && !retrace_object.hasReferrers();
        boolean new_is_way = new_object.isWay();
        boolean new_is_multipolygon = new_object.isMultipolygon();

        // Simple way -> Simple way
        if (retrace_is_simple_way && new_is_way) {
            EdWay outer_way = (EdWay) new_object;
            EdWay retrace_way = (EdWay) retrace_object;
            retrace_way.setNodes(outer_way.getNodes());
            outer_way = retrace_way;
            return outer_way;
        }

        // Simple way -> Multipolygon
        // Move all non-linear tags from way to multipolygon, use retraced way as outer way of the new multipolygon
        if (retrace_is_simple_way && new_is_multipolygon) {
            EdWay retrace_way = (EdWay) retrace_object;
            EdMultipolygon multipolygon = (EdMultipolygon)new_object;
            multipolygon.moveAllNonLinearTagsFrom(retrace_way);
            EdWay outer_way = multipolygon.outerWays().get(0);
            multipolygon.removeOuterWay(outer_way);
            retrace_way.setNodes(outer_way.getNodes());
            outer_way = retrace_way;
            multipolygon.addOuterWay(outer_way);
            return multipolygon;
        }

        // Multipolygon -> Multipolygon
        if (retrace_object.isMultipolygon() && new_is_multipolygon) {
            EdMultipolygon retrace_multipolygon = (EdMultipolygon)retrace_object;
            EdMultipolygon new_multipolygon = (EdMultipolygon)new_object;
            EdObject res = updateRetracedMultipolygons(new_multipolygon, retrace_multipolygon);
            if (res != null)
                return res;
        }

        m_postTraceNotifications.add(tr("This kind of multipolygon retrace is not supported."));
        return null;
    }

    private EdObject updateRetracedMultipolygons(EdMultipolygon new_multipolygon, EdMultipolygon retrace_multipolygon) {

        // don't retrace non-standalone multipolygons
        boolean retrace_is_standalone = !retrace_multipolygon.hasReferrers() && retrace_multipolygon.allWaysHaveSingleReferrer();
        if (!retrace_is_standalone)
            return null;

        // don't retrace multipolygons with nonclosed ways
        boolean retrace_is_closed = !retrace_multipolygon.containsNonClosedWays();
        if (!retrace_is_closed)
            return null;

        // try to convert old-style multipolygon to new-style
        if (m_convertOldStyleMultipolygons && retrace_multipolygon.containsTaggedWays() && Main.pref.getBoolean("multipoly.movetags", true))
            retrace_multipolygon.removeTagsFromWaysIfNeeded();

        // agressive retrace of multipolygon with untagged ways
        if (!retrace_multipolygon.containsTaggedWays()) {
            updateRetracedMultipolygonWaysAgressive(retrace_multipolygon, new_multipolygon, true);
            updateRetracedMultipolygonWaysAgressive(retrace_multipolygon, new_multipolygon, false);
            new_multipolygon.deleteShallow();
            return retrace_multipolygon;
        }

        // other possibilites not supported yet
        return null;
    }

    private void updateRetracedMultipolygonWaysAgressive(EdMultipolygon retrace_multipolygon, EdMultipolygon new_multipolygon, boolean out) {
        // #### use pairing based on way area intersection?

        List<EdWay> retraces = out ? retrace_multipolygon.outerWays() : retrace_multipolygon.innerWays();
        List<EdWay> news = out ? new_multipolygon.outerWays() : new_multipolygon.innerWays();
        int ridx = 0;

        for (EdWay new_way : news) {
            // update geometry of existing way
            if (ridx < retraces.size()) {
                EdWay retrace_way = retraces.get(ridx++);
                retrace_way.setNodes(new_way.getNodes());
            }
            // no more existing ways available, add new way
            else if (out)
                retrace_multipolygon.addOuterWay(new_way);
            else
                retrace_multipolygon.addInnerWay(new_way);
        }

        // remove unused old ways if the new multipolygon has less ways than the old one
        for (int i = ridx; i < retraces.size(); i++) {
            if (out)
                retrace_multipolygon.removeOuterWay(retraces.get(i));
            else
                retrace_multipolygon.removeInnerWay(retraces.get(i));
        }
    }
}
