package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

        for (FilteredRecipeEntry entry : RecipeFilterRegistry.getEntriesForBench(this.benchId)) {
            byBlock.putIfAbsent(entry.blockTypeId(), entry.recipe());
            byId.put(entry.recipeId(), entry.recipe());
        }

        recipesByBlockType = Collections.unmodifiableMap(byBlock);
        recipesById = Collections.unmodifiableMap(byId);

        log("[" + benchId + "] Initialized: " + byBlock.size() + " block recipes, "
                + byId.size() + " total");
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

    private static void log(String msg) {
        System.out.println("[BenchRecipeReg] " + msg);
    }
}
