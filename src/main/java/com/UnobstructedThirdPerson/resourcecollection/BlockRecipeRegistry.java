package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Central registry of crafting recipes that produce placeable blocks.
 * A recipe is included if:
 *   1. It has a non-null primaryOutput with an itemId
 *   2. The output item hasBlockType() (it's a placeable block)
 *   3. The recipe ID does NOT start with "Salvage"
 *
 * Built once after assets load; queried by RecipeDropListener and
 * CraftingCostModifier.
 */
public final class BlockRecipeRegistry {

    // blockTypeId -> first matching CraftingRecipe
    private static Map<String, CraftingRecipe> recipesByBlockType = Collections.emptyMap();
    // recipe ID -> CraftingRecipe (all matching recipes)
    private static Map<String, CraftingRecipe> recipesById = Collections.emptyMap();

    private BlockRecipeRegistry() {}

    private static void log(String msg) {
        System.out.println("[BlockRecipeReg] " + msg);
    }

    /**
     * Builds the registry. Must be called after assets are loaded.
     */
    public static void init() {
        Map<String, CraftingRecipe> byBlock = new HashMap<>();
        Map<String, CraftingRecipe> byId = new HashMap<>();

        for (var entry : CraftingRecipe.getAssetMap().getAssetMap().entrySet()) {
            CraftingRecipe recipe = entry.getValue();
            if (recipe == null) continue;
            if (recipe.getId().startsWith("Salvage")) continue;
            if (recipe.getPrimaryOutput() == null) continue;
            String outputItemId = recipe.getPrimaryOutput().getItemId();
            if (outputItemId == null) continue;

            Item item = Item.getAssetMap().getAsset(outputItemId);
            if (item == null || !item.hasBlockType()) continue;

            String blockTypeId = item.getBlockId();
            byBlock.putIfAbsent(blockTypeId, recipe);
            byId.put(recipe.getId(), recipe);
        }

        recipesByBlockType = Collections.unmodifiableMap(byBlock);
        recipesById = Collections.unmodifiableMap(byId);
        log("Initialized: " + byBlock.size() + " block recipes, "
                + byId.size() + " total recipes");
    }

    /** Returns the recipe for a block type, or null if none. */
    @Nullable
    public static CraftingRecipe getRecipeForBlock(@Nonnull String blockTypeId) {
        return recipesByBlockType.get(blockTypeId);
    }

    /** Returns true if the block type has a non-Salvage crafting recipe. */
    public static boolean hasRecipe(@Nonnull String blockTypeId) {
        return recipesByBlockType.containsKey(blockTypeId);
    }

    /** Returns all block-producing recipes keyed by recipe ID (read-only). */
    public static Map<String, CraftingRecipe> getAllRecipesById() {
        return recipesById;
    }

    /** Returns all block-producing recipes keyed by block type ID (read-only). */
    public static Map<String, CraftingRecipe> getAllRecipesByBlockType() {
        return recipesByBlockType;
    }
}
