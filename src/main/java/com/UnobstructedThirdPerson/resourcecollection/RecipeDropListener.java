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
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
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

    // Cache mapping block type ID -> crafting recipe (built lazily on first block break)
    private static final ConcurrentHashMap<String, CraftingRecipe> BLOCK_RECIPE_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean cacheBuilt = false;

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
     * ECS event handler for BreakBlockEvent.
     * If the broken block has a crafting recipe, cancels default drops
     * and spawns a Rock_Stone instead (deferred to after ECS tick).
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

            // Lazily build the recipe cache on first use
            if (!cacheBuilt) {
                buildRecipeCache();
            }

            CraftingRecipe recipe = BLOCK_RECIPE_CACHE.get(blockTypeId);
            if (recipe == null) {
                return;
            }

            log("Recipe match for " + blockTypeId + " — cancelling default drop, will spawn Rock_Stone");

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
                    ChunkStore chunkStore = world.getChunkStore();
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
                    Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);

                    if (chunkRef == null || !chunkRef.isValid()) {
                        log("Chunk not loaded at " + bx + "," + by + "," + bz);
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

                    // Spawn a Rock_Stone item drop at the block center
                    spawnItem(world, bx + 0.5, by + 0.5, bz + 0.5, "Rock_Stone", 1);

                    log("Dropped Rock_Stone at " + bx + "," + by + "," + bz);
                } catch (Exception e) {
                    log("Deferred action error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });

        } catch (Exception e) {
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Spawns an item drop entity at the given position.
     * Must be called on the world thread.
     */
    private static void spawnItem(@Nonnull World world, double x, double y, double z,
                                  @Nonnull String itemId, int quantity) {
        EntityStore entityStore = world.getEntityStore();
        Store<EntityStore> store = entityStore.getStore();

        ItemStack itemStack = new ItemStack(itemId, quantity);
        Vector3d position = new Vector3d(x, y, z);
        Vector3f rotation = new Vector3f();

        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                store, itemStack, position, rotation, 0.0f, 3.25f, 0.0f
        );
        if (holder != null) {
            store.addEntity(holder, AddReason.SPAWN);
        }
    }

    /**
     * Builds a lookup cache from block type ID -> CraftingRecipe.
     * Iterates all crafting recipes and items to find blocks that can be crafted.
     */
    private static synchronized void buildRecipeCache() {
        if (cacheBuilt) {
            return;
        }

        try {
            Map<String, CraftingRecipe> recipes = CraftingRecipe.getAssetMap().getAssetMap();
            log("Total crafting recipes: " + (recipes == null ? "null" : recipes.size()));

            if (recipes == null || recipes.isEmpty()) {
                log("No crafting recipes found!");
                cacheBuilt = true;
                return;
            }

            int totalRecipes = 0;
            int withPrimaryOutput = 0;
            int withItem = 0;
            int withBlockType = 0;

            for (Map.Entry<String, CraftingRecipe> entry : recipes.entrySet()) {
                CraftingRecipe recipe = entry.getValue();
                totalRecipes++;
                if (recipe == null) continue;

                MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
                if (primaryOutput == null) continue;

                String outputItemId = primaryOutput.getItemId();
                if (outputItemId == null) continue;
                withPrimaryOutput++;

                // Look up the item to see if it places a block
                Item item = Item.getAssetMap().getAsset(outputItemId);
                if (item == null) {
                    continue;
                }
                withItem++;

                if (item.hasBlockType()) {
                    withBlockType++;
                    String blockId = item.getBlockId();
                    if (blockId != null && !"Empty".equals(blockId)) {
                        BLOCK_RECIPE_CACHE.putIfAbsent(blockId, recipe);
                        // Log each input for this recipe
                        MaterialQuantity[] inputs = recipe.getInput();
                        StringBuilder inputStr = new StringBuilder();
                        if (inputs != null) {
                            for (MaterialQuantity mq : inputs) {
                                if (mq != null) {
                                    inputStr.append(mq.getItemId() != null ? mq.getItemId() : mq.getResourceTypeId())
                                            .append(" x").append(mq.getQuantity()).append(", ");
                                }
                            }
                        }
                        log("MAPPED: " + blockId
                                + " <- recipe=" + entry.getKey()
                                + " inputs=[" + inputStr + "]");
                    }
                }
            }

            cacheBuilt = true;
            log("Cache: " + totalRecipes + " recipes, "
                    + withPrimaryOutput + " w/output, "
                    + withItem + " w/item, "
                    + withBlockType + " block-placing, "
                    + BLOCK_RECIPE_CACHE.size() + " mapped");
        } catch (Exception e) {
            log("Cache build error: " + e.getMessage());
            cacheBuilt = true; // Prevent repeated failures
        }
    }

    /**
     * Clears the recipe cache so it will be rebuilt on next block break.
     * Useful if asset packs are reloaded.
     */
    public static void invalidateCache() {
        BLOCK_RECIPE_CACHE.clear();
        cacheBuilt = false;
    }
}
