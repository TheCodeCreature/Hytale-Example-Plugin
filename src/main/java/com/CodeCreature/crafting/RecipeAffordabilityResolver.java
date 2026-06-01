package com.CodeCreature.crafting;

import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

import com.CodeCreature.util.StencilMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateless utility that encapsulates the full ingredient resolution chain
 * and per-ingredient affordability checking for crafting recipes.
 *
 * <h3>Resolution chain</h3>
 * For each input {@link MaterialQuantity} in a recipe:
 * <ol>
 *   <li>{@link PlaceBlockCostUtil#getPerUnitCost} — scale input quantities
 *       by output quantity to get per-placement cost</li>
 *   <li>{@link ResourceTypeResolver#resolveInputItemId} — resolve
 *       {@code ResourceTypeId}-based inputs to concrete item IDs using
 *       bench-category preference (natural vs. non-natural)</li>
 *   <li>{@link NaturalResourceRegistry#resolveToGatherableForm} — map
 *       block-items to their gatherable drop form (e.g. Rock_Shale →
 *       Rock_Shale_Cobble)</li>
 * </ol>
 *
 * <p>Duplicate resolved item IDs are merged (quantities summed), matching
 * the behavior previously inlined in {@code BlueprintSelectionPage}.
 *
 * <h3>No UI dependencies</h3>
 * This class depends only on recipe, inventory, and resource-resolution
 * classes. It has no dependency on {@code UICommandBuilder}, {@code Value},
 * {@code AffordabilityMode}, or any UI page class. This allows it to be
 * used from both UI contexts (BlueprintSelectionPage, StencilRadialMenuPage)
 * and non-UI contexts (StencilVisualManager).
 *
 * <h3>Threading</h3>
 * All methods are stateless and safe to call from any thread. They read
 * only from immutable asset maps and the provided container.
 *
 * @see ResolvedIngredient
 * @see PlaceBlockCostUtil
 * @see ResourceTypeResolver
 * @see NaturalResourceRegistry
 */
public final class RecipeAffordabilityResolver {

    private RecipeAffordabilityResolver() {}

    /**
     * Resolves all ingredients for a recipe and checks each against the
     * player's inventory.
     *
     * <p>Steps:
     * <ol>
     *   <li>Compute per-unit costs via {@link PlaceBlockCostUtil#getPerUnitCost}</li>
     *   <li>For each {@link MaterialQuantity}, resolve to a concrete item ID
     *       via {@link ResourceTypeResolver#resolveInputItemId} using the
     *       given {@code preferNatural} preference</li>
     *   <li>Map through {@link NaturalResourceRegistry#resolveToGatherableForm}</li>
     *   <li>Merge duplicates: if two inputs resolve to the same item ID,
     *       sum their quantities into a single entry</li>
     *   <li>For each merged entry, count the player's holdings via
     *       {@code container.countItemStacks()} and build a
     *       {@link ResolvedIngredient}</li>
     * </ol>
     *
     * <p>If {@code container} is {@code null}, all ingredients will have
     * {@code playerHas=0} and {@code sufficient=false}.
     *
     * @param recipe         the crafting recipe to resolve ingredients for
     * @param preferNatural  {@code true} to prefer natural items, {@code false} for non-natural
     * @param container      the player's combined inventory container
     *                       (backpack + storage + hotbar), or {@code null} if
     *                       inventory checking is not available
     * @return list of resolved ingredients with affordability data,
     *         in resolution order (first occurrence determines position);
     *         empty list if the recipe has no inputs
     */
    @Nonnull
    public static List<ResolvedIngredient> resolveIngredientCosts(
            @Nonnull CraftingRecipe recipe,
            boolean preferNatural,
            @Nullable CombinedItemContainer container) {
        List<MaterialQuantity> perUnitInputs = PlaceBlockCostUtil.getPerUnitCost(recipe);
        if (perUnitInputs.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> ingredientMap = new LinkedHashMap<>();
        Map<String, String> resolvedToResourceType = new LinkedHashMap<>();
        for (MaterialQuantity mq : perUnitInputs) {
            if (mq == null) continue;
            String itemId = ResourceTypeResolver.resolveInputItemId(mq, preferNatural);
            if (itemId == null || itemId.isEmpty()) continue;
            itemId = NaturalResourceRegistry.resolveToGatherableForm(itemId);
            ingredientMap.merge(itemId, mq.getQuantity(), Integer::sum);
            if (mq.getResourceTypeId() != null) {
                resolvedToResourceType.putIfAbsent(itemId, mq.getResourceTypeId());
            }
        }

        List<ResolvedIngredient> result = new ArrayList<>(ingredientMap.size());
        for (var e : ingredientMap.entrySet()) {
            String itemId = e.getKey();
            int requiredQty = e.getValue();
            int playerHas;
            String resourceTypeId = resolvedToResourceType.get(itemId);
            if (container != null && resourceTypeId != null) {
                // Multi-variant: sum across all matching variants, excluding stencils
                List<String> allVariants = ResourceTypeResolver.getAllMatchingItemIds(resourceTypeId);
                int total = 0;
                Set<String> seen = new HashSet<>();
                for (String variantId : allVariants) {
                    String resolved = NaturalResourceRegistry.resolveToGatherableForm(variantId);
                    if (!seen.add(resolved)) continue;
                    final String lookupId = resolved;
                    total += container.countItemStacks(stack ->
                            lookupId.equals(stack.getItemId()) && !StencilMetadata.isStencil(stack));
                }
                playerHas = total;
            } else if (container != null) {
                playerHas = container.countItemStacks(stack -> itemId.equals(stack.getItemId()));
            } else {
                playerHas = 0;
            }
            result.add(new ResolvedIngredient(itemId, requiredQty, playerHas, playerHas >= requiredQty));
        }
        return List.copyOf(result);
    }

    /**
     * Returns whether the player can afford all ingredients for the given recipe.
     *
     * <p>This is a convenience method equivalent to:
     * <pre>
     *   resolveIngredientCosts(recipe, BUILDERS_ONLY, container)
     *       .stream().allMatch(ResolvedIngredient::sufficient)
     * </pre>
     *
     * <p>Uses {@code preferNatural=false} as the default, which matches
     * the behavior of {@code StencilVisualManager}'s previous direct
     * {@code canRemoveMaterials()} check (stencils are currently only
     * for builder-bench recipes, which prefer non-natural items).
     *
     * <p>Returns {@code true} if the recipe has no inputs (zero-cost recipe).
     *
     * @param recipe    the crafting recipe to check
     * @param container the player's combined inventory container
     * @return {@code true} if the player has sufficient quantity of every
     *         resolved ingredient, or if the recipe has no inputs
     */
    public static boolean isAffordable(@Nonnull CraftingRecipe recipe,
                                       @Nonnull CombinedItemContainer container) {
        List<ResolvedIngredient> ingredients = resolveIngredientCosts(recipe, false, container);
        return ingredients.isEmpty() || ingredients.stream().allMatch(ResolvedIngredient::sufficient);
    }

    /**
     * Returns whether the player can afford the recipe, including auto-craft
     * resolution of missing intermediates to raw materials.
     *
     * <p>Delegates to {@link AutoCraftPlanner#plan} and returns
     * {@code plan.affordable()}. This method checks both the direct
     * ingredient path (fast) and the auto-craft path (slow).
     *
     * <p>This is the preferred affordability check for stencil-related
     * contexts where auto-craft should be considered.
     *
     * @param recipe         the crafting recipe to check
     * @param preferNatural  {@code true} to prefer natural items during resolution
     * @param container      the player's combined inventory container
     * @return {@code true} if the player can afford directly or via auto-craft
     */
    public static boolean isAffordableWithAutoCraft(@Nonnull CraftingRecipe recipe,
                                                     boolean preferNatural,
                                                     @Nonnull CombinedItemContainer container) {
        return AutoCraftPlanner.plan(recipe, preferNatural, container).affordable();
    }
}
