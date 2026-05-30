package com.CodeCreature.crafting;

import com.CodeCreature.scaling.BenchCategory;
import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.scaling.RecipeTierClassifier;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes an {@link AutoCraftPlan} for a stencil placement, determining
 * what materials to consume from the player's inventory. Prefers existing
 * intermediates in inventory, falling back to auto-crafting deficits from
 * raw materials.
 *
 * <h3>Two-pass algorithm</h3>
 * <ol>
 *   <li><b>Fast path:</b> Check if the player has all direct recipe
 *       ingredients via {@code container.canRemoveMaterials()}. This uses
 *       the engine's native {@code ResourceTypeId} matching, so any
 *       matching variant satisfies the check. If yes, return a plan
 *       with no auto-crafting needed.</li>
 *   <li><b>Slow path:</b> For each direct ingredient:
 *     <ol>
 *       <li>Resolve to a concrete item ID</li>
 *       <li>Count how many the player has</li>
 *       <li>Use existing stock up to the needed quantity</li>
 *       <li>For any deficit where the item is crafted: look up
 *           {@link RecipeTreeResolver#resolveItemToRaw} and accumulate
 *           raw material needs</li>
 *       <li>For any deficit where the item is raw: accumulate directly</li>
 *     </ol>
 *     After processing all ingredients, verify that the player has
 *     enough of every accumulated raw material.</li>
 * </ol>
 *
 * <h3>Atomicity</h3>
 * The planner only produces a plan — it does not consume anything.
 * {@link StencilPlacementSystem} is responsible for executing the
 * plan atomically via {@code container.removeMaterials()}.
 *
 * <h3>Threading</h3>
 * Stateless — safe to call from any thread. Reads only from immutable
 * caches ({@link RecipeTreeResolver}) and the provided container.
 *
 * @see AutoCraftPlan
 * @see RecipeTreeResolver
 * @see com.CodeCreature.stencil.StencilPlacementSystem
 */
public final class AutoCraftPlanner {

    private AutoCraftPlanner() {}

    /**
     * Produces an {@link AutoCraftPlan} for placing one block from the
     * given recipe, checking the player's inventory for direct ingredients
     * and falling back to auto-crafting from raw materials.
     *
     * <h3>Fast path</h3>
     * If {@code container.canRemoveMaterials(directMaterials)} returns
     * {@code true}, the player has all direct ingredients (possibly
     * matching via {@code ResourceTypeId}). Returns a plan with
     * {@code requiresAutoCraft=false} and the direct materials as
     * consumptions.
     *
     * <h3>Slow path</h3>
     * For each per-unit input from
     * {@link PlaceBlockCostUtil#getPerUnitCost}:
     * <ol>
     *   <li>Resolve to concrete item ID via
     *       {@link ResourceTypeResolver#resolveInputItemId} and
     *       {@link NaturalResourceRegistry#resolveToGatherableForm}</li>
     *   <li>{@code playerHas = container.countItemStacks(predicate)}</li>
     *   <li>{@code useExisting = min(playerHas, needed)}</li>
     *   <li>{@code deficit = needed - useExisting}</li>
     *   <li>If deficit > 0 and item is crafted:
     *       look up {@link RecipeTreeResolver#resolveItemToRaw} and
     *       accumulate {@code rawQty * deficit} for each raw material</li>
     *   <li>If deficit > 0 and item is raw:
     *       accumulate deficit directly</li>
     * </ol>
     * After all ingredients are processed, verify total consumption
     * against actual inventory. If all satisfied, return an affordable
     * auto-craft plan.
     *
     * <h3>Overlap handling</h3>
     * The same raw material may appear both as a direct recipe input
     * and as a raw material needed for auto-crafting. The consumption
     * map uses {@code merge(itemId, qty, Integer::sum)} to aggregate
     * all needs, and the final inventory check validates the total.
     *
     * @param recipe    the crafting recipe for the block being placed
     * @param category  the bench category controlling
     *                  {@code ResourceTypeId} resolution preference
     * @param container the player's combined inventory container
     *                  (backpack + storage + hotbar)
     * @return an {@link AutoCraftPlan} with the consumption list and
     *         affordability result
     */
    @Nonnull
    public static AutoCraftPlan plan(@Nonnull CraftingRecipe recipe,
                                     @Nonnull BenchCategory category,
                                     @Nonnull CombinedItemContainer container) {
        // Step 1: Compute direct materials and display data
        List<MaterialQuantity> directMaterials = PlaceBlockCostUtil.getPerUnitCost(recipe);
        // Display data (directView, rawView) is not computed here — no caller reads
        // directCostView() or rawCostView() from the plan. Computing them doubled the
        // ResourceType resolution cost on every affordability check.
        List<ResolvedIngredient> directView = List.of();
        List<RawMaterialRequirement> rawView = List.of();

        if (directMaterials.isEmpty()) {
            return AutoCraftPlan.direct(List.of(), directView, rawView);
        }

        // Step 2: Fast path — player has all direct ingredients (excluding stencils)
        // NOTE: We cannot use container.canRemoveMaterials(directMaterials) here because
        // ResourceTypeId-based matching incorrectly matches stencil items (which share
        // the same ResourceTypes as regular items but have stencil BSON metadata).
        // Instead, resolve each ingredient to a concrete ItemId and count non-stencil items.
        boolean directlyAvailable = true;
        List<ConsumptionEntry> fastPathConsumptions = new ArrayList<>();
        for (MaterialQuantity mq : directMaterials) {
            if (mq == null) continue;
            String itemId = ResourceTypeResolver.resolveInputItemId(mq, category);
            if (itemId == null || itemId.isEmpty()) { directlyAvailable = false; break; }
            itemId = NaturalResourceRegistry.resolveToGatherableForm(itemId);
            final String lookupId = itemId;
            int available = container.countItemStacks(stack ->
                    lookupId.equals(stack.getItemId()) && !StencilMetadata.isStencil(stack));
            if (available < mq.getQuantity()) { directlyAvailable = false; break; }
            fastPathConsumptions.add(new ConsumptionEntry(itemId, mq.getQuantity()));
        }
        if (directlyAvailable) {
            return AutoCraftPlan.direct(fastPathConsumptions, directView, rawView);
        }

        // Step 3: Slow path — per-ingredient deficit computation
        Map<String, Integer> totalConsumption = new LinkedHashMap<>();
        boolean requiresAutoCraft = false;

        for (MaterialQuantity mq : directMaterials) {
            if (mq == null) continue;
            String resolvedId = ResourceTypeResolver.resolveInputItemId(mq, category);
            if (resolvedId == null || resolvedId.isEmpty()) {
                return AutoCraftPlan.unaffordable(directView, rawView);
            }
            resolvedId = NaturalResourceRegistry.resolveToGatherableForm(resolvedId);
            int needed = mq.getQuantity();

            final String lookupId = resolvedId;
            int playerHas = container.countItemStacks(stack ->
                    lookupId.equals(stack.getItemId()) && !StencilMetadata.isStencil(stack));
            int useExisting = Math.min(playerHas, needed);
            int deficit = needed - useExisting;

            if (useExisting > 0) {
                totalConsumption.merge(resolvedId, useExisting, Integer::sum);
            }

            if (deficit > 0) {
                if (RecipeTierClassifier.isCraftedItem(resolvedId)) {
                    requiresAutoCraft = true;
                    List<RawMaterialRequirement> rawPerUnit = RecipeTreeResolver.resolveItemToRaw(resolvedId);
                    if (rawPerUnit == null) {
                        return AutoCraftPlan.unaffordable(directView, rawView);
                    }
                    for (RawMaterialRequirement rawReq : rawPerUnit) {
                        totalConsumption.merge(rawReq.itemId(), rawReq.quantity() * deficit, Integer::sum);
                    }
                } else {
                    totalConsumption.merge(resolvedId, deficit, Integer::sum);
                }
            }
        }

        // Step 4: Verify all consumptions are affordable
        for (var entry : totalConsumption.entrySet()) {
            final String itemId = entry.getKey();
            int qtyNeeded = entry.getValue();
            int available = container.countItemStacks(stack ->
                    itemId.equals(stack.getItemId()) && !StencilMetadata.isStencil(stack));
            if (available < qtyNeeded) {
                return AutoCraftPlan.unaffordable(directView, rawView);
            }
        }

        // Step 5: Build and return affordable plan
        List<ConsumptionEntry> consumptions = totalConsumption.entrySet().stream()
                .map(e -> new ConsumptionEntry(e.getKey(), e.getValue()))
                .toList();

        if (requiresAutoCraft) {
            return AutoCraftPlan.autoCraft(consumptions, directView, rawView);
        } else {
            return AutoCraftPlan.direct(consumptions, directView, rawView);
        }
    }

    /**
     * Converts a list of {@link ConsumptionEntry} records to
     * {@link MaterialQuantity} instances suitable for
     * {@code container.removeMaterials()}.
     *
     * <p>For each entry, finds an existing {@link MaterialQuantity} in
     * the recipe ecosystem that references the same item ID and clones
     * it with the required quantity. This avoids the need to construct
     * {@code MaterialQuantity} instances directly (the SDK class may
     * not have a public constructor).
     *
     * <p>If no existing {@code MaterialQuantity} can be found for a
     * given item ID, that entry is unresolvable — the caller should
     * treat the plan as unexecutable.
     *
     * @param consumptions the consumption entries to convert
     * @return list of {@link MaterialQuantity} for use with
     *         {@code removeMaterials()}, or {@code null} if any entry
     *         could not be materialized
     */
    @Nonnull
    public static List<MaterialQuantity> toMaterialQuantities(
            @Nonnull List<ConsumptionEntry> consumptions) {
        // TODO: For each ConsumptionEntry:
        //   1. Find a MaterialQuantity somewhere in the recipe data that has
        //      matching itemId (search recipe inputs, or build a lookup map at init)
        //   2. Clone it with the entry's quantity via mq.clone(qty)
        //   3. Collect into result list
        //   If any entry has no matching MQ, log error and return null
        throw new UnsupportedOperationException("TODO");
    }
}
