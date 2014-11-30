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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.CreateMultipolygonAction;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import static org.openstreetmap.josm.tools.I18n.tr;


public abstract class EdObject {
    private Object m_refs;
    private final WayEditor m_editor;
    private OsmPrimitive m_original;
    private boolean m_modified;
    private boolean m_deleted;
    private boolean m_finalized;

    protected EdObject (WayEditor editor, OsmPrimitive original) {
        m_refs = null;
        m_editor = editor;
        m_original = original;
        m_modified = false;
        m_deleted = false;
        m_finalized = false;
    }

    public WayEditor getEditor() {
        return m_editor;
    }

    protected void setModified() {
        m_modified = true;
    }

    protected void setFinalized() {
        m_finalized = true;
    }

    protected void resetModified() {
        m_modified = false;
    }

    public boolean isFinalized() {
        return m_finalized;
    }

    protected final void forgeOriginalDangerous(OsmPrimitive orig) {
        if (m_original != null)
            throw new IllegalStateException("Cannot forge OsmPrimitive, EdObject already has original primitive");
        m_original = orig;
    }

    public void deleteShallow() {
        checkEditable();
        if (this.hasReferrers())
            throw new IllegalStateException("Cannot delete referenced EdObject");
        this.deleteContentsShallow();
        m_deleted = true;
    }

    protected abstract void deleteContentsShallow();

    public boolean isDeleted() {
        return m_deleted;
    }

    public boolean isModified() {
        return m_modified;
    }

    public void addRef(EdObject ref) {
        if (m_refs == null) {
            m_refs = ref;
            return;
        }
        if (m_refs instanceof EdObject) {
            if (m_refs != ref)
                m_refs = new EdObject[] { (EdObject)m_refs, ref };
            return;
        }
        for (EdObject r: (EdObject[]) m_refs) {
            if (r == ref)
                return;
        }

        EdObject[] refs = (EdObject[]) m_refs;
        EdObject[] nrefs = new EdObject[refs.length + 1];
        System.arraycopy(refs, 0, nrefs, 0, refs.length);
        nrefs[refs.length] = ref;
        m_refs = nrefs;
    }

    public void removeRef(EdObject ref) {
        if (m_refs == null) {
            return;
        }
        if (m_refs instanceof EdObject) {
            if (m_refs == ref)
                m_refs = null;
            return;
        }
        EdObject[] refs = (EdObject[])m_refs;

        int idx = -1;
        for (int i = 0; i < refs.length; i++) {
            if (refs[i] == ref) {
                idx = i;
                break;
            }
        }

        if (idx == -1)
            return;

        if (refs.length == 2) {
            m_refs = refs[1-idx];
            return;
        }

        EdObject[] nrefs = new EdObject[refs.length - 1];
        System.arraycopy(refs, 0, nrefs, 0, idx);
        System.arraycopy(refs, idx + 1, nrefs, idx, nrefs.length - idx);
        m_refs = nrefs;
    }

    protected OsmPrimitive originalPrimitive() {
        return m_original;
    }

    protected abstract OsmPrimitive currentPrimitive();

    public boolean hasEditorReferrers() {
        return m_refs != null;
    }

    public boolean hasOriginal() {
        return m_original != null;
    }

    protected void checkEditable() {
        checkNotFinalized();
        checkNotDeleted();
    }

    protected void checkNotFinalized() {
        if (isFinalized())
            throw new IllegalStateException(tr("EdObject is finalized"));
    }

    protected void checkNotDeleted() {
        if (isDeleted())
            throw new IllegalStateException(tr("EdObject is deleted"));
    }

    public boolean matches(Match m) {
        checkNotDeleted();
        return m.match(currentPrimitive());
    }

    public boolean isTagged() {
        checkNotDeleted();
        return currentPrimitive().isTagged();
    }

    public final String get(String key) {
        return currentPrimitive().get(key);
    }

    public final void put(String key, String value) {
        checkEditable();
        currentPrimitive().put(key, value);
        setModified();
    }

    public final void remove(String key) {
        checkEditable();
        currentPrimitive().remove(key);
        setModified();
    }

    public void setKeys(Map<String,String> keys) {
        checkEditable();
        currentPrimitive().setKeys(keys);
        setModified();
    }

    public final Map<String,String> getKeys() {
        checkNotDeleted();
        return new HashMap<> (currentPrimitive().getKeys());
    }

    public final Map<String, String> getInterestingKeys() {
        checkNotDeleted();
        return new HashMap<> (currentPrimitive().getInterestingTags());
    }

    public final Collection<String> keySet() {
        checkNotDeleted();
        return currentPrimitive().keySet();
    }

    public final boolean hasKey(String key) {
        checkNotDeleted();
        return currentPrimitive().hasKey(key);
    }


    protected boolean hasIdenticalKeys(OsmPrimitive other) {
        checkEditable();
        OsmPrimitive cur = currentPrimitive();
        if (cur.hasKeys() != other.hasKeys())
            return false;
        return cur.getKeys().equals(other.getKeys());
    }

    abstract void updateModifiedFlag();

    /**
     * Checks if the given EdObject is the only one referrer of this EdObject.
     * That is, if the object has only this given editor referrer and no
     * other external or editor referrers.
     * @param referrer referrer to test
     * @return true if the referrer is the only referrer of this EdObject
     */
    public boolean hasSingleReferrer(EdObject referrer) {
        if (m_refs == null)
            return false;
        if (m_refs instanceof EdObject[])
            return false;
        if ((EdObject)m_refs != referrer)
            return false;
        if (this.hasExternalReferrers())
            return false;
        return true;
    }

    public <T extends EdObject> List<T> getEditorReferrers(Class<T> type) {
        if (m_refs == null)
            return Collections.emptyList();
        if (m_refs instanceof EdObject) {
            if (!type.isInstance(m_refs))
                return Collections.emptyList();

            List<T> result = new ArrayList<>(1);
            result.add(type.cast(m_refs));
            return result;
        }

        EdObject[] refs = (EdObject[])m_refs;
        List<T> result = new ArrayList<> ();
        for (EdObject obj: refs) {
            if (type.isInstance(obj)) {
                result.add(type.cast(obj));
            }
        }
        return result;
    }

    public <T extends EdObject> boolean hasEditorReferrers(Class<T> type) {
        List<T> objs = getEditorReferrers(type);
        return objs.isEmpty();
    }

    public <T extends OsmPrimitive> List<T> getExternalReferrers(Class<T> type) {

        if (!hasOriginal())
            return Collections.emptyList();

        List<OsmPrimitive> parents = m_original.getReferrers();
        List<T> result = new ArrayList<> ();
        for (OsmPrimitive parent: parents) {
            if (!type.isInstance(parent))
                continue;
            if (getEditor().isEdited(parent))
                continue;
            result.add(type.cast(parent));
        }
        return result;
    }

    public boolean hasExternalReferrers () {
        if (!hasOriginal())
            return false;

        List<OsmPrimitive> parents = m_original.getReferrers();
        for (OsmPrimitive parent: parents) {
            if (!getEditor().isEdited(parent))
                return true;
        }
        return false;
    }

    public boolean hasReferrers() {
        return hasEditorReferrers() || hasExternalReferrers();
    }

    public abstract BBox getBBox();

    public BBox getBBox(double oversize) {
        BBox box = this.getBBox();
        BBoxUtils.extendBBox(box, oversize);
        return box;
    }

    public long getUniqueId() {
        checkNotDeleted();
        return currentPrimitive().getUniqueId();
    }

    public void moveAllNonLinearTagsFrom(EdObject src) {
        checkNotDeleted();

        Map<String, String> values = src.getKeys();

        for (String linear_tag: Main.pref.getCollection("multipoly.lineartagstokeep", CreateMultipolygonAction.DEFAULT_LINEAR_TAGS))
            values.remove(linear_tag);

        if ("coastline".equals(values.get("natural")))
            values.remove("natural");

        for (Map.Entry<String, String> entry : values.entrySet()) {
            this.put (entry.getKey(), entry.getValue());
            src.remove (entry.getKey());
        }
    }

    public abstract Set<EdNode> getAllNodes();
    public abstract boolean reuseExistingNodes(IEdNodePredicate filter);
    public abstract boolean reuseNearNodes(IReuseNearNodePredicate reuse, IEdNodePredicate filter);
    public abstract boolean connectExistingTouchingNodes(GeomDeviation tolerance, IEdNodePredicate filter);
    public abstract double getEastNorthArea();
}

