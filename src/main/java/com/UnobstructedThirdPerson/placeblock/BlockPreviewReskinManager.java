package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages per-player, per-variant reskinning of Green placeholder block types via
 * {@link UpdateBlockTypes} packets so each hotbar slot can independently show its
 * armed recipe's target block in the client's native block preview.
 *
 * <p><strong>Usage:</strong> Call {@link #syncPlaceholder} from {@link PlaceholderSyncSystem}
 * on every inventory change. Call {@link #cleanup} on player disconnect.
 */
public final class BlockPreviewReskinManager {

    private static final Logger LOGGER = Logger.getLogger("BlockPreviewReskinManager");

    /**
     * Player UUID → (variant index → current target block type ID).
     * Outer map is ConcurrentHashMap for cross-thread safety; inner maps are
     * effectively single-threaded per player.
     */
    private static final ConcurrentHashMap<UUID, Map<Integer, String>> activeReskins = new ConcurrentHashMap<>();

    /**
     * Cached original packets for the 9 Green variants (indices 0-8).
     * Lazily captured on first use via {@link #captureOriginalPackets()}.
     */
    private static volatile com.hypixel.hytale.protocol.BlockType[] originalVariantPackets;

    /**
     * Cached original item packets for the 9 Green variants (indices 0-8).
     * Lazily captured alongside block packets.
     */
    private static volatile ItemBase[] originalVariantItemPackets;

    private BlockPreviewReskinManager() {}

    /**
     * Main sync entry point. For each hotbar slot 0-8:
     * <ol>
     *   <li>If armed Green: ensure correct variant ID, reskin that variant's block type</li>
     *   <li>If not armed Green: restore that variant if previously reskinned</li>
     * </ol>
     * Also scans storage and reverts any Green variants back to base Green.
     */
    public static void syncPlaceholder(@Nonnull PlayerRef playerRef, @Nonnull Inventory inventory) {
        captureOriginalPackets();
        if (originalVariantPackets == null) return;

        ItemContainer hotbar = inventory.getHotbar();

        for (int slot = 0; slot < PlaceBlockMetadata.HOTBAR_SIZE; slot++) {
            ItemStack stack = hotbar.getItemStack((short) slot);

            if (stack != null && PlaceBlockMetadata.isGreenVariant(stack) && PlaceBlockMetadata.isArmed(stack)) {
                // Ensure correct variant ID for this slot
                String expectedId = PlaceBlockMetadata.getGreenStateItemId(slot);
                if (!expectedId.equals(stack.getItemId())) {
                    ItemStack converted = PlaceBlockMetadata.toArmedGreen(stack, slot);
                    hotbar.setItemStackForSlot((short) slot, converted);
                }

                String targetBlockTypeId = PlaceBlockMetadata.getOutputBlockTypeId(stack);
                if (targetBlockTypeId != null) {
                    reskinVariant(playerRef, slot, targetBlockTypeId);
                }
            } else {
                restoreVariant(playerRef, slot);
            }
        }

        // Scan storage: revert any Green variants back to base
        ItemContainer storage = inventory.getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && PlaceBlockMetadata.isGreenVariant(stack)) {
                ItemStack base = PlaceBlockMetadata.disarm(stack);
                storage.setItemStackForSlot(i, base);
            }
        }
    }

    /**
     * Removes all tracking state for the player. No packets sent (connection is closing).
     */
    public static void cleanup(@Nonnull UUID playerId) {
        Map<Integer, String> removed = activeReskins.remove(playerId);
        if (removed != null && !removed.isEmpty()) {
            LOGGER.info("[BlockPreviewReskin] Cleaned up reskin state for " + playerId);
        }
    }

    private static void reskinVariant(@Nonnull PlayerRef playerRef, int variantIndex,
                                      @Nonnull String targetBlockTypeId) {
        UUID playerId = playerRef.getUuid();
        Map<Integer, String> playerMap = activeReskins.computeIfAbsent(playerId, k -> new HashMap<>());

        // Idempotent: skip if already reskinned to this target
        String current = playerMap.get(variantIndex);
        if (targetBlockTypeId.equals(current)) return;

        BlockType targetType = BlockType.getAssetMap().getAsset(targetBlockTypeId);
        if (targetType == null) {
            LOGGER.warning("[BlockPreviewReskin] Target block type not found: " + targetBlockTypeId);
            return;
        }

        com.hypixel.hytale.protocol.BlockType targetPacket =
                new com.hypixel.hytale.protocol.BlockType(targetType.toPacket());

        // Preserve the original variant's PlacementSettings (RotationMode: Default,
        // AllowRotationKey: true). The target block may have RotationMode: FacingPlayer
        // which auto-rotates the ghost preview every tick, overriding R-key input.
        if (originalVariantPackets[variantIndex] != null
                && originalVariantPackets[variantIndex].placementSettings != null) {
            targetPacket.placementSettings = originalVariantPackets[variantIndex].placementSettings;
        }

        sendBlockTypeUpdate(playerRef, variantIndex, targetPacket);
        sendItemUpdate(playerRef, variantIndex, targetBlockTypeId);
        playerMap.put(variantIndex, targetBlockTypeId);
    }

    private static void restoreVariant(@Nonnull PlayerRef playerRef, int variantIndex) {
        UUID playerId = playerRef.getUuid();
        Map<Integer, String> playerMap = activeReskins.get(playerId);
        if (playerMap == null) return;

        String removed = playerMap.remove(variantIndex);
        if (removed == null) return;

        sendBlockTypeUpdate(playerRef, variantIndex, originalVariantPackets[variantIndex]);
        sendItemRestore(playerRef, variantIndex);

        if (playerMap.isEmpty()) {
            activeReskins.remove(playerId);
        }
    }

    private static void sendBlockTypeUpdate(@Nonnull PlayerRef playerRef, int variantIndex,
                                   @Nonnull com.hypixel.hytale.protocol.BlockType packet) {
        String variantId = PlaceBlockMetadata.getGreenStateItemId(variantIndex);
        int numericId = BlockType.getAssetMap().getIndex(variantId);

        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = BlockType.getAssetMap().getNextIndex();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
        blockTypes.put(numericId, packet);
        update.blockTypes = blockTypes;
        update.updateBlockTextures = true;
        update.updateModelTextures = true;
        update.updateModels = true;
        update.updateMapGeometry = true;

        playerRef.getPacketHandler().writeNoCache(update);
    }

    private static void sendItemUpdate(@Nonnull PlayerRef playerRef, int variantIndex,
                                       @Nonnull String targetBlockTypeId) {
        // Look up the target block's item to clone its appearance
        Item targetItem = Item.getAssetMap().getAsset(targetBlockTypeId);
        if (targetItem == null) return;

        String variantId = PlaceBlockMetadata.getGreenStateItemId(variantIndex);
        ItemBase targetPacket = new ItemBase(targetItem.toPacket());
        targetPacket.id = variantId;

        // Preserve the variant's original interactions and rarity.
        // The target item's interactions include RemoveItemInHand: true which would
        // overwrite the client's definition of our placeholder state, causing the
        // client to predictively consume the item on right-click.
        if (originalVariantItemPackets[variantIndex] != null) {
            targetPacket.qualityIndex = originalVariantItemPackets[variantIndex].qualityIndex;
            targetPacket.interactions = originalVariantItemPackets[variantIndex].interactions;
        }

        UpdateItems update = new UpdateItems();
        update.type = UpdateType.AddOrUpdate;
        Map<String, ItemBase> items = new HashMap<>();
        items.put(variantId, targetPacket);
        update.items = items;
        update.updateModels = true;
        update.updateIcons = true;

        playerRef.getPacketHandler().writeNoCache(update);
    }

    private static void sendItemRestore(@Nonnull PlayerRef playerRef, int variantIndex) {
        String variantId = PlaceBlockMetadata.getGreenStateItemId(variantIndex);
        ItemBase originalPacket = originalVariantItemPackets[variantIndex];
        if (originalPacket == null) return;

        UpdateItems update = new UpdateItems();
        update.type = UpdateType.AddOrUpdate;
        Map<String, ItemBase> items = new HashMap<>();
        items.put(variantId, originalPacket);
        update.items = items;
        update.updateModels = true;
        update.updateIcons = true;

        playerRef.getPacketHandler().writeNoCache(update);
    }

    private static void captureOriginalPackets() {
        if (originalVariantPackets != null) return;

        com.hypixel.hytale.protocol.BlockType[] blockPackets = new com.hypixel.hytale.protocol.BlockType[PlaceBlockMetadata.HOTBAR_SIZE];
        ItemBase[] itemPackets = new ItemBase[PlaceBlockMetadata.HOTBAR_SIZE];
        for (int i = 0; i < PlaceBlockMetadata.HOTBAR_SIZE; i++) {
            String variantId = PlaceBlockMetadata.getGreenStateItemId(i);
            BlockType variantType = BlockType.getAssetMap().getAsset(variantId);
            if (variantType == null) {
                LOGGER.severe("[BlockPreviewReskin] Variant block type not found: " + variantId);
                return;
            }
            blockPackets[i] = variantType.toPacket();

            Item variantItem = Item.getAssetMap().getAsset(variantId);
            if (variantItem != null) {
                itemPackets[i] = variantItem.toPacket();
            }
        }
        originalVariantItemPackets = itemPackets;
        originalVariantPackets = blockPackets;
    }
}
