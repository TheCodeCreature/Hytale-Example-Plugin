package com.CodeCreature.registry;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of a crafting recipe that passed all common validation
 * predicates in {@link RecipeFilterRegistry}. Contains the fields needed by
 * both consumers (UI and DropScaler pipeline) without any consumer-specific
 * state such as affordability or base-block classification.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code recipe} — reference to the original {@link CraftingRecipe}
 *       asset for consumers that need access to inputs, outputs, etc.</li>
 *   <li>{@code recipeId} — the asset ID (e.g. {@code "Wood_Hardwood_Planks"})</li>
 *   <li>{@code outputItemId} — the item ID of the primary output</li>
 *   <li>{@code blockTypeId} — the block ID of the output item (non-null,
 *       guaranteed by the registry's validation predicate)</li>
 *   <li>{@code benchIds} — the set of ALL bench requirement IDs that this
 *       recipe declares (scanning every {@code BenchRequirement} entry,
 *       not just the first)</li>
 *   <li>{@code preferNatural} — whether this recipe's bench prefers
 *       natural resource items during {@code ResourceTypeId} resolution</li>
 *   <li>{@code set} — the {@code Item.set} value extracted via reflection,
 *       used for UI grouping; may be {@code null} if the item has no set</li>
 * </ul>
 *
 * @param recipe        the original CraftingRecipe asset
 * @param recipeId      recipe asset ID
 * @param outputItemId  output item asset ID
 * @param blockTypeId   output item's block type ID (never null)
 * @param benchIds      all matching bench requirement IDs (immutable, never empty)
 * @param preferNatural whether this recipe's bench prefers natural items
 * @param set           the Item.set value, or null
 */
public record FilteredRecipeEntry(
        @Nonnull CraftingRecipe recipe,
        @Nonnull String recipeId,
        @Nonnull String outputItemId,
        @Nonnull String blockTypeId,
        @Nonnull Set<String> benchIds,
        boolean preferNatural,
        @Nullable String set,
        @Nonnull List<String> categoryIds
) {}
