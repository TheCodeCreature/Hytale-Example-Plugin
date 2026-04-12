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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
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
     * If the broken block has a crafting recipe, logs the match.
     */
    public static void onBlockBreak(@Nonnull BreakBlockEvent event) {
        try {
            log("BreakBlockEvent fired | cancelled=" + event.isCancelled()
                    + " blockType=" + event.getBlockType().getId()
                    + " pos=" + event.getTargetBlock());

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
                log("No recipe for blockType=" + blockTypeId);
                return;
            }

            // Log the recipe match and its inputs
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
            log("Recipe FOUND for blockType=" + blockTypeId + " inputs=[" + inputStr + "]");

        } catch (Exception e) {
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Nullable
    private static World findActiveWorld() {
        for (World world : PLAYER_WORLDS.values()) {
            if (world != null) {
                return world;
            }
        }
        return null;
    }

    /**
     * Removes the block at the given position by setting it to empty/air
     * through the chunk's block storage.
     */
    private static void removeBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        try {
            ChunkStore chunkStore = world.getChunkStore();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);

            if (chunkRef == null || !chunkRef.isValid()) {
                log("Chunk not loaded at " + pos);
                return;
            }

            WorldChunk chunk = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
            if (chunk == null) {
                log("Could not get WorldChunk at " + pos);
                return;
            }

            int emptyId = BlockType.getAssetMap().getIndex("Empty");
            BlockType emptyBlockType = BlockType.getAssetMap().getAsset("Empty");

            // Block coordinates within a chunk are local: x & 15, z & 15; y is absolute
            int localX = pos.getX() & 15;
            int localZ = pos.getZ() & 15;

            chunk.setBlock(localX, pos.getY(), localZ, emptyId, emptyBlockType, 0, 0, 0);
            log("Removed block at " + pos);
        } catch (Exception e) {
            log("Error removing block: " + e.getMessage());
        }
    }

    /**
     * Spawns the crafting recipe's input ingredients as item drops
     * at the center of the broken block's position.
     */
    private static void spawnRecipeIngredients(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull CraftingRecipe recipe) {
        try {
            EntityStore entityStore = world.getEntityStore();
            Store<EntityStore> store = entityStore.getStore();

            // Center the drop position on the block
            Vector3d dropPosition = new Vector3d(
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5
            );
            Vector3f rotation = new Vector3f();

            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length == 0) {
                return;
            }

            List<ItemStack> itemStacks = new ArrayList<>();
            for (MaterialQuantity input : inputs) {
                if (input == null) continue;

                ItemStack itemStack = input.toItemStack();
                if (itemStack != null && !itemStack.isEmpty()) {
                    itemStacks.add(itemStack);
                }
            }

            if (itemStacks.isEmpty()) {
                return;
            }

            // Generate item drop entity holders and add them to the world
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                    store, itemStacks, dropPosition, rotation
            );
            for (Holder<EntityStore> holder : holders) {
                if (holder != null) {
                    store.addEntity(holder, AddReason.SPAWN);
                }
            }

            log("Dropped " + itemStacks.size()
                    + " ingredient(s) at " + pos);
        } catch (Exception e) {
            log("Error spawning ingredients: " + e.getMessage());
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
