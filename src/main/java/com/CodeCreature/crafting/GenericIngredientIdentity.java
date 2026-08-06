package com.CodeCreature.crafting;

import javax.annotation.Nullable;

public record GenericIngredientIdentity(
        @Nullable String itemId,
        @Nullable String resourceTypeId,
        int quantity
) {
}
