package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Field;

/**
 * Multiplies all input quantities of block-producing recipes by
 * {@link ResourceConstants#RESOURCE_MULTIPLIER}.
 * Uses the same filter as {@link BlockRecipeRegistry}.
 * Must be called after {@link BlockRecipeRegistry#init()}.
 */
public final class CraftingCostModifier {

    private CraftingCostModifier() {}

    private static void log(String msg) {
        System.out.println("[CraftingCost] " + msg);
    }

    /**
     * Replaces every recipe's input array with a scaled copy where each
     * MaterialQuantity has its quantity multiplied by RESOURCE_MULTIPLIER.
     */
    public static void apply() {
        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;

        Field inputField;
        try {
            inputField = CraftingRecipe.class.getDeclaredField("input");
            inputField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find 'input' field on CraftingRecipe: " + e.getMessage());
            return;
        }

        int modified = 0;
        for (var entry : BlockRecipeRegistry.getAllRecipesById().entrySet()) {
            String recipeId = entry.getKey();
            CraftingRecipe recipe = entry.getValue();

            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length == 0) continue;

            MaterialQuantity[] scaled = new MaterialQuantity[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                MaterialQuantity mq = inputs[i];
                if (mq == null) {
                    scaled[i] = null;
                    continue;
                }
                int newQty = mq.getQuantity() * multiplier;
                scaled[i] = mq.clone(newQty);
            }

            try {
                inputField.set(recipe, scaled);
                modified++;
            } catch (IllegalAccessException e) {
                log("ERROR: Could not set input for recipe " + recipeId + ": " + e.getMessage());
            }
        }

        log("Scaled input costs (" + multiplier + "x) for " + modified + " block recipes");
    }
}
