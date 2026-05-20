package com.CodeCreature.crafting;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Immutable plan for consuming materials during a stencil placement,
 * potentially including auto-crafted intermediates resolved to raw materials.
 *
 * <p>Produced by {@link AutoCraftPlanner#plan}. Contains two categories
 * of information:
 *
 * <h3>Execution data</h3>
 * <ul>
 *   <li>{@link #consumptions()} — the flat list of concrete items to
 *       remove from the player's inventory. This may include both
 *       existing intermediates and raw materials for auto-crafting.</li>
 *   <li>{@link #affordable()} — whether the plan can be executed
 *       (all consumptions are satisfiable from inventory).</li>
 *   <li>{@link #requiresAutoCraft()} — whether any intermediates are
 *       being virtually crafted from raw materials. If {@code false},
 *       the plan is equivalent to the existing direct-consumption path.</li>
 * </ul>
 *
 * <h3>Display data (for UI)</h3>
 * <ul>
 *   <li>{@link #directCostView()} — the recipe's direct ingredients with
 *       per-ingredient affordability, for showing "Recipe needs: 3 bricks
 *       (you have: 1)".</li>
 *   <li>{@link #rawCostView()} — the total raw material cost for the
 *       full recipe, for showing "Total raw cost: 36 cobblestone".</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   AutoCraftPlan plan = AutoCraftPlanner.plan(recipe, category, container);
 *   if (plan.affordable()) {
 *       // Convert plan.consumptions() to MaterialQuantity list
 *       // Call container.removeMaterials(...)
 *   }
 * </pre>
 *
 * @param consumptions     the items to consume from inventory; empty if unaffordable
 * @param affordable       whether the player can afford the full plan
 * @param requiresAutoCraft whether auto-crafting is needed (false = fast path)
 * @param directCostView   the recipe's direct ingredients with affordability data
 * @param rawCostView      the total raw material cost for the recipe
 *
 * @see AutoCraftPlanner
 * @see ConsumptionEntry
 * @see RawMaterialRequirement
 */
public record AutoCraftPlan(
        @Nonnull List<ConsumptionEntry> consumptions,
        boolean affordable,
        boolean requiresAutoCraft,
        @Nonnull List<ResolvedIngredient> directCostView,
        @Nonnull List<RawMaterialRequirement> rawCostView
) {

    /**
     * Factory method for an affordable plan that uses the existing
     * direct-consumption path (no auto-crafting needed).
     *
     * @param consumptions  the direct ingredient consumptions
     * @param directCostView the resolved direct ingredients
     * @param rawCostView    the raw material breakdown (for display)
     * @return an affordable, non-auto-craft plan
     */
    @Nonnull
    public static AutoCraftPlan direct(
            @Nonnull List<ConsumptionEntry> consumptions,
            @Nonnull List<ResolvedIngredient> directCostView,
            @Nonnull List<RawMaterialRequirement> rawCostView) {
        return new AutoCraftPlan(List.copyOf(consumptions), true, false, List.copyOf(directCostView), List.copyOf(rawCostView));
    }

    /**
     * Factory method for an affordable plan that requires auto-crafting
     * some intermediates from raw materials.
     *
     * @param consumptions   the mixed consumption list (existing items + raw materials)
     * @param directCostView the resolved direct ingredients
     * @param rawCostView    the raw material breakdown (for display)
     * @return an affordable, auto-craft plan
     */
    @Nonnull
    public static AutoCraftPlan autoCraft(
            @Nonnull List<ConsumptionEntry> consumptions,
            @Nonnull List<ResolvedIngredient> directCostView,
            @Nonnull List<RawMaterialRequirement> rawCostView) {
        return new AutoCraftPlan(List.copyOf(consumptions), true, true, List.copyOf(directCostView), List.copyOf(rawCostView));
    }

    /**
     * Factory method for an unaffordable plan (player cannot afford the
     * recipe even with auto-crafting).
     *
     * @param directCostView the resolved direct ingredients (for display,
     *                       showing what's missing)
     * @param rawCostView    the raw material breakdown (for display)
     * @return an unaffordable plan with empty consumptions
     */
    @Nonnull
    public static AutoCraftPlan unaffordable(
            @Nonnull List<ResolvedIngredient> directCostView,
            @Nonnull List<RawMaterialRequirement> rawCostView) {
        return new AutoCraftPlan(List.of(), false, false, List.copyOf(directCostView), List.copyOf(rawCostView));
    }
}
