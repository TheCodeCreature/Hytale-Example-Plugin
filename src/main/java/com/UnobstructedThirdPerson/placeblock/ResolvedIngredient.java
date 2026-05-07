package com.UnobstructedThirdPerson.placeblock;

import javax.annotation.Nonnull;

/**
 * Immutable result of resolving a single recipe ingredient through the
 * 3-step resolution chain and checking it against a player's inventory.
 *
 * <p>Produced by {@link RecipeAffordabilityResolver#resolveIngredientCosts}.
 * If multiple {@code MaterialQuantity} entries in a recipe resolve to the
 * same concrete item ID, their quantities are merged into a single record.
 *
 * @param resolvedItemId the concrete item ID after resolution through
 *                       {@code ResourceTypeResolver} and
 *                       {@code NaturalResourceRegistry}
 * @param requiredQty    the total quantity needed (merged if duplicates existed)
 * @param playerHas      the quantity the player currently holds in the
 *                       combined backpack+storage+hotbar container;
 *                       0 if no container was provided
 * @param sufficient     {@code true} if {@code playerHas >= requiredQty}
 */
public record ResolvedIngredient(
        @Nonnull String resolvedItemId,
        int requiredQty,
        int playerHas,
        boolean sufficient
) {}
