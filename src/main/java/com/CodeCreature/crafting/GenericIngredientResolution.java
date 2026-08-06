package com.CodeCreature.crafting;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record GenericIngredientResolution(
        @Nonnull GenericIngredientIdentity identity,
        @Nonnull List<String> orderedMatchingItemIds,
        @Nullable String representativeItemId,
        boolean requiresGenericMatching
) {
    public GenericIngredientResolution {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(orderedMatchingItemIds, "orderedMatchingItemIds");
        orderedMatchingItemIds = List.copyOf(orderedMatchingItemIds);
    }
}
