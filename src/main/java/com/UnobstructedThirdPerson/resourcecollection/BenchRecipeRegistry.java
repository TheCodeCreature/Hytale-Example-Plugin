package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.BenchRequirement;
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
 * Registry of crafting recipes for a single workbench, identified by its
 * {@link BenchRequirement#id} (e.g. {@code "Builders"}, {@code "Furniture_Bench"}).
 *
 * <p>A recipe is included if:
 * <ol>
 *   <li>It has a non-null primaryOutput with an itemId</li>
 *   <li>The output item has a blockId (it produces a placeable block)</li>
 *   <li>The recipe ID does NOT start with "Salvage"</li>
 *   <li>At least one {@link BenchRequirement} has an {@code id} matching this
 *       registry's {@code benchId}</li>
 * </ol>
 *
 * <p>Instances are created and managed by {@link BenchRecipeRegistries}.
 * Built once after assets load; queried by {@code DropScaler},
 * {@code RecipeDropListener}, and {@code NaturalResourceRegistry}.
 */
public final class BenchRecipeRegistry {

    private final String benchId;

    /** blockTypeId → first matching CraftingRecipe */
    private Map<String, CraftingRecipe> recipesByBlockType = Collections.emptyMap();

    /** recipe ID → CraftingRecipe (all matching recipes for this bench) */
    private Map<String, CraftingRecipe> recipesById = Collections.emptyMap();

    /** recipe IDs whose inputs are all natural resources ("base block" recipes) */
    private Set<String> baseBlockRecipeIds = Collections.emptySet();

    public BenchRecipeRegistry(@Nonnull String benchId) {
        if (benchId == null) throw new NullPointerException("benchId must not be null");
        this.benchId = benchId;
    }

    @Nonnull
    public String getBenchId() {
        return benchId;
    }

    public void init() {
        Map<String, CraftingRecipe> byBlock = new HashMap<>();
        Map<String, CraftingRecipe> byId = new HashMap<>();

        for (var entry : CraftingRecipe.getAssetMap().getAssetMap().entrySet()) {
            CraftingRecipe recipe = entry.getValue();
            if (recipe == null) continue;
            if (recipe.getId().startsWith("Salvage")) continue;
            if (!hasBenchId(recipe, this.benchId)) continue;
            if (recipe.getPrimaryOutput() == null) continue;
            String outputItemId = recipe.getPrimaryOutput().getItemId();
            if (outputItemId == null) continue;

            Item item = Item.getAssetMap().getAsset(outputItemId);
            if (item == null) continue;
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
        for (var e : byId.entrySet()) {
            if (allInputsNatural(e.getValue(), naturalItems)) {
                baseIds.add(e.getKey());
            }
        }
        baseBlockRecipeIds = Collections.unmodifiableSet(baseIds);

        log("[" + benchId + "] Initialized: " + byBlock.size() + " block recipes, "
                + byId.size() + " total, " + baseIds.size() + " base block recipes");
    }

    @Nullable
    public CraftingRecipe getRecipeForBlock(@Nonnull String blockTypeId) {
        return recipesByBlockType.get(blockTypeId);
    }

    public boolean hasRecipe(@Nonnull String blockTypeId) {
        return recipesByBlockType.containsKey(blockTypeId);
    }

    public Map<String, CraftingRecipe> getAllRecipesById() {
        return recipesById;
    }

    public Map<String, CraftingRecipe> getAllRecipesByBlockType() {
        return recipesByBlockType;
    }

    public boolean isBaseBlockRecipe(@Nonnull String recipeId) {
        return baseBlockRecipeIds.contains(recipeId);
    }

    public boolean isBaseBlockType(@Nonnull String blockTypeId) {
        CraftingRecipe recipe = recipesByBlockType.get(blockTypeId);
        return recipe != null && baseBlockRecipeIds.contains(recipe.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Bench matching
    // ═══════════════════════════════════════════════════════════════

    private static boolean hasBenchId(@Nonnull CraftingRecipe recipe, @Nonnull String benchId) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return false;
        for (BenchRequirement req : reqs) {
            if (req != null && benchId.equals(req.id)) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Input resolution (static utilities — shared across instances)
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    public static String resolveInputItemId(@Nonnull MaterialQuantity input) {
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

    /**
     * Resolves a resourceTypeId to a concrete item by scanning BlockGroups
     * for blocks whose name starts with the resource type prefix.
     *
     * <p>Specific types (e.g. {@code "Wood_Hardwood"}) match blocks starting
     * with {@code "Wood_Hardwood_"} — typically found in {@code FullBlocks_Hardwood}.
     *
     * <p>Wildcard types ending in {@code "_All"} (e.g. {@code "Wood_All"}) strip
     * the suffix and match any block starting with {@code "Wood_"} — picks the
     * first valid item across all groups.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static String resolveByBlockGroup(@Nonnull String resId) {
        var store = AssetRegistry.getAssetStore(BlockGroup.class);
        if (store == null) return null;
        DefaultAssetMap<String, BlockGroup> blockGroupMap =
                (DefaultAssetMap<String, BlockGroup>) store.getAssetMap();

        // "Wood_All" → match "Wood_"; "Wood_Hardwood" → match "Wood_Hardwood_"; "Rock" → match "Rock_"
        String blockPrefix = resId.endsWith("_All")
                ? resId.substring(0, resId.length() - "All".length())
                : resId + "_";

        for (var groupEntry : blockGroupMap.getAssetMap().entrySet()) {
            BlockGroup group = groupEntry.getValue();
            if (group == null) continue;
            for (int i = 0; i < group.size(); i++) {
                String blockId = group.get(i);
                if (blockId == null || !blockId.startsWith(blockPrefix)) continue;
                Item item = Item.getAssetMap().getAsset(blockId);
                if (item != null) return blockId;
                // Block ID may not be a direct item key — find item by blockId field
                for (Map.Entry<String, Item> e : Item.getAssetMap().getAssetMap().entrySet()) {
                    if (e.getValue() != null && blockId.equals(e.getValue().getBlockId())) {
                        return e.getKey();
                    }
                }
            }
        }
        return null;
    }

    private static boolean allInputsNatural(@Nonnull CraftingRecipe recipe,
                                            @Nonnull Set<String> naturalItems) {
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

    private static void log(String msg) {
        System.out.println("[BenchRecipeReg] " + msg);
    }
}
