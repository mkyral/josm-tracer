package org.openstreetmap.josm.plugins.tracer.connectways;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.openstreetmap.josm.data.osm.OsmPrimitive;


public class EdMultipolygon extends EdObject {
    private Relation m_relation;
    private List<EdWay> m_outerWays;
    private List<EdWay> m_innerWays;

    EdMultipolygon (WayEditor editor) {
        super(editor, null);
        m_relation = new Relation();
        m_relation.put("type", "multipolygon");

        m_outerWays = new ArrayList<EdWay> ();
        m_innerWays = new ArrayList<EdWay> ();
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

    public Relation originalMultipolygon() {
        if (!hasOriginal())
            throw new IllegalStateException(tr("EdMultipolygon has no original Relation"));
        return (Relation)originalPrimitive();
    }

    public Relation finalMultipolygon() {
        checkNotDeleted();
        if (isFinalized())
            return m_relation;

        setFinalized();
        if (hasOriginal() && !isModified()) {
            m_relation = originalMultipolygon();
        }
        else {
            Relation fin = new Relation(m_relation);
            for (EdWay ew: m_outerWays)
                fin.addMember(new RelationMember ("outer", ew.finalWay()));
            for (EdWay ew: m_innerWays)
                fin.addMember(new RelationMember ("inner", ew.finalWay()));
            m_relation = fin;
        }
        return m_relation;
    }

    public void setKeys(Map<String,String> keys) {
        checkEditable();
        String type = keys.get("type");
        if (type != null && type != "multipolygon")
            throw new IllegalArgumentException(tr("Multipolygon must have type=multipolygon"));
        m_relation.setKeys(keys);
        if (type == null)
            m_relation.put("type", "multipolygon");
        setModified();
    }

    public List<EdWay> outerWays() {
        checkEditable(); // #### maybe support finalized multipolygons
        return new ArrayList<EdWay>(m_outerWays);
    }

    public List<EdWay> innerWays() {
        checkEditable(); // #### maybe support finalized multipolygons
        return new ArrayList<EdWay>(m_innerWays);
    }

    public List<EdWay> allWays() {
        checkEditable();
        List<EdWay> list = new ArrayList<EdWay>(m_outerWays.size() + m_innerWays.size());
        for (EdWay w: m_outerWays)
            list.add(w);
        for (EdWay w: m_innerWays)
            list.add(w);
        return list;
    }

    protected OsmPrimitive currentPrimitive() {
        return m_relation;
    }
}
