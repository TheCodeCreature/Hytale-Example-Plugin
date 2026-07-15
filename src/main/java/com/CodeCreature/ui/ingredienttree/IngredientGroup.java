package com.CodeCreature.ui.ingredienttree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IngredientGroup implements IngredientTreeNode {

    private final String groupId;
    private final String displayName;
    private final String iconPath;
    private final String displayItemId;
    private final List<IngredientResourceType> children;

    public IngredientGroup(
            @Nonnull String groupId,
            @Nonnull String displayName,
            @Nullable String iconPath,
            @Nullable String displayItemId,
            @Nonnull List<IngredientResourceType> children) {
        this.groupId = groupId;
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.displayItemId = displayItemId;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    @Override
    public String getId() {
        return groupId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getIconPath() {
        return iconPath;
    }

    @Nullable
    public String getDisplayItemId() {
        return displayItemId;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.META_GROUP;
    }

    public List<IngredientResourceType> getChildren() {
        return children;
    }

    public int getChildCount() {
        return children.size();
    }
}
