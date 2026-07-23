package com.CodeCreature.crafting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * @node    IngredientPresentation
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Provides UI-facing ingredient display metadata that separates semantic generic identity
 *          from concrete compatibility fallbacks and optional generic icon paths.
 * @wave    4 (generic icons + generic affordability boundary)
 * @status  Wave 4 - implemented UI projection metadata with generic-icon support
 * @do-not  Use this record to encode planner or raw-cost policy.
 *          Assume iconItemId alone carries semantic meaning for generic ingredients.
 */
public record IngredientPresentation(
        @Nullable String itemId,
        @Nullable String iconItemId,
        @Nonnull String displayName,
        boolean semanticRepresentative,
        boolean genericMode,
        @Nullable String resourceTypeId,
        @Nullable String genericIconPath
) {
    public IngredientPresentation {
        Objects.requireNonNull(displayName, "displayName");
    }

    public IngredientPresentation(@Nullable String itemId,
                                  @Nullable String iconItemId,
                                  @Nonnull String displayName,
                                  boolean semanticRepresentative) {
        this(itemId, iconItemId, displayName, semanticRepresentative, false, null, null);
    }
}
