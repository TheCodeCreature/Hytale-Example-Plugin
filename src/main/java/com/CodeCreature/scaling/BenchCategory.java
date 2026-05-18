package com.CodeCreature.scaling;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Classifies a recipe or block by which bench(es) it belongs to.
 *
 * <p>Each category defines a {@link #preferNatural()} flag that controls
 * how {@link ResourceTypeResolver} picks a concrete item for a
 * {@code ResourceTypeId} input:
 * <ul>
 *   <li>{@link #BUILDERS_ONLY} — prefers <strong>non-natural</strong> items
 *       (planks, decorative blocks — the FullBlocks set items that the
 *       Builders Bench works with)</li>
 *   <li>{@link #FURNITURE_ONLY} — prefers <strong>natural</strong> items
 *       (trunks, logs — raw materials that furniture recipes consume)</li>
 *   <li>{@link #BUILDERS_AND_FURNITURE} — prefers <strong>natural</strong> items
 *       (furniture preference wins when a recipe belongs to both benches)</li>
 * </ul>
 *
 * <p>New bench categories can be added here as the system grows (e.g.
 * {@code PROCESSING_ONLY}, {@code ALCHEMY_BENCH}, etc.).
 */
public enum BenchCategory {

    /** Recipe belongs to Builders bench only. Drops should be FullBlocks items (non-natural). */
    BUILDERS_ONLY(false, Set.of("Builders")),

    /** Recipe belongs to Furniture bench only. Drops should be natural resource items. */
    FURNITURE_ONLY(true, Set.of("Furniture_Bench")),

    /** Recipe belongs to both Builders and Furniture benches. Furniture preference wins. */
    BUILDERS_AND_FURNITURE(true, Set.of("Builders", "Furniture_Bench"));

    private final boolean preferNatural;
    private final Set<String> benchIds;

    BenchCategory(boolean preferNatural, Set<String> benchIds) {
        this.preferNatural = preferNatural;
        this.benchIds = benchIds;
    }

    /**
     * Whether {@link ResourceTypeResolver} should prefer natural items
     * when resolving a {@code ResourceTypeId} for this category.
     *
     * @return {@code true} for natural preference, {@code false} for
     *         non-natural (crafted/FullBlocks) preference
     */
    public boolean preferNatural() {
        return preferNatural;
    }

    /**
     * The set of bench IDs that define this category.
     *
     * @return immutable set of bench requirement IDs
     */
    public Set<String> benchIds() {
        return benchIds;
    }

    /**
     * Returns the union of all bench IDs across all enum constants.
     * This is the <strong>single source of truth</strong> for which bench
     * IDs are recognized system-wide.
     *
     * <p>Used by {@link RecipeFilterRegistry} to determine which
     * {@link BenchRequirement} IDs qualify a recipe for inclusion.
     *
     * @return unmodifiable set of all known bench IDs
     */
    public static Set<String> allBenchIds() {
        Set<String> result = new LinkedHashSet<>();
        for (BenchCategory c : EnumSet.allOf(BenchCategory.class)) {
            result.addAll(c.benchIds());
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Determines the {@link BenchCategory} for a recipe based on which
     * bench requirement IDs it declares.
     *
     * <p>Checks the recipe's {@link BenchRequirement} array against the
     * known bench IDs ({@code "Builders"}, {@code "Furniture_Bench"}).
     *
     * @param recipe the crafting recipe to classify
     * @return the matching category, or {@code null} if the recipe
     *         does not belong to any known bench
     */
    @javax.annotation.Nullable
    public static BenchCategory fromRecipe(@Nonnull CraftingRecipe recipe) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return null;

        boolean hasBuilders = false;
        boolean hasFurniture = false;
        for (BenchRequirement req : reqs) {
            if (req == null) continue;
            if ("Builders".equals(req.id)) hasBuilders = true;
            else if ("Furniture_Bench".equals(req.id)) hasFurniture = true;
        }

        if (hasBuilders && hasFurniture) return BUILDERS_AND_FURNITURE;
        if (hasBuilders) return BUILDERS_ONLY;
        if (hasFurniture) return FURNITURE_ONLY;
        return null;
    }
}
