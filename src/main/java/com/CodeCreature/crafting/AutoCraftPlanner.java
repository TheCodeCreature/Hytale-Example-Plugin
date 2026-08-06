package com.CodeCreature.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.scaling.RecipeTierClassifier;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

/**
 * Computes an {@link AutoCraftPlan} for a stencil placement, determining
 * what materials to consume from the player's inventory. Prefers existing
 * intermediates in inventory, falling back to auto-crafting deficits from
 * raw materials.
 *
 * <h3>Two-pass algorithm</h3>
 * <ol>
 *   <li><b>Fast path:</b> Use typed generic resolution and variant-aware
 *       counting to satisfy direct ingredients without auto-crafting.
 *       This avoids direct {@code canRemoveMaterials()} checks so stencil-tag
 *       exclusions stay consistent with planner parity rules.</li>
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
 * Raw projection helpers in {@link RecipeTreeResolver} remain outside
 * this final removal boundary.
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

    private record PlannerIngredientNeed(
            GenericIngredientResolution resolution,
            @Nullable String deficitProjectionItemId,
            boolean usedCompatibilityFallback,
            List<ConsumptionEntry> directConsumptions,
            int deficitQty
    ) {
        private boolean directlyAvailable() {
            return deficitQty <= 0;
        }
    }

    private record DeficitExpansionResult(boolean resolved, boolean requiresAutoCraft) {}

    private AutoCraftPlanner() {}

    @Nonnull
    public static AutoCraftPlan plan(@Nonnull CraftingRecipe recipe,
                                     boolean preferNatural,
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

        List<GenericIngredientResolution> directResolutions =
                ResourceTypeResolver.resolveGenericIngredients(directMaterials, preferNatural);

        // Step 2: Typed resolution plus variant-aware direct stock accounting.
        // We still avoid container.canRemoveMaterials(directMaterials) because generic
        // engine matching includes stencil-tagged items that the planner must exclude.
        List<PlannerIngredientNeed> plannerNeeds = new ArrayList<>(directResolutions.size());
        List<ConsumptionEntry> fastPathConsumptions = new ArrayList<>();
        boolean directlyAvailable = true;
        for (GenericIngredientResolution resolution : directResolutions) {
            PlannerIngredientNeed plannerNeed = resolvePlannerIngredientNeed(resolution, container);
            if (plannerNeed == null) {
                return AutoCraftPlan.unaffordable(directView, rawView);
            }
            plannerNeeds.add(plannerNeed);
            fastPathConsumptions.addAll(plannerNeed.directConsumptions());
            directlyAvailable &= plannerNeed.directlyAvailable();
        }
        if (directlyAvailable) {
            return AutoCraftPlan.direct(fastPathConsumptions, directView, rawView);
        }

        // Step 3: Late deficit expansion. Direct variant availability is already accounted for;
        // only the remaining deficit needs a concrete compatibility projection for current
        // crafted-item checks and raw-cost lookup policy.
        Map<String, Integer> totalConsumption = new LinkedHashMap<>();
        for (PlannerIngredientNeed plannerNeed : plannerNeeds) {
            mergeConsumptionEntries(totalConsumption, plannerNeed.directConsumptions());
        }

        boolean requiresAutoCraft = false;
        for (PlannerIngredientNeed plannerNeed : plannerNeeds) {
            DeficitExpansionResult expansion = expandDeficit(plannerNeed, totalConsumption);
            if (!expansion.resolved()) {
                return AutoCraftPlan.unaffordable(directView, rawView);
            }
            requiresAutoCraft |= expansion.requiresAutoCraft();
        }

        // Step 4: Verify all consumptions are affordable
        for (var entry : totalConsumption.entrySet()) {
            final String itemId = entry.getKey();
            int qtyNeeded = entry.getValue();
            int available = GenericVariantMatcher.countConcreteQuantity(container, itemId, true);
            if (available < qtyNeeded) {
                return AutoCraftPlan.unaffordable(directView, rawView);
            }
        }

        // Step 5: Build and return affordable plan
        List<ConsumptionEntry> consumptions = toConsumptionEntries(totalConsumption);

        if (requiresAutoCraft) {
            return AutoCraftPlan.autoCraft(consumptions, directView, rawView);
        } else {
            return AutoCraftPlan.direct(consumptions, directView, rawView);
        }
    }

    private static PlannerIngredientNeed resolvePlannerIngredientNeed(@Nonnull GenericIngredientResolution resolution,
                                                                     @Nonnull CombinedItemContainer container) {
        String compatibilityItemId = resolveCompatibilityItemId(resolution);
        if (compatibilityItemId == null || compatibilityItemId.isEmpty()) {
            return null;
        }

        List<GenericVariantMatcher.VariantAvailability> variantAvailability =
                GenericVariantMatcher.collectVariantAvailability(resolution, compatibilityItemId, container, true);

        int remaining = resolution.identity().quantity();
        List<ConsumptionEntry> directConsumptions = new ArrayList<>(variantAvailability.size());
        for (GenericVariantMatcher.VariantAvailability entry
                : GenericTokenMorphPolicy.orderForConsumption(variantAvailability)) {
            if (remaining <= 0) {
                break;
            }
            int use = Math.min(entry.quantity(), remaining);
            if (use > 0) {
                directConsumptions.add(new ConsumptionEntry(entry.itemId(), use));
                remaining -= use;
            }
        }

        DeficitProjection projection = resolveDeficitProjectionItemId(resolution, variantAvailability, compatibilityItemId);
        return new PlannerIngredientNeed(
                resolution,
                projection.itemId(),
                projection.compatibilityFallback(),
                List.copyOf(directConsumptions),
                remaining);
    }

    private record DeficitProjection(@Nullable String itemId, boolean compatibilityFallback) {}

    @Nonnull
    private static DeficitProjection resolveDeficitProjectionItemId(
            @Nonnull GenericIngredientResolution resolution,
            @Nonnull List<GenericVariantMatcher.VariantAvailability> availability,
            @Nullable String compatibilityItemId) {
        GenericTokenMorphPolicy.MorphSelection morphSelection =
                GenericTokenMorphPolicy.selectFromAvailability(resolution, availability);
        if (morphSelection.concreteItemId() != null && !morphSelection.concreteItemId().isEmpty()) {
            return new DeficitProjection(morphSelection.concreteItemId(), false);
        }

        if (morphSelection.remainsGeneric()) {
            // Temporary compatibility path: planner deficit expansion still needs a concrete key
            // for crafted-item checks and raw-cost recursion; use representative projection only
            // when policy A cannot pick a concrete matching stack.
            return new DeficitProjection(compatibilityItemId, true);
        }

        return new DeficitProjection(compatibilityItemId, false);
    }

    private static String resolveCompatibilityItemId(@Nonnull GenericIngredientResolution resolution) {
        String representativeItemId = resolution.representativeItemId();
        if ((representativeItemId == null || representativeItemId.isEmpty())
                && resolution.orderedMatchingItemIds().size() == 1) {
            representativeItemId = resolution.orderedMatchingItemIds().get(0);
        }
        if (representativeItemId == null || representativeItemId.isEmpty()) {
            return null;
        }
        return NaturalResourceRegistry.resolveToGatherableForm(representativeItemId);
    }

    @Nonnull
    private static DeficitExpansionResult expandDeficit(@Nonnull PlannerIngredientNeed plannerNeed,
                                                        @Nonnull Map<String, Integer> totalConsumption) {
        if (plannerNeed.deficitQty() <= 0) {
            return new DeficitExpansionResult(true, false);
        }

        String compatibilityItemId = plannerNeed.deficitProjectionItemId();
        if (compatibilityItemId == null || compatibilityItemId.isEmpty()) {
            return new DeficitExpansionResult(false, false);
        }

        if (plannerNeed.usedCompatibilityFallback()) {
            // Compatibility fallback is non-ideal for parity; keep deterministic behavior while
            // runtime probe coverage drives a future engine-backed replacement.
            // Boundary guard: execution still removes the explicit concrete consumption list
            // emitted by the planner and never calls RecipeTreeResolver display-projection helpers.
        }

        if (RecipeTierClassifier.isCraftedItem(compatibilityItemId)) {
            List<RawMaterialRequirement> rawPerUnit = RecipeTreeResolver.resolveItemToRaw(compatibilityItemId);
            if (rawPerUnit == null) {
                return new DeficitExpansionResult(false, false);
            }
            for (RawMaterialRequirement rawReq : rawPerUnit) {
                totalConsumption.merge(rawReq.itemId(), rawReq.quantity() * plannerNeed.deficitQty(), Integer::sum);
            }
            return new DeficitExpansionResult(true, true);
        }

        totalConsumption.merge(compatibilityItemId, plannerNeed.deficitQty(), Integer::sum);
        return new DeficitExpansionResult(true, false);
    }

    private static void mergeConsumptionEntries(@Nonnull Map<String, Integer> totalConsumption,
                                                @Nonnull List<ConsumptionEntry> consumptions) {
        for (ConsumptionEntry consumption : consumptions) {
            totalConsumption.merge(consumption.itemId(), consumption.quantity(), Integer::sum);
        }
    }

    @Nonnull
    private static List<ConsumptionEntry> toConsumptionEntries(@Nonnull Map<String, Integer> totalConsumption) {
        return totalConsumption.entrySet().stream()
                .map(entry -> new ConsumptionEntry(entry.getKey(), entry.getValue()))
                .toList();
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
