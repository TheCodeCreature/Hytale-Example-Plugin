package com.CodeCreature.crafting;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * @node    CraftingAffordabilityFacade
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Exposes a stable crafting affordability boundary for UI and runtime callers, including
 *          direct ingredient views and generic-aware affordability checks.
 * @wave    4 (generic icons + generic affordability boundary)
 * @status  Wave 4 - implemented facade wiring for direct and auto-craft affordability callers
 * @do-not  Implement planner internals here.
 *          Collapse generic identity into concrete-only semantics in facade payloads.
 */
public final class CraftingAffordabilityFacade {

    /** @intent Immutable direct ingredient affordability projection for UI and runtime consumers.
     *  @wave   4 - implemented facade view contract
     *  @status implemented
     *  @node   CraftingAffordabilityFacade.DirectIngredientView
     */
    public record DirectIngredientView(
            @Nonnull GenericIngredientIdentity identity,
            @Nonnull IngredientPresentation presentation,
            int requiredQty,
            int playerHas,
            boolean sufficient
    ) {
        public DirectIngredientView {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(presentation, "presentation");
        }
    }

    private CraftingAffordabilityFacade() {
    }

    /** @intent Resolve recipe direct ingredients with generic-aware identity and presentation metadata.
     *  @wave   4 - implemented facade direct ingredient adapter
     *  @status implemented
     *  @node   CraftingAffordabilityFacade#resolveDirectIngredients
     */
    @Nonnull
    public static List<DirectIngredientView> resolveDirectIngredients(@Nonnull CraftingRecipe recipe,
                                                                      boolean preferNatural,
                                                                      @Nullable CombinedItemContainer container) {
        return RecipeAffordabilityResolver.resolveIngredientAffordability(recipe, preferNatural, container);
    }

    /** @intent Check direct affordability without auto-crafting while preserving generic-aware variant accounting.
     *  @wave   4 - implemented direct affordability facade check
     *  @status implemented
     *  @node   CraftingAffordabilityFacade#isAffordable
     */
    public static boolean isAffordable(@Nonnull CraftingRecipe recipe,
                                       boolean preferNatural,
                                       @Nullable CombinedItemContainer container) {
        if (container == null) {
            return false;
        }
        List<DirectIngredientView> ingredients = resolveDirectIngredients(recipe, preferNatural, container);
        return ingredients.isEmpty() || ingredients.stream().allMatch(DirectIngredientView::sufficient);
    }

    /** @intent Check affordability including auto-craft expansion while keeping concrete removal as a later execution boundary.
     *  @wave   4 - implemented auto-craft affordability facade check
     *  @status implemented
     *  @node   CraftingAffordabilityFacade#isAffordableWithAutoCraft
     */
    public static boolean isAffordableWithAutoCraft(@Nonnull CraftingRecipe recipe,
                                                    boolean preferNatural,
                                                    @Nullable CombinedItemContainer container) {
        if (container == null) {
            return false;
        }
        return RecipeAffordabilityResolver.isAffordableWithAutoCraft(recipe, preferNatural, container);
    }
}
