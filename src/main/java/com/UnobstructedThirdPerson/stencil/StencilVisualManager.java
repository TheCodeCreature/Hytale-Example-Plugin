package com.UnobstructedThirdPerson.stencil;

import com.UnobstructedThirdPerson.placeblock.PlaceBlockCostUtil;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemTranslationProperties;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItemQualities;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages per-player visual overrides for blueprint stencil items in the hotbar.
 *
 * <p>Provides two visual features via {@code UpdateItems} packets:
 * <ul>
 *   <li><b>Visual Identity:</b> Overrides the item's display name to
 *       {@code "[Stencil] {Block Name}"} so players can distinguish stencils
 *       from normal block items.</li>
 *   <li><b>Affordability Indicator:</b> Swaps the item's {@code qualityIndex}
 *       between {@code Stencil_Affordable} (blue/green glow) and
 *       {@code Stencil_Unaffordable} (red glow) based on whether the player
 *       can currently afford the recipe's material cost.</li>
 * </ul>
 *
 * <p><b>State Tracking:</b> Maintains a {@link PlayerVisualState} per player
 * that records the last-sent affordability state for each stencil item type.
 * Packets are only sent when state actually changes, avoiding redundant
 * network traffic on every hotbar mutation.
 *
 * <p><b>Integration:</b> This class is a static utility invoked from three places:
 * <ol>
 *   <li>{@code StencilSyncSystem.restoreStencils()} — piggybacked on the
 *       existing hotbar change listener to refresh affordability after every
 *       inventory mutation</li>
 *   <li>{@code UnobstructedThirdPersonPlugin.onPlayerReady()} — applies initial
 *       visuals on connect for any stencils already in the hotbar</li>
 *   <li>{@code UnobstructedThirdPersonPlugin.onPlayerDisconnect()} — cleans up
 *       per-player state</li>
 * </ol>
 *
 * <p><b>Threading:</b> All public methods are safe to call from the server
 * thread. The internal {@code ConcurrentHashMap} guards against concurrent
 * access during player connect/disconnect races, but visual refresh calls
 * are expected to be sequential per-player (driven by the single hotbar
 * change listener).
 *
 * <p><b>Packet Details:</b> Uses {@code UpdateItems} with
 * {@code updateModels=false} and {@code updateIcons=false} to keep packets
 * lightweight — only the {@code qualityIndex} and {@code translationProperties}
 * fields are meaningful. No 3D model or icon atlas rebuild is triggered.
 *
 * @see StencilMetadata
 * @see StencilSyncSystem
 */
public final class StencilVisualManager {

    private static final Logger LOGGER = Logger.getLogger("StencilVisualManager");

    /** Quality asset ID for affordable stencils (green slot glow). */
    private static final String QUALITY_AFFORDABLE = "Stencil_Affordable";

    /** Quality asset ID for unaffordable stencils (red slot glow). */
    private static final String QUALITY_UNAFFORDABLE = "Stencil_Unaffordable";

    /** Display name prefix prepended to the block name. */
    private static final String STENCIL_NAME_PREFIX = "[Stencil] ";

    /**
     * Per-player visual state. Key is the player's UUID.
     * Entries are added on {@link #applyVisuals} and removed on {@link #removePlayer}.
     */
    private static final ConcurrentHashMap<UUID, PlayerVisualState> playerStates = new ConcurrentHashMap<>();

    private StencilVisualManager() {}

    /**
     * Initializes visual overrides for a player who just connected.
     *
     * <p>Scans the player's hotbar for stencil items, evaluates affordability
     * for each, builds initial {@link PlayerVisualState}, and sends a single
     * {@code UpdateItems} packet with all overrides. If no stencils are in
     * the hotbar, state is initialized empty and no packet is sent.
     *
     * <p>Must be called <b>after</b> {@code StencilSyncSystem.register()} so
     * that stencil quantities have been restored before scanning.
     *
     * @param playerRef the player's network reference, used to send packets
     * @param player    the player entity, used to access inventory
     */
    public static void applyVisuals(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        LOGGER.info("[StencilVisual] applyVisuals called for " + playerRef.getUuid());
        int affIdx = ItemQuality.getAssetMap().getIndexOrDefault(QUALITY_AFFORDABLE, -1);
        int unaffIdx = ItemQuality.getAssetMap().getIndexOrDefault(QUALITY_UNAFFORDABLE, -1);
        LOGGER.info("[StencilVisual] Quality indices: affordable=" + affIdx + ", unaffordable=" + unaffIdx);

        // Send custom quality definitions to the client — the init packet may not include
        // plugin-loaded qualities if the packet generator cached before our asset pack loaded.
        sendCustomQualities(playerRef);

        PlayerVisualState state = new PlayerVisualState(playerRef.getUuid());
        playerStates.put(playerRef.getUuid(), state);
        scanAndSend(playerRef, player, state);
    }

    /**
     * Removes all tracked visual state for a disconnecting player.
     *
     * <p>No packet is sent — the client session is ending. This only cleans
     * up the server-side tracking map to prevent memory leaks.
     *
     * @param uuid the UUID of the disconnecting player
     */
    public static void removePlayer(@Nonnull UUID uuid) {
        playerStates.remove(uuid);
    }

    /**
     * Sends an {@code UpdateItemQualities} packet to ensure the client knows
     * about our custom quality definitions. The engine's init packet may not
     * include plugin-loaded qualities if the packet generator cached before
     * our asset pack was registered.
     */
    private static void sendCustomQualities(@Nonnull PlayerRef playerRef) {
        var assetMap = ItemQuality.getAssetMap();
        String[] customIds = { QUALITY_AFFORDABLE, QUALITY_UNAFFORDABLE };

        UpdateItemQualities packet = new UpdateItemQualities();
        packet.type = UpdateType.AddOrUpdate;
        packet.itemQualities = new HashMap<>();

        for (String qualityId : customIds) {
            ItemQuality quality = assetMap.getAsset(qualityId);
            if (quality == null) {
                LOGGER.warning("[StencilVisual] Custom quality not found: " + qualityId);
                continue;
            }
            int index = assetMap.getIndexOrDefault(qualityId, -1);
            if (index < 0) continue;
            packet.itemQualities.put(index, quality.toPacket());
        }

        packet.maxId = assetMap.getNextIndex();

        if (!packet.itemQualities.isEmpty()) {
            LOGGER.info("[StencilVisual] Sending UpdateItemQualities with " + packet.itemQualities.size()
                    + " custom qualities (maxId=" + packet.maxId + ")");
            playerRef.getPacketHandler().writeNoCache(packet);
        }
    }

    /**
     * Re-evaluates affordability for all stencil items in a player's hotbar
     * and sends an {@code UpdateItems} packet if any state changed.
     *
     * <p>This is the primary update path, called from
     * {@code StencilSyncSystem.restoreStencils()} on every hotbar change.
     * It compares current affordability against the last-sent state and only
     * sends a packet if at least one item's affordability flipped.
     *
     * <p>If the player has no tracked state (e.g., called before
     * {@link #applyVisuals}), this method is a no-op.
     *
     * @param playerRef the player's network reference, used to send packets
     * @param player    the player entity, used to access inventory
     */
    public static void refreshAffordability(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        PlayerVisualState state = playerStates.get(playerRef.getUuid());
        if (state == null) return;
        scanAndSend(playerRef, player, state);
    }

    /**
     * Core scan-and-send logic shared by {@link #applyVisuals} and
     * {@link #refreshAffordability}.
     *
     * <p>Iterates every slot in the player's hotbar. For each stencil item:
     * <ol>
     *   <li>Reads the recipe ID from BSON metadata via {@link StencilMetadata}</li>
     *   <li>Resolves the {@link CraftingRecipe} and computes per-unit cost</li>
     *   <li>Checks affordability via {@code canRemoveMaterials} on the combined
     *       backpack+storage+hotbar container</li>
     *   <li>Calls {@link PlayerVisualState#updateItem} — returns {@code true}
     *       if the affordability state changed (or is new)</li>
     *   <li>If changed, queues the item type for inclusion in the
     *       {@code UpdateItems} packet</li>
     * </ol>
     *
     * <p>Also detects stencil items that were removed from the hotbar since
     * the last scan and clears their tracked state (no packet needed — the
     * engine already restored the default item appearance when the stack was
     * removed).
     *
     * <p>After scanning, if any items changed, calls {@link #buildUpdatePacket}
     * and sends via {@code writeNoCache}.
     *
     * @param playerRef the player's network reference
     * @param player    the player entity
     * @param state     the player's current visual tracking state
     */
    private static void scanAndSend(@Nonnull PlayerRef playerRef,
                                    @Nonnull Player player,
                                    @Nonnull PlayerVisualState state) {
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer container = player.getInventory().getCombinedBackpackStorageHotbar();

        Set<String> currentStencils = new HashSet<>();
        Map<String, ItemVisualState> changedItems = new HashMap<>();

        LOGGER.info("[StencilVisual] scanAndSend: hotbar capacity=" + hotbar.getCapacity());
        short capacity = hotbar.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null) continue;
            if (!StencilMetadata.isStencil(stack)) {
                LOGGER.fine("[StencilVisual] slot " + slot + ": not a stencil (" + stack.getItemId() + ")");
                continue;
            }

            String recipeId = StencilMetadata.getRecipeId(stack);
            if (recipeId == null) continue;

            String itemId = stack.getItemId();
            LOGGER.info("[StencilVisual] slot " + slot + ": stencil found — itemId=" + itemId + ", recipeId=" + recipeId);
            currentStencils.add(itemId);

            CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
            if (recipe == null) continue;

            List<MaterialQuantity> materials = PlaceBlockCostUtil.getPerUnitCost(recipe);
            boolean affordable = materials.isEmpty() || container.canRemoveMaterials(materials);

            if (state.updateItem(itemId, affordable)) {
                changedItems.put(itemId, state.getTrackedItems().get(itemId));
            }
        }

        state.getTrackedItems().keySet().removeIf(key -> !currentStencils.contains(key));

        LOGGER.info("[StencilVisual] scanAndSend: found " + currentStencils.size() + " stencils, " + changedItems.size() + " changed");
        if (!changedItems.isEmpty()) {
            UpdateItems packet = buildUpdatePacket(changedItems);
            if (packet != null) {
                LOGGER.info("[StencilVisual] Sending UpdateItems with " + packet.items.size() + " item overrides");
                playerRef.getPacketHandler().writeNoCache(packet);
            } else {
                LOGGER.warning("[StencilVisual] buildUpdatePacket returned null despite " + changedItems.size() + " changed items");
            }
        }
    }

    /**
     * Constructs a single batched {@code UpdateItems} packet containing
     * overrides for all changed stencil item types.
     *
     * <p>For each entry in {@code changedItems}:
     * <ul>
     *   <li>Retrieves the server {@link Item} asset and converts to
     *       {@link ItemBase} via {@code item.toPacket()}</li>
     *   <li>Overrides {@code qualityIndex} based on affordability</li>
     *   <li>Overrides {@code translationProperties.name} to
     *       {@code "[Stencil] {Block Name}"}</li>
     *   <li>Adds to the packet's {@code items} map keyed by item ID</li>
     * </ul>
     *
     * <p>The packet is configured with {@code updateModels=false} and
     * {@code updateIcons=false} to avoid triggering expensive client-side
     * rebuilds.
     *
     * @param changedItems map of item ID → current visual state for items that changed
     * @return the constructed packet, or {@code null} if {@code changedItems} is empty
     */
    @Nullable
    private static UpdateItems buildUpdatePacket(@Nonnull Map<String, ItemVisualState> changedItems) {
        if (changedItems.isEmpty()) return null;

        UpdateItems update = new UpdateItems();
        update.type = UpdateType.AddOrUpdate;
        update.items = new HashMap<>();
        update.updateModels = false;
        update.updateIcons = false;

        for (Map.Entry<String, ItemVisualState> entry : changedItems.entrySet()) {
            String itemId = entry.getKey();
            ItemVisualState vis = entry.getValue();

            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) {
                LOGGER.warning("[StencilVisual] Item asset not found for: " + itemId);
                continue;
            }

            ItemBase packet = item.toPacket();
            LOGGER.info("[StencilVisual] Override: " + itemId + " qualityIndex=" + vis.getQualityIndex() + " name=" + resolveDisplayName(itemId));
            packet.qualityIndex = vis.getQualityIndex();
            packet.translationProperties = new ItemTranslationProperties();
            packet.translationProperties.name = resolveDisplayName(itemId);

            update.items.put(itemId, packet);
        }

        return update.items.isEmpty() ? null : update;
    }

    /**
     * Resolves the {@code qualityIndex} for the {@code UpdateItems} packet
     * based on the affordability flag.
     *
     * <p>Uses {@code ItemQuality.getAssetMap().getIndexOrDefault()} to look up
     * the index of either {@value #QUALITY_AFFORDABLE} or
     * {@value #QUALITY_UNAFFORDABLE}. Falls back to index 0 (Default quality)
     * if the custom quality asset is not registered.
     *
     * @param affordable true if the player can afford the recipe
     * @return the quality index for the packet
     */
    private static int resolveQualityIndex(boolean affordable) {
        String qualityId = affordable ? QUALITY_AFFORDABLE : QUALITY_UNAFFORDABLE;
        return ItemQuality.getAssetMap().getIndexOrDefault(qualityId, 0);
    }

    /**
     * Builds the display name for a stencil item.
     *
     * <p>Format: {@code "[Stencil] Oak Planks"} — the item ID is converted
     * from snake_case ({@code "Oak_Planks"}) to space-separated title case.
     * This is a raw string, not a localization key — the client renders it
     * verbatim.
     *
     * @param itemId the item type key (e.g., {@code "Oak_Planks"})
     * @return the formatted display name
     */
    @Nonnull
    private static String resolveDisplayName(@Nonnull String itemId) {
        return STENCIL_NAME_PREFIX + itemId.replace('_', ' ');
    }

    // ── Inner classes ──────────────────────────────────────────────

    /**
     * Tracks the last-sent visual state for all stencil items of a single player.
     *
     * <p>Keyed by item type ID (e.g., {@code "Oak_Planks"}). Each entry records
     * whether the item was last sent as affordable or unaffordable. This allows
     * {@link StencilVisualManager#scanAndSend} to detect state changes and
     * skip redundant packets.
     *
     * <p>Not thread-safe — accessed only from the server thread via the hotbar
     * change listener.
     */
    static final class PlayerVisualState {

        private final UUID playerId;
        private final Map<String, ItemVisualState> trackedItems = new HashMap<>();

        PlayerVisualState(@Nonnull UUID playerId) {
            this.playerId = playerId;
        }

        @Nonnull
        UUID getPlayerId() {
            return playerId;
        }

        /**
         * Returns the current tracked items map.
         *
         * @return unmodifiable view of tracked item states
         */
        @Nonnull
        Map<String, ItemVisualState> getTrackedItems() {
            return trackedItems;
        }

        /**
         * Updates the tracked state for an item type and returns whether
         * the state changed.
         *
         * <p>If the item is not yet tracked, creates a new entry and returns
         * {@code true}. If already tracked, returns {@code true} only if
         * the affordability flag differs from the last-sent state.
         *
         * @param itemId     the item type key
         * @param affordable whether the player can currently afford the recipe
         * @return {@code true} if this is a new item or affordability changed
         */
        boolean updateItem(@Nonnull String itemId, boolean affordable) {
            ItemVisualState existing = trackedItems.get(itemId);
            if (existing == null) {
                int qualityIndex = resolveQualityIndex(affordable);
                trackedItems.put(itemId, new ItemVisualState(itemId, affordable, qualityIndex));
                return true;
            }
            if (existing.isAffordable() != affordable) {
                int qualityIndex = resolveQualityIndex(affordable);
                existing.setAffordable(affordable);
                existing.setQualityIndex(qualityIndex);
                return true;
            }
            return false;
        }

        /**
         * Removes all tracked items. Used during cleanup.
         */
        void clear() {
            trackedItems.clear();
        }
    }

    /**
     * Immutable snapshot of the last-sent visual state for a single stencil
     * item type.
     *
     * <p>Records the item ID, affordability flag, and resolved quality index
     * at the time the last {@code UpdateItems} packet was sent.
     */
    static final class ItemVisualState {

        private final String itemId;
        private boolean affordable;
        private int qualityIndex;

        ItemVisualState(@Nonnull String itemId, boolean affordable, int qualityIndex) {
            this.itemId = itemId;
            this.affordable = affordable;
            this.qualityIndex = qualityIndex;
        }

        @Nonnull
        String getItemId() {
            return itemId;
        }

        boolean isAffordable() {
            return affordable;
        }

        int getQualityIndex() {
            return qualityIndex;
        }

        void setAffordable(boolean affordable) {
            this.affordable = affordable;
        }

        void setQualityIndex(int qualityIndex) {
            this.qualityIndex = qualityIndex;
        }
    }
}
