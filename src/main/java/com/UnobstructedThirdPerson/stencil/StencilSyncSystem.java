package com.UnobstructedThirdPerson.stencil;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors hotbar inventory changes and restores stencil items that were
 * decremented by the engine's native block placement consumption.
 *
 * <p>When the engine places a block, it calls
 * {@code removeItemStackFromSlot(activeSlot, itemStack, 1)}, decrementing the
 * stencil from qty 2 → qty 1. This listener detects that a stencil in the
 * hotbar dropped to qty 1 and restores it to qty 2.</p>
 *
 * <p><b>Why qty 2?</b> Stencils are created with qty 2 so the engine's
 * consumption of 1 leaves qty 1 (item survives). This listener restores
 * qty 2 so the next placement can also proceed. The client sees qty 2 → 1 → 2,
 * but the restoration happens server-side on the next inventory change event,
 * which is fast enough to be invisible.</p>
 */
public final class StencilSyncSystem {

    private static final ConcurrentHashMap<UUID, Boolean> registeredPlayers = new ConcurrentHashMap<>();

    private StencilSyncSystem() {}

    /**
     * Registers inventory change listeners for a player to auto-restore stencil quantities.
     */
    public static void register(PlayerRef playerRef, Player player) {
        UUID uuid = playerRef.getUuid();
        if (registeredPlayers.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return; // Already registered
        }

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();

        hotbar.registerChangeEvent(event -> {
            restoreStencils(hotbar);
            StencilVisualManager.refreshAffordability(playerRef, player);
        });

        // Also refresh affordability when backpack/storage change (e.g., picking up or dropping items)
        inventory.getBackpack().registerChangeEvent(event ->
                StencilVisualManager.refreshAffordability(playerRef, player));
        inventory.getStorage().registerChangeEvent(event ->
                StencilVisualManager.refreshAffordability(playerRef, player));
    }

    /**
     * Unregisters a player (cleanup on disconnect).
     */
    public static void unregister(UUID uuid) {
        registeredPlayers.remove(uuid);
    }

    /**
     * Scans the hotbar for stencil items with qty 1 and restores them to qty 2.
     */
    private static void restoreStencils(ItemContainer hotbar) {
        short capacity = hotbar.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null) continue;
            if (!StencilMetadata.isStencil(stack)) continue;
            if (stack.getQuantity() == 1) {
                ItemStack restored = new ItemStack(stack.getItemId(), 2, stack.getMetadata());
                hotbar.setItemStackForSlot(slot, restored);
            }
        }
    }
}
