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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openstreetmap.josm.actions.CreateMultipolygonAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.spi.preferences.Config;
import static org.openstreetmap.josm.tools.I18n.tr;


public class EdMultipolygon extends EdObject {
    private Relation m_relation;
    private List<EdWay> m_outerWays;
    private List<EdWay> m_innerWays;

    EdMultipolygon (WayEditor editor) {
        super(editor, null);
        m_relation = new Relation();
        m_relation.put("type", "multipolygon");

        m_outerWays = new ArrayList<> ();
        m_innerWays = new ArrayList<> ();
    }

    EdMultipolygon(WayEditor editor, Relation original_rel) {
        super(editor, original_rel);
        m_relation = new Relation(original_rel);
        while (m_relation.getMembersCount() > 0)
            m_relation.removeMember(m_relation.getMembersCount()-1);

        m_outerWays = new ArrayList<>();
        m_innerWays = new ArrayList<>();

        for (RelationMember member: original_rel.getMembers()) {
            if (!member.isWay())
                throw new IllegalArgumentException(tr("Cannot edit multipolygon with non-Way members!"));
            if (!member.hasRole())
                throw new IllegalArgumentException(tr("Cannot edit multipolygon with members having no role!"));
            switch (member.getRole()) {
                case "outer":
                    m_outerWays.add(editor.useWay(member.getWay()));
                    break;
                case "inner":
                    m_innerWays.add(editor.useWay(member.getWay()));
                    break;
                default:
                    throw new IllegalArgumentException(tr("Cannot edit multipolygon member with unknown role: {0}", member.getRole()));
            }
        }

        for (EdWay w: m_outerWays)
            w.addRef(this);
        for (EdWay w: m_innerWays)
            w.addRef(this);
    }

    public static boolean isUsableRelation(Relation rel) {
        if (!rel.isUsable() || rel.hasIncompleteMembers())
            return false;
        if (rel.getMembersCount() <= 0)
            return false;
        for (RelationMember member: rel.getMembers()) {
            if (!member.isWay())
                return false;
            if (!member.hasRole())
                return false;
            switch (member.getRole()) {
                case "outer":
                case "inner":
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    public void addOuterWay(EdWay edway) {
        checkEditable();
        if (edway == null)
            throw new IllegalArgumentException();
        if (!this.getEditor().ownedByEditor(edway))
            throw new IllegalArgumentException(tr("EdWay from a different WayEditor"));

        if (m_outerWays.contains (edway) || m_innerWays.contains (edway))
            throw new IllegalArgumentException(tr("EdWay is already member of the multipolygon"));

        m_outerWays.add(edway);
        edway.addRef(this);
        setModified();
    }

    public void addInnerWay(EdWay edway) {
        checkEditable();
        if (edway == null)
            throw new IllegalArgumentException();
        if (!this.getEditor().ownedByEditor(edway))
            throw new IllegalArgumentException(tr("EdWay from a different WayEditor"));

        if (m_outerWays.contains (edway) || m_innerWays.contains (edway))
            throw new IllegalArgumentException(tr("EdWay is already member of the multipolygon"));

        m_innerWays.add(edway);
        edway.addRef(this);
        setModified();
    }

    public boolean removeOuterWay(EdWay edway) {
        checkEditable();
        if (edway == null)
            throw new IllegalArgumentException();
        if (!m_outerWays.remove(edway))
            return false;

        edway.removeRef(this);
        setModified();
        return true;
    }

    public boolean removeInnerWay(EdWay edway) {
        checkEditable();
        if (edway == null)
            throw new IllegalArgumentException();
        if (!m_innerWays.remove(edway))
            return false;

        edway.removeRef(this);
        setModified();
        return true;
    }

    public void removeAllWays() {
        checkEditable();

        if (m_outerWays.isEmpty() && m_innerWays.isEmpty())
            return;

        for (EdWay way: m_outerWays)
            way.removeRef(this);
        m_outerWays.clear();

        for (EdWay way: m_innerWays)
            way.removeRef(this);
        m_innerWays.clear();

        setModified();
    }



    public Relation originalMultipolygon() {
        if (!hasOriginal())
            throw new IllegalStateException(tr("EdMultipolygon has no original Relation"));
        return (Relation)originalPrimitive();
    }

    public Relation finalMultipolygon() {
//         checkNotDeleted();
        if (isFinalized())
            return m_relation;

        setFinalized();
        if (hasOriginal() && (!isModified() || isDeleted())) {
            m_relation = originalMultipolygon();
        }
        else {
            Relation fin = new Relation(m_relation);
            for (EdWay ew: m_outerWays)
                fin.addMember(new RelationMember ("outer", ew.finalReferenceableWay()));
            for (EdWay ew: m_innerWays)
                fin.addMember(new RelationMember ("inner", ew.finalReferenceableWay()));
            m_relation = fin;
        }
        return m_relation;
    }

    @Override
    protected void updateModifiedFlag() {
        checkEditable();
        if (!hasOriginal() || isDeleted() || !isModified())
            return;
        Relation orig = originalMultipolygon();

        if (orig.getUniqueId() != m_relation.getUniqueId())
            return;
        if (!hasIdenticalKeys(orig))
            return;
        if (orig.getMembersCount() != m_outerWays.size() + m_innerWays.size())
            return;

        for (RelationMember member: orig.getMembers()) {
            if (!member.isWay() || !member.hasRole())
                throw new AssertionError("Original relation changed since ctor checks!");
            switch (member.getRole()) {
                case "outer":
                    if (!containsWayWithUniqueId(m_outerWays, member.getWay().getUniqueId()))
                        return;
                    break;
                case "inner":
                    if (!containsWayWithUniqueId(m_innerWays, member.getWay().getUniqueId()))
                        return;
                    break;
                default:
                    throw new AssertionError("Original relation changed since ctor checks!");
            }
        }

        resetModified();
    }

    @Override
    protected void deleteContentsShallow() {
        this.removeAllWays();
        this.m_relation.removeAll();
    }

    private boolean containsWayWithUniqueId(List<EdWay> ways, long id) {
        for (EdWay w: ways) {
            if (w.getUniqueId() == id)
                return true;
        }
        return false;
    }

    @Override
    public void setKeys(Map<String,String> keys) {
        checkEditable();
        String type = keys.get("type");
        if (type != null && !type.equals("multipolygon"))
            throw new IllegalArgumentException(tr("Multipolygon must have type=multipolygon"));
        m_relation.setKeys(keys);
        if (type == null)
            m_relation.put("type", "multipolygon");
        setModified();
    }

    public List<EdWay> outerWays() {
        checkEditable();
        return new ArrayList<>(m_outerWays);
    }

    public List<EdWay> innerWays() {
        checkEditable();
        return new ArrayList<>(m_innerWays);
    }

    public List<EdWay> allWays() {
        checkEditable();
        List<EdWay> list = new ArrayList<>(m_outerWays.size() + m_innerWays.size());
        for (EdWay w: m_outerWays)
            list.add(w);
        for (EdWay w: m_innerWays)
            list.add(w);
        return list;
    }

    @Override
    public Set<EdWay> getAllWays() {
        checkEditable();
        Set<EdWay> result = new HashSet<> ();
        for (EdWay w: m_outerWays)
            result.add(w);
        for (EdWay w: m_innerWays)
            result.add(w);
        return result;
    }

    public boolean replaceWay(EdWay src, EdWay dst) {
        checkEditable();

        int i = m_outerWays.indexOf(src);
        if (i >= 0) {
            src.removeRef(this);
            m_outerWays.set(i, dst);
            dst.addRef(this);
            setModified();
            System.out.println("Replacing EdWay " + Long.toString(src.getUniqueId()) + " with " + Long.toString(dst.getUniqueId()) + " in relation " + Long.toString(this.getUniqueId()));
            return true;
        }

        i = m_innerWays.indexOf(src);
        if (i >= 0) {
            src.removeRef(this);
            m_innerWays.set(i, dst);
            dst.addRef(this);
            setModified();
            return true;
        }

        return false;
    }

    public boolean containsWay(EdWay way) {
        checkEditable();
        for (EdWay w: m_outerWays)
            if (w == way)
                return true;
        for (EdWay w: m_innerWays)
            if (w == way)
                return true;
        return false;
    }

    public boolean containsNonClosedWays() {
        checkEditable();
        for (EdWay w: m_outerWays)
            if (!w.isClosed())
                return true;
        for (EdWay w: m_innerWays)
            if (!w.isClosed())
                return true;
        return false;
    }

    public boolean containsTaggedWays() {
        checkEditable();
        for (EdWay w: m_outerWays)
            if (w.isTagged())
                return true;
        for (EdWay w: m_innerWays)
            if (w.isTagged())
                return true;
        return false;
    }

    public boolean allWaysHaveSingleReferrer() {
        checkEditable();
        for (EdWay w: m_outerWays)
            if (!w.hasSingleReferrer(this))
                return false;
        for (EdWay w: m_innerWays)
            if (!w.hasSingleReferrer(this))
                return false;
        return true;
    }

    @Override
    protected OsmPrimitive currentPrimitive() {
        return m_relation;
    }

    @Override
    public BBox getBBox() {
        checkNotDeleted();
        if (isFinalized())
            return m_relation.getBBox();

        BBox box = null;
        for (EdWay w: m_outerWays) {
            if (box == null)
                box = w.getBBox();
            else
                box.add(w.getBBox());
        }

        for (EdWay w: m_innerWays) {
            if (box == null)
                box = w.getBBox();
            else
                box.add(w.getBBox());
        }

        if (box == null)
            throw new IllegalStateException("EdMultipolygon contains no ways");
        return box;
    }

    @Override
    public boolean reuseExistingNodes(IEdNodePredicate filter) {
        checkEditable();
        boolean r = false;

        for (EdWay way: m_outerWays)
            if (way.reuseExistingNodes(filter))
                r = true;
        for (EdWay way: m_innerWays)
            if (way.reuseExistingNodes(filter))
                r = true;

        return r;
    }

    @Override
    public boolean reuseNearNodes(IReuseNearNodePredicate reuse, IEdNodePredicate nodes_filter) {
        checkEditable();
        boolean r = false;

        // never try to reuse near nodes across multipolygon ways
        IEdNodePredicate exclude_my_nodes = new ExcludeEdNodesPredicate(this);
        IEdNodePredicate filter = new EdNodeLogicalAndPredicate(exclude_my_nodes, nodes_filter);

        for (EdWay way: m_outerWays)
            if (way.reuseNearNodes(reuse, filter))
                r = true;
        for (EdWay way: m_innerWays)
            if (way.reuseNearNodes(reuse, filter))
                r = true;

        return r;
    }

    /**
     * Add all existing nodes that touch this multipolygon (i.e. are very close to
     * any of its ways' segments) and satisfy given predicate.
     * Nodes are added to the right positions into way segments.
     *
     * This function doesn't impose any additional restrictions to matching nodes,
     * except those provided by "filter" predicate.
     *
     * @param filter EdNode predicate to filter existing nodes
     * @return true if any existing nodes were connected, false otherwise
     */
    @Override
    public boolean connectExistingTouchingNodes(GeomDeviation tolerance, IEdNodePredicate filter) {
        checkEditable();
        boolean r = false;

        for (EdWay way: m_outerWays)
            if (way.connectExistingTouchingNodes(tolerance, filter))
                r = true;
        for (EdWay way: m_innerWays)
            if (way.connectExistingTouchingNodes(tolerance, filter))
                r = true;

        return r;
    }

    @Override
    public double getEastNorthArea() {
        checkEditable();

        double area = 0.0;

        for (EdWay way: m_outerWays) {
            area += way.getEastNorthArea();
        }

        for (EdWay way: m_innerWays) {
            area -= way.getEastNorthArea();
        }

        return area;
    }

    public void removeTagsFromWaysIfNeeded() {
        checkEditable();
        Map<String, String> values = this.getKeys();

        Set<String> conflicting_keys = new HashSet<>();

        for (EdWay way: outerWays()) {
            for (String key : way.keySet()) {
                if (!values.containsKey(key)) { //relation values take precedence
                    values.put(key, way.get(key));
                } else if (!this.hasKey(key) && !values.get(key).equals(way.get(key))) {
                    conflicting_keys.add(key);
                }
            }
        }

        // filter out empty key conflicts - we need second iteration
        if (!Config.getPref().getBoolean("multipoly.alltags", false))
            for (EdWay way: outerWays())
                for (String key : values.keySet())
                    if (!way.hasKey(key) && !this.hasKey(key))
                        conflicting_keys.add(key);

        for (String key : conflicting_keys)
            values.remove(key);

        for (String linear_tag : Config.getPref().getList("multipoly.lineartagstokeep", Arrays.asList(new String[] {"barrier", "source"})))
            values.remove(linear_tag);

        if ("coastline".equals(values.get("natural")))
            values.remove("natural");

        values.put("area", "yes");

        boolean move_tags = Config.getPref().getBoolean("multipoly.movetags", true);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            for (EdWay way : innerWays()) {
                if (value.equals(way.get(key))) {
                    way.remove(key);
                }
            }

            if (move_tags) {
                // remove duplicated tags from outer ways
                for (EdWay way : outerWays()) {
                    if(way.hasKey(key)) {
                        way.remove(key);
                    }
                }
            }
        }

        if (move_tags) {
            // add those tag values to the relation
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                if (!this.hasKey(key) && !"area".equals(key)) {
                    this.put(key, entry.getValue());
                }
            }
        }
    }

    @Override
    public Set<EdNode> getAllNodes() {
        checkEditable();
        Set<EdNode> s = new HashSet<>();

        for (EdWay way: m_outerWays)
            s.addAll(way.getAllNodes());
        for (EdWay way: m_innerWays)
            s.addAll(way.getAllNodes());

        return s;
    }

    @Override
    public boolean isInsideBounds(List<Bounds> bounds, LatLonSize oversize) {
        checkEditable();

        for (EdWay way: m_outerWays)
            if (!way.isInsideBounds(bounds, oversize))
                return false;
        for (EdWay way: m_innerWays)
            if (!way.isInsideBounds(bounds, oversize))
                return false;

        return true;
    }

    @Override
    public boolean connectNonIncludedTouchingNodes(GeomDeviation tolerance, EdObject obj) {
        if (this == obj)
            return false;
        checkEditable();
        boolean result = false;

        for (EdWay way: m_outerWays)
            if (way.connectNonIncludedTouchingNodes(tolerance, obj))
                result = true;
        for (EdWay way: m_innerWays)
            if (way.connectNonIncludedTouchingNodes(tolerance, obj))
                result = true;

        return result;
    }

    @Override
    public EdWay getFirstOuterWay () {
        checkEditable ();
        if (m_outerWays.isEmpty())
            throw new IllegalStateException(tr("EdMultipolygon has no outer way"));
        return m_outerWays.get(0);
    }
}
