package com.CodeCreature.crafting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * @node    GenericIngredientResolution
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Captures one ingredient's typed generic resolution result, preserving ordered matching
 *          concrete variants and a compatibility representative for legacy consumers.
 * @wave    4 (generic icons + generic affordability boundary)
 * @status  Wave 4 - implemented typed resolution payload used by affordability and planner paths
 * @do-not  Imply engine removeMaterials variant-consumption ordering from this payload.
 *          Change the meaning of orderedMatchingItemIds away from index order.
 */
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
