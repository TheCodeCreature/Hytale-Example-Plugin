package com.CodeCreature.crafting;

import javax.annotation.Nullable;

/**
 * @node    GenericIngredientIdentity
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Represents the authored ingredient identity at the generic boundary so affordability can
 *          remain ResourceTypeId-aware without collapsing semantics to one concrete variant.
 * @wave    4 (generic icons + generic affordability boundary)
 * @status  Wave 4 - implemented identity carrier for direct ingredient checks and UI projections
 * @do-not  Treat representative concrete items as semantic identity here.
 *          Add concrete removal-policy behavior to this identity type.
 */
public record GenericIngredientIdentity(
        @Nullable String itemId,
        @Nullable String resourceTypeId,
        int quantity
) {
}
