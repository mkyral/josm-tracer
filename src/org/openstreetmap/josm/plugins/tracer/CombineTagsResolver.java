package org.openstreetmap.josm.plugins.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.TagCollection;
import org.openstreetmap.josm.gui.conflict.tags.CombinePrimitiveResolverDialog;
import org.openstreetmap.josm.gui.conflict.tags.TagConflictResolutionUtil;

import static org.openstreetmap.josm.tools.I18n.*;

public abstract class CombineTagsResolver {

    @SuppressWarnings("deprecation")
    public static Map<String, String> launchIfNecessary(
            final Map<String, String> old_keys,
            final Map<String, String> new_keys) {

        // Note 1: this method was copied from CombinePrimitiveResolverDialog.launchIfNecessary() and
        // adapted to resolution of tag sets only. It's not possible to use the original launchIfNecessary()
        // because (a) it requires primitives occurring in DataSet, (b) it tries to resolve relation
        // memberships, (c) it builds a list of DataSet Commands instead of the resolved set of tags.

        // Note 2: this method relies on deprecated function CombinePrimitiveResolverDialog.getInstance().
        // The function was deprecated by introduction of CombinePrimitiveResolverDialog.launchIfNecessary()
        // but it's still available for public use.

        // Prepare tags - for collision tags put theirs source
        Map<String, String> m_old_keys = new HashMap<>(old_keys);
        Map<String, String> m_new_keys = new HashMap<>(new_keys);

        String m_spacer = "    .. ";
        String m_osm =    tr("/osm/");
        String m_traced = tr("/traced/");

        for (Entry<String, String> entry : old_keys.entrySet())
        {
            if (new_keys.containsKey(entry.getKey())) {
                // Both lists contains the same key
                if (new_keys.get(entry.getKey()).equals(entry.getValue())) {
                    // ... and values of this key are equal
                    m_old_keys.put(entry.getKey(), entry.getValue());
                    m_new_keys.put(entry.getKey(), entry.getValue());
                } else {
                    m_old_keys.put(entry.getKey(), entry.getValue() + m_spacer + m_osm);
                    m_new_keys.put(entry.getKey(), new_keys.get(entry.getKey()) + m_spacer + m_traced);
                }
            } else {
                // Unique key in old_keys
                m_old_keys.put(entry.getKey(), entry.getValue());
            }
        }

        // Finish update from new_keys
        for (Entry<String, String> entry : new_keys.entrySet())
        {
            if (!m_new_keys.containsKey(entry.getKey())) {
                m_new_keys.put(entry.getKey(), entry.getValue());
            }
        }
        // Setup collection of all tags
        TagCollection tagsOfPrimitives = TagCollection.from(m_old_keys);
        tagsOfPrimitives.add(TagCollection.from(m_new_keys));

        // Create faked primitives for resolution functions
        OsmPrimitive prim1 = new Node();
        OsmPrimitive prim2 = new Node();
        prim1.setKeys(m_old_keys);
        prim2.setKeys(m_new_keys);
        List<OsmPrimitive> primitives = new ArrayList<>(2);
        primitives.add(prim1);
        primitives.add(prim2);

        // Setup tag collections for resolution dialog
        final TagCollection completeWayTags = new TagCollection(tagsOfPrimitives);
        TagConflictResolutionUtil.combineTigerTags(completeWayTags);
        TagConflictResolutionUtil.normalizeTagCollectionBeforeEditing(completeWayTags, primitives);
        final TagCollection tagsToEdit = new TagCollection(completeWayTags);
        TagConflictResolutionUtil.completeTagCollectionForEditing(tagsToEdit);

        // Fake relations, we don't want to edit any relation memberships
        final Set<Relation> parentRelations = new HashSet<>();

        // Build conflict resolution dialog
        final CombinePrimitiveResolverDialog dialog = CombinePrimitiveResolverDialog.getInstance();

        dialog.getTagConflictResolverModel().populate(tagsToEdit, completeWayTags.getKeysWithMultipleValues());
        dialog.getRelationMemberConflictResolverModel().populate(parentRelations, primitives);
        dialog.prepareDefaultDecisions();
        dialog.setTargetPrimitive(null); // Use universal dialog title. Setting targetPrimitive to null seems to be safe if dialog.buildResolutionCommands() function isn't called.

        // Resolve tag conflicts if necessary
        if (!dialog.isResolvedCompletely()) {
            dialog.setVisible(true);
            if (!dialog.isApplied()) {
                return null;
            }
        }

        // Create final set
        Map<String, String> result = new HashMap<>(new_keys);
        TagCollection resolution = dialog.getTagConflictResolverModel().getAllResolutions();
        for (Tag tag: resolution) {
            result.put(tag.getKey(), tag.getValue().replaceFirst("^(.*)    \\.\\. "+ m_osm +"$","$1").replaceFirst("^(.*)    \\.\\. " + m_traced + "$","$1"));
        }

        return result;
    }
}
