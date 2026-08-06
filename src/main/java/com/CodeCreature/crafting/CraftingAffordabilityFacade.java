package com.CodeCreature.crafting;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

public final class CraftingAffordabilityFacade {

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

    @Nonnull
    public static List<DirectIngredientView> resolveDirectIngredients(@Nonnull CraftingRecipe recipe,
                                                                      boolean preferNatural,
                                                                      @Nullable CombinedItemContainer container) {
        return RecipeAffordabilityResolver.resolveIngredientAffordability(recipe, preferNatural, container);
    }

    public static boolean isAffordable(@Nonnull CraftingRecipe recipe,
                                       boolean preferNatural,
                                       @Nullable CombinedItemContainer container) {
        if (container == null) {
            return false;
        }
        List<DirectIngredientView> ingredients = resolveDirectIngredients(recipe, preferNatural, container);
        return ingredients.isEmpty() || ingredients.stream().allMatch(DirectIngredientView::sufficient);
    }

    public static boolean isAffordableWithAutoCraft(@Nonnull CraftingRecipe recipe,
                                                    boolean preferNatural,
                                                    @Nullable CombinedItemContainer container) {
        if (container == null) {
            return false;
        }
        return RecipeAffordabilityResolver.isAffordableWithAutoCraft(recipe, preferNatural, container);
    }
}
