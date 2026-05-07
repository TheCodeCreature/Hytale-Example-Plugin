package com.UnobstructedThirdPerson.placeblock.ui.ingredienttree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IngredientResourceType implements IngredientTreeNode {

    private final String resourceTypeId;
    private final String displayName;
    private final String iconPath;
    private final IngredientGroup parent;
    private final List<IngredientExactItem> children;

    public IngredientResourceType(
            @Nonnull String resourceTypeId,
            @Nonnull String displayName,
            @Nullable String iconPath,
            @Nonnull IngredientGroup parent,
            @Nonnull List<IngredientExactItem> children) {
        this.resourceTypeId = resourceTypeId;
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.parent = parent;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    @Override
    public String getId() {
        return resourceTypeId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    @Nullable
    public String getIconPath() {
        return iconPath;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.RESOURCE_TYPE;
    }

    public IngredientGroup getParent() {
        return parent;
    }

    public List<IngredientExactItem> getChildren() {
        return children;
    }

    public int getChildCount() {
        return children.size();
    }
}
