package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for per-unit placement cost.
 *
 * <p>A recipe may produce multiple output items (e.g., 1 wood → 2 ladders).
 * When placing a single block via the PlaceBlock tool, the cost is the
 * recipe's (already-scaled) input divided by the output quantity:
 *
 * <pre>
 *   perUnitCost = Math.max(1, scaledInputQty / outputQty)
 * </pre>
 *
 * <p>This matches the formula used by {@code AbstractBenchProcessor} for
 * break-drops, ensuring the place→break loop is symmetric.
 */
public final class PlaceBlockCostUtil {

    private PlaceBlockCostUtil() {}

    /**
     * Returns the per-unit material cost for placing one block from the given recipe.
     *
     * <p>Each input quantity is divided by the recipe's primary output quantity,
     * with a floor of 1 to prevent zero-cost placements.
     *
     * @param recipe the crafting recipe (with already-scaled input quantities)
     * @return list of MaterialQuantity with per-unit quantities
     */
    @Nonnull
    public static List<MaterialQuantity> getPerUnitCost(@Nonnull CraftingRecipe recipe) {
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) {
            return List.of();
        }

        MaterialQuantity primaryOut = recipe.getPrimaryOutput();
        int outputQty = (primaryOut != null && primaryOut.getQuantity() > 0)
                ? primaryOut.getQuantity() : 1;

        List<MaterialQuantity> result = new ArrayList<>(inputs.length);
        for (MaterialQuantity mq : inputs) {
            if (mq == null) continue;
            int perUnit = Math.max(1, mq.getQuantity() / outputQty);
            result.add(mq.clone(perUnit));
        }
        return result;
    }
}
