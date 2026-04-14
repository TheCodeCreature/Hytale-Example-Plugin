package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static coordinator that manages all {@link BenchRecipeRegistry} instances.
 * Provides aggregate queries across all registered benches — most consumers
 * should call methods here rather than on individual registries.
 *
 * <p>Initialized once by {@link DropScaler#apply()} with the configured bench
 * IDs. The bench IDs correspond to the {@code BenchRequirement.id} field in
 * Hytale's recipe assets (e.g. {@code "Builders"}, {@code "Furniture_Bench"}).
 *
 * <p>Iteration order of registries is preserved (insertion order) and determines
 * priority when the same block type has recipes in multiple benches —
 * {@link #getRecipeForBlock(String)} returns the first match.
 */
public final class BenchRecipeRegistries {

    /** Ordered map: bench ID → registry instance. Insertion order = priority order. */
    private static Map<String, BenchRecipeRegistry> registries = Collections.emptyMap();

    private BenchRecipeRegistries() {}

    public static void init(@Nonnull String... benchIds) {
        Map<String, BenchRecipeRegistry> map = new LinkedHashMap<>();
        int totalRecipes = 0;
        for (String benchId : benchIds) {
            BenchRecipeRegistry registry = new BenchRecipeRegistry(benchId);
            registry.init();
            map.put(benchId, registry);
            totalRecipes += registry.getAllRecipesById().size();
        }
        registries = Collections.unmodifiableMap(map);
        log("Initialized " + map.size() + " bench registries with "
                + totalRecipes + " total recipes");
    }

    @Nullable
    public static BenchRecipeRegistry getRegistry(@Nonnull String benchId) {
        return registries.get(benchId);
    }

    public static Collection<BenchRecipeRegistry> getAllRegistries() {
        return registries.values();
    }

    public static boolean hasRecipeAnywhere(@Nonnull String blockTypeId) {
        for (BenchRecipeRegistry reg : registries.values()) {
            if (reg.hasRecipe(blockTypeId)) return true;
        }
        return false;
    }

    @Nullable
    public static CraftingRecipe getRecipeForBlock(@Nonnull String blockTypeId) {
        for (BenchRecipeRegistry reg : registries.values()) {
            CraftingRecipe recipe = reg.getRecipeForBlock(blockTypeId);
            if (recipe != null) return recipe;
        }
        return null;
    }

    public static boolean isBaseBlockTypeAnywhere(@Nonnull String blockTypeId) {
        for (BenchRecipeRegistry reg : registries.values()) {
            if (reg.isBaseBlockType(blockTypeId)) return true;
        }
        return false;
    }

    public static boolean isBaseBlockRecipeAnywhere(@Nonnull String recipeId) {
        for (BenchRecipeRegistry reg : registries.values()) {
            if (reg.isBaseBlockRecipe(recipeId)) return true;
        }
        return false;
    }

    private static void log(String msg) {
        System.out.println("[BenchRecipeRegs] " + msg);
    }
}
