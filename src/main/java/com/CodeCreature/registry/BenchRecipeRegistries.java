package com.CodeCreature.registry;

import com.CodeCreature.scaling.BenchCategory;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

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

    public static void init() {
        Set<String> benchIds = BenchCategory.allBenchIds();
        Map<String, BenchRecipeRegistry> map = new LinkedHashMap<>();
        int totalRecipes = 0;
        for (String benchId : benchIds) {
            BenchRecipeRegistry registry = new BenchRecipeRegistry(benchId);
            registry.init();
            map.put(benchId, registry);
            totalRecipes += registry.getAllRecipesById().size();
        }
        registries = Collections.unmodifiableMap(map);
        DebugLogger.log(REGISTRY, Level.INFO, "[BenchRecipeRegs] Initialized " + map.size() + " bench registries with "
                + totalRecipes + " total recipes");
    }

    @Nullable
    public static BenchRecipeRegistry getRegistry(@Nonnull String benchId) {
        return registries.get(benchId);
    }

    public static Collection<BenchRecipeRegistry> getAllRegistries() {
        return registries.values();
    }

    @Nullable
    public static BenchRecipeRegistry getRegistryForBlock(@Nonnull String blockTypeId) {
        for (BenchRecipeRegistry reg : registries.values()) {
            if (reg.hasRecipe(blockTypeId)) return reg;
        }
        return null;
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
}
