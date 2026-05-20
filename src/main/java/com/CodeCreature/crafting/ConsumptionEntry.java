package com.CodeCreature.crafting;

import javax.annotation.Nonnull;

/**
 * Immutable record representing a single item and quantity to consume from
 * the player's inventory as part of an {@link AutoCraftPlan}.
 *
 * <p>A consumption entry always uses a concrete item ID (never a
 * {@code ResourceTypeId}), because the auto-craft planner resolves all
 * abstract types during planning.
 *
 * <p>The entries in a plan may include both:
 * <ul>
 *   <li><b>Direct intermediates:</b> crafted items the player already has
 *       in inventory (e.g., 1 brick the player pre-crafted)</li>
 *   <li><b>Raw materials:</b> gathered resources consumed to "virtually
 *       craft" missing intermediates (e.g., 24 cobblestone to auto-craft
 *       2 bricks)</li>
 * </ul>
 *
 * @param itemId   the concrete item ID to consume
 * @param quantity the quantity to consume
 */
public record ConsumptionEntry(
        @Nonnull String itemId,
        int quantity
) {}
