package com.CodeCreature.crafting;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
