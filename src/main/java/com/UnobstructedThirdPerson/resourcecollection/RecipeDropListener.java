package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for block break events and drops the crafting recipe ingredients
 * instead of the block itself, if the broken block has a crafting recipe.
 */
public class RecipeDropListener {

    // Stores player refs so we can send chat messages and access worlds
    private static final ConcurrentHashMap<UUID, PlayerRef> PLAYER_REFS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, World> PLAYER_WORLDS = new ConcurrentHashMap<>();

    // Cache mapping resourceTypeId -> resolved item ID
    private static final ConcurrentHashMap<String, Optional<String>> RESOURCE_GROUP_CACHE = new ConcurrentHashMap<>();

    public static void setPlayerWorld(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        PLAYER_REFS.put(playerId, playerRef);
        PLAYER_WORLDS.put(playerId, world);
    }

    public static void removePlayer(@Nonnull UUID playerId) {
        PLAYER_REFS.remove(playerId);
        PLAYER_WORLDS.remove(playerId);
    }

    /**
     * Prints to server console only — safe to call from any thread.
     */
    private static void log(@Nonnull String msg) {
        System.out.println("[RecipeDrop] " + msg);
    }

    /**
     * Sends a message to all online players. Must be called from the world thread.
     */
    private static void broadcast(@Nonnull String msg) {
        Message message = Message.raw(msg);
        for (PlayerRef ref : PLAYER_REFS.values()) {
            ref.sendMessage(message);
        }
    }

    /**
     * ECS event handler for BreakBlockEvent.
     * If the broken block has a crafting recipe, cancels default drops
     * and spawns the recipe's input ingredients instead.
     */
    public static void onBlockBreak(@Nonnull BreakBlockEvent event, @Nonnull Store<EntityStore> store) {
        try {
            if (event.isCancelled()) {
                return;
            }

            BlockType blockType = event.getBlockType();
            String blockTypeId = blockType.getId();

            if ("Empty".equals(blockTypeId) || "Unknown".equals(blockTypeId)) {
                return;
            }

            // Look up recipe for this block type via shared registry
            CraftingRecipe recipe = BlockRecipeRegistry.getRecipeForBlock(blockTypeId);
            if (recipe == null) {
                return;
            }

            // Resolve recipe inputs to droppable ItemStacks
            // Inputs are already scaled by CraftingCostModifier (12x),
            // so we just divide by outputQty to get per-block drops.
            MaterialQuantity[] inputs = recipe.getInput();
            MaterialQuantity primaryOut = recipe.getPrimaryOutput();
            int outputQty = (primaryOut != null && primaryOut.getQuantity() > 0) ? primaryOut.getQuantity() : 1;

            List<ItemStack> ingredients = new ArrayList<>();
            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("Block: ").append(blockTypeId).append(" | Recipe: ").append(recipe.getId())
                    .append(" | outputQty=").append(outputQty);

            if (inputs == null || inputs.length == 0) {
                debugInfo.append("\nInputs: NONE (null or empty array)");
            } else {
                debugInfo.append("\nInputs (").append(inputs.length).append("):");
                for (int i = 0; i < inputs.length; i++) {
                    MaterialQuantity input = inputs[i];
                    if (input == null) {
                        debugInfo.append("\n  [").append(i).append("] NULL entry");
                        continue;
                    }
                    String itemId = input.getItemId();
                    String resId = input.getResourceTypeId();
                    int baseQty = input.getQuantity();
                    int qty = Math.max(1, baseQty / outputQty);
                    int tagIdx = input.getTagIndex();

                    debugInfo.append("\n  [").append(i).append("] itemId=").append(itemId)
                            .append(" resourceTypeId=").append(resId)
                            .append(" tagIndex=").append(tagIdx)
                            .append(" baseQty=").append(baseQty)
                            .append(" scaledQty=").append(qty);

                    // If itemId is set, use it directly
                    if (itemId != null && !"Empty".equals(itemId)) {
                        Item directItem = Item.getAssetMap().getAsset(itemId);
                        if (directItem != null) {
                            ingredients.add(new ItemStack(itemId, qty));
                            debugInfo.append(" -> DROP ").append(itemId);
                        } else {
                            debugInfo.append(" -> ITEM NOT FOUND: ").append(itemId);
                        }
                    } else if (resId != null) {
                        // Resolve resourceTypeId to a specific item via configured strategy
                        String resolvedId = RESOURCE_GROUP_CACHE.computeIfAbsent(resId,
                                RecipeDropListener::resolveByBlockGroup
                        ).orElse(null);

                        if (resolvedId != null) {
                            ingredients.add(new ItemStack(resolvedId, qty));
                            debugInfo.append(" -> GROUP '").append(resId)
                                    .append("' -> DROP ").append(resolvedId);
                        } else {
                            debugInfo.append(" -> NO ITEM IN GROUP '").append(resId).append("'");
                        }
                    } else {
                        debugInfo.append(" -> SKIPPED (no itemId or resourceTypeId)");
                    }
                }
            }

            if (primaryOut != null) {
                debugInfo.append("\nPrimaryOutput: itemId=").append(primaryOut.getItemId())
                        .append(" resourceTypeId=").append(primaryOut.getResourceTypeId())
                        .append(" qty=").append(primaryOut.getQuantity());
            }

            debugInfo.append("\nIngredient stacks resolved: ").append(ingredients.size());
            String chatMessage = debugInfo.toString();

            if (ingredients.isEmpty()) {
                // Still send debug so we can see why nothing resolved
                World debugWorld = store.getExternalData().getWorld();
                debugWorld.execute(() -> broadcast(chatMessage + "\n*** NO DROPS — all inputs unresolved ***"));
                return;
            }

            // Cancel default break behavior (prevents normal drops AND block removal)
            event.setCancelled(true);

            // Capture position before deferring
            Vector3i pos = event.getTargetBlock();
            int bx = pos.getX();
            int by = pos.getY();
            int bz = pos.getZ();

            // Defer world modifications to after the ECS tick completes
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                try {
                    // Send debug info to chat
                    broadcast(chatMessage);
                    flushDeferredBroadcasts();

                    ChunkStore chunkStore = world.getChunkStore();
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
                    Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);

                    if (chunkRef == null || !chunkRef.isValid()) {
                        return;
                    }

                    Store<EntityStore> esStore = world.getEntityStore().getStore();
                    Store<ChunkStore> csStore = chunkStore.getStore();

                    // Break block with particles + sounds, but suppress item drops
                    BlockHarvestUtils.naturallyRemoveBlock(
                            new Vector3i(bx, by, bz),
                            blockType,
                            0,     // filler
                            0,     // quantity (irrelevant — drops suppressed)
                            null,  // itemId
                            null,  // dropListId
                            SetBlockSettings.PERFORM_BLOCK_UPDATE | SetBlockSettings.NO_DROP_ITEMS,
                            chunkRef,
                            esStore,
                            csStore
                    );

                    // Spawn recipe input ingredients as item drops at the block center
                    Vector3d dropPos = new Vector3d(bx + 0.5, by + 0.5, bz + 0.5);
                    broadcast(chatMessage + "\nSpawning " + ingredients.size() + " stack(s) at " + dropPos);
                    Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                            esStore, ingredients, dropPos, new Vector3f()
                    );
                    broadcast("generateItemDrops returned " + holders.length + " holder(s)");
                    int spawned = 0;
                    for (Holder<EntityStore> holder : holders) {
                        if (holder != null) {
                            esStore.addEntity(holder, AddReason.SPAWN);
                            spawned++;
                        }
                    }
                    broadcast("Spawned " + spawned + " item entities");
                } catch (Exception e) {
                    log("Deferred action error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });

        } catch (Exception e) {
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Clears the recipe cache so lookups will be recomputed.
     * Useful if asset packs are reloaded.
     */
    public static void invalidateCache() {
        RESOURCE_GROUP_CACHE.clear();
    }

    /**
     * Resolves a resourceTypeId to a specific item by looking up the corresponding BlockGroup.
     * Replaces the prefix of resId (before first '_') with "FullBlocks" and looks up in BlockGroup asset registry.
     * The group's blocks array contains block type IDs — find the first one that
     * maps to a valid item and return that item ID.
     * Prints the full group list to chat for debugging.
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    private static Optional<String> resolveByBlockGroup(@Nonnull String resId) {
        int underscoreIdx = resId.indexOf('_');
        String groupName = underscoreIdx >= 0
                ? "FullBlocks" + resId.substring(underscoreIdx)
                : "FullBlocks_" + resId;
        DefaultAssetMap<String, BlockGroup> blockGroupMap =
                (DefaultAssetMap<String, BlockGroup>) AssetRegistry.getAssetStore(BlockGroup.class).getAssetMap();
        BlockGroup group = blockGroupMap.getAsset(groupName);

        StringBuilder groupDebug = new StringBuilder();
        groupDebug.append("\n[BlockGroup] Looking up '").append(groupName).append("'");

        if (group == null) {
            groupDebug.append(" -> NOT FOUND");
            // Defer broadcast so it's on the world thread
            broadcastDeferred(groupDebug.toString());
            return Optional.empty();
        }

        groupDebug.append(" -> FOUND (").append(group.size()).append(" blocks):");
        for (int i = 0; i < group.size(); i++) {
            String blockId = group.get(i);
            groupDebug.append("\n  [").append(i).append("] ").append(blockId);
        }

        // The block group contains block type IDs. Find the first one that
        // has a corresponding item (items reference blocks via getBlockId()).
        String resolvedItemId = null;
        for (int i = 0; i < group.size(); i++) {
            String blockId = group.get(i);
            // Try blockId directly as an item ID first
            Item item = Item.getAssetMap().getAsset(blockId);
            if (item != null) {
                resolvedItemId = blockId;
                groupDebug.append("\n  -> Selected [").append(i).append("] ").append(blockId).append(" (direct item match)");
                break;
            }
        }

        if (resolvedItemId == null) {
            // Search all items for one whose blockId matches the first group entry
            for (int i = 0; i < group.size(); i++) {
                String blockId = group.get(i);
                for (Map.Entry<String, Item> entry : Item.getAssetMap().getAssetMap().entrySet()) {
                    if (entry.getValue() != null && blockId.equals(entry.getValue().getBlockId())) {
                        resolvedItemId = entry.getKey();
                        groupDebug.append("\n  -> Selected [").append(i).append("] item=").append(resolvedItemId)
                                .append(" (block=").append(blockId).append(")");
                        break;
                    }
                }
                if (resolvedItemId != null) break;
            }
        }

        if (resolvedItemId == null) {
            groupDebug.append("\n  -> NO MATCHING ITEM FOUND");
        }

        broadcastDeferred(groupDebug.toString());
        return Optional.ofNullable(resolvedItemId);
    }

    /**
     * Queues a broadcast for the next world.execute() cycle. Used when resolving
     * from computeIfAbsent (not yet on the world thread).
     */
    private static final List<String> PENDING_BROADCASTS = new ArrayList<>();

    private static void broadcastDeferred(@Nonnull String msg) {
        synchronized (PENDING_BROADCASTS) {
            PENDING_BROADCASTS.add(msg);
        }
    }

    private static void flushDeferredBroadcasts() {
        synchronized (PENDING_BROADCASTS) {
            for (String msg : PENDING_BROADCASTS) {
                broadcast(msg);
            }
            PENDING_BROADCASTS.clear();
        }
    }
}
