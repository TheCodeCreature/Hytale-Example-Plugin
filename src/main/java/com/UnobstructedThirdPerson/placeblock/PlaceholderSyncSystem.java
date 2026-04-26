package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.registry.Registration;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages per-player {@link ItemContainer#registerChangeEvent} listeners on
 * hotbar and storage containers. When either container changes, delegates to
 * {@link BlockPreviewReskinManager#syncHotbar(PlayerRef, Inventory)}.
 *
 * <p><strong>Lifecycle:</strong>
 * <ul>
 *   <li>{@link #register(PlayerRef, Player)} — call on {@code PlayerReadyEvent}</li>
 *   <li>{@link #unregister(UUID)} — call on {@code PlayerDisconnectEvent}</li>
 * </ul>
 */
public final class PlaceholderSyncSystem {

    private static final Logger LOGGER = Logger.getLogger("PlaceholderSyncSystem");

    private static final Map<UUID, Registration[]> registrations = new ConcurrentHashMap<>();

    private PlaceholderSyncSystem() {}

    /**
     * Registers inventory change listeners on the player's hotbar and storage
     * containers. Each change triggers {@link BlockPreviewReskinManager#syncHotbar}.
     */
    public static void register(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        UUID playerId = playerRef.getUuid();

        // Avoid double-registration
        if (registrations.containsKey(playerId)) return;

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();

        Registration hotbarReg = hotbar.registerChangeEvent(event -> {
            BlockPreviewReskinManager.syncHotbar(playerRef, inventory);
        });

        Registration storageReg = storage.registerChangeEvent(event -> {
            BlockPreviewReskinManager.syncHotbar(playerRef, inventory);
        });

        registrations.put(playerId, new Registration[]{hotbarReg, storageReg});
        LOGGER.info("[PlaceholderSync] Registered inventory listeners for " + playerRef.getUsername());

        // Initial sync: reskin any armed variants already in the hotbar (e.g., after reconnect)
        BlockPreviewReskinManager.syncHotbar(playerRef, inventory);
    }

    /**
     * Unregisters inventory change listeners for the given player.
     * Call on disconnect.
     */
    public static void unregister(@Nonnull UUID playerId) {
        Registration[] regs = registrations.remove(playerId);
        if (regs != null) {
            for (Registration reg : regs) {
                reg.unregister();
            }
            LOGGER.info("[PlaceholderSync] Unregistered inventory listeners for " + playerId);
        }
    }
}
