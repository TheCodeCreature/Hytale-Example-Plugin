package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.registry.Registration;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages per-player {@link ItemContainer#registerChangeEvent} listeners on
 * hotbar and storage containers. When either container changes, delegates to
 * {@link BlockPreviewReskinManager#syncPlaceholder(PlayerRef, Inventory)}.
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
     * containers. Each change triggers {@link BlockPreviewReskinManager#syncPlaceholder}.
     */
    public static void register(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        UUID playerId = playerRef.getUuid();

        // Avoid double-registration
        if (registrations.containsKey(playerId)) return;

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();

        Registration hotbarReg = hotbar.registerChangeEvent(event -> {
            BlockPreviewReskinManager.syncPlaceholder(playerRef, inventory);
            checkAffordability(playerRef, inventory);
        });

        Registration storageReg = storage.registerChangeEvent(event -> {
            BlockPreviewReskinManager.syncPlaceholder(playerRef, inventory);
            checkAffordability(playerRef, inventory);
        });

        registrations.put(playerId, new Registration[]{hotbarReg, storageReg});
        LOGGER.info("[PlaceholderSync] Registered inventory listeners for " + playerRef.getUsername());

        // Initial sync: reskin any armed variants already in the hotbar (e.g., after reconnect)
        BlockPreviewReskinManager.syncPlaceholder(playerRef, inventory);
        checkAffordability(playerRef, inventory);
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

    /**
     * Scans the hotbar for armed placeholders and toggles between Green/Red
     * states based on whether the player can afford the armed recipe.
     * Idempotent: only sets the item if the state actually needs to change.
     */
    private static void checkAffordability(@Nonnull PlayerRef playerRef, @Nonnull Inventory inventory) {
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer container = inventory.getCombinedBackpackStorageHotbar();

        for (short slot = 0; slot < PlaceBlockMetadata.HOTBAR_SIZE; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || !PlaceBlockMetadata.isArmed(stack)) continue;

            String recipeId = PlaceBlockMetadata.getArmedRecipeId(stack);
            if (recipeId == null) continue;

            CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
            if (recipe == null) continue;

            List<MaterialQuantity> materials = PlaceBlockCostUtil.getPerUnitCost(recipe);
            boolean affordable = container.canRemoveMaterials(materials);

            boolean currentlyGreen = PlaceBlockMetadata.isGreenVariant(stack);

            if (affordable && !currentlyGreen) {
                // Red → Green: recover the slot-specific Green state
                ItemStack green = PlaceBlockMetadata.toArmedGreen(stack, slot);
                hotbar.setItemStackForSlot(slot, green);
            } else if (!affordable && currentlyGreen) {
                // Green → Red
                ItemStack red = PlaceBlockMetadata.toArmedRed(stack);
                hotbar.setItemStackForSlot(slot, red);
            }
        }
    }
}
