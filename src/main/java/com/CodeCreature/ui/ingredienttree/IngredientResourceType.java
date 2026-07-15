package com.CodeCreature.ui.ingredienttree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IngredientResourceType implements IngredientTreeNode {

    private final String resourceTypeId;
    private final String displayName;
    private final String iconPath;
    private final String displayItemId;
    private final boolean directItem;
    private final IngredientGroup parent;
    private final List<IngredientExactItem> children;

    public IngredientResourceType(
            @Nonnull String resourceTypeId,
            @Nonnull String displayName,
            @Nullable String iconPath,
            @Nullable String displayItemId,
            boolean directItem,
            @Nonnull IngredientGroup parent,
            @Nonnull List<IngredientExactItem> children) {
        this.resourceTypeId = resourceTypeId;
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.displayItemId = displayItemId;
        this.directItem = directItem;
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

    @Nullable
    public String getDisplayItemId() {
        return displayItemId;
    }

    /**
     * Returns {@code true} if this node represents a direct item (resolved via
     * {@code Item.getIcon()}), as opposed to a resource-type group node.
     *
     * <p>Use this flag — not {@code iconPath == null} — to distinguish direct-item
     * leaf nodes from resource-type nodes whose icon could not be resolved.
     */
    public boolean isDirectItem() {
        return directItem;
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
