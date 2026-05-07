package com.UnobstructedThirdPerson.placeblock.ui.ingredienttree;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IngredientExactItem implements IngredientTreeNode {

    private final String itemId;
    private final String displayName;
    private final IngredientResourceType parent;

    public IngredientExactItem(
            @Nonnull String itemId,
            @Nonnull String displayName,
            @Nonnull IngredientResourceType parent) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.parent = parent;
    }

    @Override
    public String getId() {
        return itemId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    @Nullable
    public String getIconPath() {
        return null;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.EXACT_ITEM;
    }

    public IngredientResourceType getParent() {
        return parent;
    }
}
