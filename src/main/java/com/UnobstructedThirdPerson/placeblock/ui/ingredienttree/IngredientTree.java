package com.UnobstructedThirdPerson.placeblock.ui.ingredienttree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IngredientTree {

    private final List<IngredientGroup> groups;
    private final Map<String, IngredientTreeNode> nodeIndex;

    public IngredientTree(
            @Nonnull List<IngredientGroup> groups,
            @Nonnull Map<String, IngredientTreeNode> nodeIndex) {
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        this.nodeIndex = Collections.unmodifiableMap(new HashMap<>(nodeIndex));
    }

    public List<IngredientGroup> getGroups() {
        return groups;
    }

    @Nullable
    public IngredientTreeNode findNode(String id) {
        return nodeIndex.get(id);
    }

    public Set<String> getAllResourceTypeIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (IngredientGroup group : groups) {
            for (IngredientResourceType rt : group.getChildren()) {
                if (rt.getIconPath() != null) {
                    ids.add(rt.getId());
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public Set<String> getAllDirectItemIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (IngredientGroup group : groups) {
            for (IngredientResourceType rt : group.getChildren()) {
                if (rt.getIconPath() == null) {
                    ids.add(rt.getId());
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public Set<String> getAllExactItemIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (IngredientGroup group : groups) {
            for (IngredientResourceType rt : group.getChildren()) {
                for (IngredientExactItem item : rt.getChildren()) {
                    ids.add(item.getId());
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public int getTotalNodeCount() {
        return nodeIndex.size();
    }
}
