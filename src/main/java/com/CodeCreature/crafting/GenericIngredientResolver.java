package com.CodeCreature.crafting;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

public interface GenericIngredientResolver {

    @Nonnull
    GenericIngredientResolution resolve(@Nonnull MaterialQuantity input, boolean preferNatural);

    @Nonnull
    default List<GenericIngredientResolution> resolveAll(@Nonnull List<MaterialQuantity> inputs,
                                                         boolean preferNatural) {
        List<GenericIngredientResolution> resolved = new ArrayList<>(inputs.size());
        for (MaterialQuantity input : inputs) {
            if (input != null) {
                resolved.add(resolve(input, preferNatural));
            }
        }
        return List.copyOf(resolved);
    }
}
