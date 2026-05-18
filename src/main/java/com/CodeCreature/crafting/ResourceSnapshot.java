package com.CodeCreature.crafting;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of available resources across a player's inventory
 * and nearby chest containers.
 *
 * <p>Produced by {@link ResourceScanner#scanAvailableResources} and consumed
 * by both recipe filtering (Phase 1) and atomic placement consumption (Phase 3).
 * By using the same snapshot for check-and-consume, we avoid TOCTOU races
 * within a single placement action.
 *
 * <p><strong>Important:</strong> The snapshot captures item counts at scan time.
 * Between scan and consumption, another thread or event could modify containers.
 * This is accepted per the contract: "first to place gets the resources."
 *
 * @param itemCounts aggregated item counts by item ID across all scanned containers
 * @param sources    the ordered list of item containers that were scanned (inventory
 *                   first, then chests). Used by {@link ResourceScanner#consumeAtomically}
 *                   to know WHERE to remove items from.
 */
public record ResourceSnapshot(
        @Nonnull Map<String, Integer> itemCounts,
        @Nonnull List<ItemContainer> sources
) {
    public ResourceSnapshot {
        itemCounts = Collections.unmodifiableMap(itemCounts);
        sources = Collections.unmodifiableList(sources);
    }

    /**
     * Returns the total available count of the given item across all
     * scanned containers.
     *
     * @param itemId the item asset ID to look up
     * @return the total count, or 0 if the item was not found
     */
    public int getCount(@Nonnull String itemId) {
        return itemCounts.getOrDefault(itemId, 0);
    }

    /**
     * Checks whether all the given material inputs can be satisfied by
     * the resources in this snapshot.
     *
     * <p>For each {@link MaterialQuantity} input, resolves the item ID
     * (direct or via ResourceTypeId) and checks that the available count
     * meets or exceeds the required quantity.
     *
     * @param inputs the recipe input requirements to check
     * @return true if all inputs can be satisfied
     */
    public boolean canAfford(@Nonnull MaterialQuantity[] inputs) {
        // TODO: For each input, resolve item ID (handle ResourceTypeId via
        //       ResourceTypeResolver if needed), sum required quantities per
        //       unique item ID, then compare against itemCounts.
        //       Return false if any item is insufficient.
        return false;
    }
}
