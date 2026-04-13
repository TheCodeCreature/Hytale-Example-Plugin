package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Central registry of crafting recipes from builder's bench and furniture bench
 * (StructuralCrafting) that produce placeable blocks.
 * A recipe is included if:
 *   1. It has a non-null primaryOutput with an itemId
 *   2. The output item hasBlockType() (it's a placeable block)
 *   3. The recipe ID does NOT start with "Salvage"
 *   4. It requires a StructuralCrafting bench (builder's/furniture bench)
 *
 * Built once after assets load; queried by RecipeDropListener,
 * CraftingCostModifier, and RecipeDropModifier.
 */
public final class BlockRecipeRegistry {

    // blockTypeId -> first matching CraftingRecipe
    private static Map<String, CraftingRecipe> recipesByBlockType = Collections.emptyMap();
    // recipe ID -> CraftingRecipe (all matching recipes)
    private static Map<String, CraftingRecipe> recipesById = Collections.emptyMap();
    // recipe IDs of "base block" recipes whose inputs are all natural resources
    private static Set<String> baseBlockRecipeIds = Collections.emptySet();

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
            if (!isStructuralCrafting(recipe)) continue;
            if (recipe.getPrimaryOutput() == null) continue;
            String outputItemId = recipe.getPrimaryOutput().getItemId();
            if (outputItemId == null) continue;

            Item item = Item.getAssetMap().getAsset(outputItemId);
            if (item == null) continue;
            // Use getBlockId() instead of hasBlockType() — some items
            // (e.g. rails, doors) reference an external block definition
            // without having hasBlockType=true.
            String blockTypeId = item.getBlockId();
            if (blockTypeId == null || blockTypeId.isEmpty()) continue;
            byBlock.putIfAbsent(blockTypeId, recipe);
            byId.put(recipe.getId(), recipe);
        }

        recipesByBlockType = Collections.unmodifiableMap(byBlock);
        recipesById = Collections.unmodifiableMap(byId);

        // Classify base block recipes: all inputs resolve to natural resource items
        Set<String> baseIds = new HashSet<>();
        Set<String> naturalItems = NaturalResourceRegistry.getNaturalItemIds();
        for (var entry : byId.entrySet()) {
            CraftingRecipe recipe = entry.getValue();
            if (allInputsNatural(recipe, naturalItems)) {
                baseIds.add(entry.getKey());
            }
        }
        baseBlockRecipeIds = Collections.unmodifiableSet(baseIds);

        log("Initialized: " + byBlock.size() + " block recipes, "
                + byId.size() + " total, " + baseIds.size() + " base block recipes");
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

    /**
     * Returns true if the recipe requires a StructuralCrafting bench
     * (builder's bench or furniture bench).
     */
    private static boolean isStructuralCrafting(@Nonnull CraftingRecipe recipe) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return false;
        for (BenchRequirement req : reqs) {
            if (req != null && req.type == BenchType.StructuralCrafting) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if ALL of the recipe's inputs resolve to natural resource items.
     * These are "base block" recipes (e.g. logs → planks) that should keep 1x costs.
     */
    private static boolean allInputsNatural(@Nonnull CraftingRecipe recipe, @Nonnull Set<String> naturalItems) {
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) return false;
        for (MaterialQuantity mq : inputs) {
            if (mq == null) continue;
            String resolved = resolveInputItemId(mq);
            if (resolved == null || !naturalItems.contains(resolved)) {
                return false;
            }
        }
        return true;
    }

    /** Returns true if this recipe is a base block recipe (inputs are all natural). */
    public static boolean isBaseBlockRecipe(@Nonnull String recipeId) {
        return baseBlockRecipeIds.contains(recipeId);
    }

    /** Returns true if this block type's recipe is a base block recipe. */
    public static boolean isBaseBlockType(@Nonnull String blockTypeId) {
        CraftingRecipe recipe = recipesByBlockType.get(blockTypeId);
        return recipe != null && baseBlockRecipeIds.contains(recipe.getId());
    }

    @Nullable
    static String resolveInputItemId(@Nonnull MaterialQuantity input) {
        String itemId = input.getItemId();
        if (itemId != null && !"Empty".equals(itemId)) {
            Item item = Item.getAssetMap().getAsset(itemId);
            return item != null ? itemId : null;
        }
        String resId = input.getResourceTypeId();
        if (resId != null) {
            return resolveByBlockGroup(resId);
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static String resolveByBlockGroup(@Nonnull String resId) {
        int underscoreIdx = resId.indexOf('_');
        String groupName = underscoreIdx >= 0
                ? "FullBlocks" + resId.substring(underscoreIdx)
                : "FullBlocks_" + resId;
        DefaultAssetMap<String, BlockGroup> blockGroupMap =
                (DefaultAssetMap<String, BlockGroup>) AssetRegistry.getAssetStore(BlockGroup.class).getAssetMap();
        BlockGroup group = blockGroupMap.getAsset(groupName);
        if (group == null) return null;
        for (int i = 0; i < group.size(); i++) {
            String blockId = group.get(i);
            Item item = Item.getAssetMap().getAsset(blockId);
            if (item != null) return blockId;
        }
        for (int i = 0; i < group.size(); i++) {
            String blockId = group.get(i);
            for (Map.Entry<String, Item> e : Item.getAssetMap().getAssetMap().entrySet()) {
                if (e.getValue() != null && blockId.equals(e.getValue().getBlockId())) {
                    return e.getKey();
                }
            }
        }
        return null;
    }
}
