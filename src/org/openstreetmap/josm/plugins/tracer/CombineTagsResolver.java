package org.openstreetmap.josm.plugins.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.TagCollection;
import org.openstreetmap.josm.gui.conflict.tags.CombinePrimitiveResolverDialog;
import org.openstreetmap.josm.gui.conflict.tags.TagConflictResolutionUtil;

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

        // Setup collection of all tags
        TagCollection tagsOfPrimitives = TagCollection.from(old_keys);
        tagsOfPrimitives.add(TagCollection.from(new_keys));

        // Create faked primitives for resolution functions
        OsmPrimitive prim1 = new Node();
        OsmPrimitive prim2 = new Node();
        prim1.setKeys(old_keys);
        prim2.setKeys(new_keys);
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
            if (dialog.isCanceled()) {
                return null;
            }
        }

        // Create final set
        Map<String, String> result = new HashMap<>(new_keys);
        TagCollection resolution = dialog.getTagConflictResolverModel().getAllResolutions();
        for (Tag tag: resolution) {
            result.put(tag.getKey(), tag.getValue());
        }

        return result;
    }
}
