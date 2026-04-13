package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Handles naturally-spawned blocks (not player-placed, not craftable).
 * Multiplies their drops by {@link ResourceConstants#RESOURCE_MULTIPLIER}.
 *
 * A block is considered "natural" if:
 *   1. It has no crafting recipe (excluding Salvage recipes)
 *   2. It was NOT placed by a player (BlockPhysics deco flag is not set)
 *
 * Player-placed natural blocks drop at normal (1x) rates.
 */
public class NaturalResourceDropListener {

    // Max blocks the pre-emptive cascade BFS will break in one pass
    private static final int MAX_CASCADE_BLOCKS = 300;

    private static void log(@Nonnull String msg) {
        System.out.println("[NaturalDrop] " + msg);
    }

    private record DropInfo(String itemId, String dropListId, int quantity) {
        static final DropInfo EMPTY = new DropInfo(null, null, 1);
    }

    /**
     * Resolves what a block would drop based on its BlockGathering config.
     * When physicsFirst is true, uses physics cascade priority: physics > breaking > soft > harvest.
     * Otherwise uses player-break priority: breaking > soft > harvest.
     */
    @Nonnull
    private static DropInfo getDropInfo(@Nonnull BlockType blockType, boolean physicsFirst) {
        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) return DropInfo.EMPTY;

        if (physicsFirst) {
            PhysicsDropType physics = gathering.getPhysics();
            if (physics != null) {
                return new DropInfo(physics.getItemId(), physics.getDropListId(), 1);
            }
        }

        BlockBreakingDropType breaking = gathering.getBreaking();
        if (breaking != null) {
            return new DropInfo(breaking.getItemId(), breaking.getDropListId(), breaking.getQuantity());
        }

        SoftBlockDropType soft = gathering.getSoft();
        if (soft != null) {
            return new DropInfo(soft.getItemId(), soft.getDropListId(), 1);
        }

        HarvestingDropType harvest = gathering.getHarvest();
        if (harvest != null) {
            return new DropInfo(harvest.getItemId(), harvest.getDropListId(), 1);
        }

        return DropInfo.EMPTY;
    }

    /**
     * Checks if a block at the given position is marked as deco (player-placed).
     */
    private static boolean isDeco(int x, int y, int z,
                                  @Nonnull Ref<ChunkStore> chunkRef,
                                  @Nonnull Store<ChunkStore> csStore) {
        ChunkColumn col = csStore.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (col == null) return false;
        Ref<ChunkStore> secRef = col.getSection(ChunkUtil.chunkCoordinate(y));
        if (secRef == null || !secRef.isValid()) return false;
        BlockPhysics phys = csStore.getComponent(secRef, BlockPhysics.getComponentType());
        return phys != null && phys.isDeco(x, y, z);
    }

    /**
     * ECS event handler for BreakBlockEvent.
     * If the block is a natural (non-craftable, non-player-placed) resource,
     * cancels default drops and spawns multiplied drops instead.
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

            // Skip blocks that are not natural — RecipeDropListener handles those
            if (!NaturalResourceRegistry.isNaturalBlock(blockTypeId)) {
                return;
            }

            // Cancel default break so we control everything
            event.setCancelled(true);

            Vector3i pos = event.getTargetBlock();
            int bx = pos.getX();
            int by = pos.getY();
            int bz = pos.getZ();

            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                try {
                    // Check if the block was placed by a player
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
                    ChunkStore chunkStore = world.getChunkStore();
                    Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
                    if (chunkRef == null || !chunkRef.isValid()) {
                        return;
                    }

                    Store<ChunkStore> csStore = chunkStore.getStore();
                    Store<EntityStore> esStore = world.getEntityStore().getStore();

                    if (isDeco(bx, by, bz, chunkRef, csStore)) {
                        // Player-placed: break with normal (1x) drops
                        BlockHarvestUtils.naturallyRemoveBlock(
                                new Vector3i(bx, by, bz),
                                blockType,
                                0, 1, null, null,
                                SetBlockSettings.PERFORM_BLOCK_UPDATE,
                                chunkRef, esStore, csStore
                        );
                        log(blockTypeId + " at [" + bx + "," + by + "," + bz + "] was player-placed -> 1x drops");
                    } else {
                        // Natural: suppress default drops, spawn multiplied drops
                        DropInfo drop = getDropInfo(blockType, false);

                        List<ItemStack> baseDrops = BlockHarvestUtils.getDrops(
                                blockType, drop.quantity(), drop.itemId(), drop.dropListId());

                        // Break block with no drops
                        BlockHarvestUtils.naturallyRemoveBlock(
                                new Vector3i(bx, by, bz),
                                blockType,
                                0, 0, null, null,
                                SetBlockSettings.PERFORM_BLOCK_UPDATE | SetBlockSettings.NO_DROP_ITEMS,
                                chunkRef, esStore, csStore
                        );

                        if (!baseDrops.isEmpty()) {
                            // Multiply each drop stack
                            int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;
                            List<ItemStack> multipliedDrops = baseDrops.stream()
                                    .map(s -> new ItemStack(s.getItemId(), s.getQuantity() * multiplier))
                                    .toList();

                            Vector3d dropPos = new Vector3d(bx + 0.5, by + 0.5, bz + 0.5);
                            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                                    esStore, multipliedDrops, dropPos, new Vector3f());
                            for (Holder<EntityStore> holder : holders) {
                                if (holder != null) {
                                    esStore.addEntity(holder, AddReason.SPAWN);
                                }
                            }
                            log(blockTypeId + " at [" + bx + "," + by + "," + bz + "] -> " + multiplier
                                    + "x drops (" + multipliedDrops.size() + " stacks)");
                        } else {
                            log(blockTypeId + " at [" + bx + "," + by + "," + bz + "] -> no drops configured");
                        }

                        // Pre-emptively break connected natural blocks before physics cascade
                        preemptivelyBreakConnected(
                                world, bx, by, bz, esStore, csStore, chunkStore);
                    }
                } catch (Exception e) {
                    log("Deferred error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ========================= Pre-emptive Cascade =========================

    private static final int[][] NEIGHBORS = {{0,1,0},{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}};

    /**
     * BFS outward from the break origin. Finds all connected natural (non-deco,
     * non-craftable) blocks at or above the break Y level, breaks them with
     * NO_DROP_ITEMS, and spawns multiplied drops ourselves.
     *
     * Runs entirely within one world.execute() callback, so the physics system
     * has no chance to cascade before we finish — guaranteeing 12x drops.
     * Only expands to blocks at y >= originY to avoid collapsing cave walls.
     */
    private static void preemptivelyBreakConnected(
            @Nonnull World world, int originX, int originY, int originZ,
            @Nonnull Store<EntityStore> esStore, @Nonnull Store<ChunkStore> csStore,
            @Nonnull ChunkStore chunkStore) {

        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;
        Set<Long> visited = new HashSet<>();
        Queue<int[]> queue = new ArrayDeque<>();

        // Seed with the 6 neighbors of the broken block
        visited.add(packPos(originX, originY, originZ));
        for (int[] off : NEIGHBORS) {
            int nx = originX + off[0], ny = originY + off[1], nz = originZ + off[2];
            if (ny >= originY) { // only at or above break point
                queue.add(new int[]{nx, ny, nz});
            }
        }

        int broken = 0;
        while (!queue.isEmpty() && broken < MAX_CASCADE_BLOCKS) {
            int[] pos = queue.poll();
            int x = pos[0], y = pos[1], z = pos[2];
            if (y < 0 || y >= 320) continue;
            if (!visited.add(packPos(x, y, z))) continue;

            long ci = ChunkUtil.indexChunkFromBlock(x, z);
            Ref<ChunkStore> cRef = chunkStore.getChunkReference(ci);
            if (cRef == null || !cRef.isValid()) continue;

            WorldChunk wc = csStore.getComponent(cRef, WorldChunk.getComponentType());
            if (wc == null) continue;

            BlockType bt = wc.getBlockType(x, y, z);
            if (bt == null) continue;
            String btId = bt.getId();
            if ("Empty".equals(btId) || "Unknown".equals(btId)) continue;
            if (!NaturalResourceRegistry.isNaturalBlock(btId)) continue;

            // Skip deco (player-placed) blocks
            if (isDeco(x, y, z, cRef, csStore)) continue;

            // Natural, non-deco, non-craftable block — break it with 12x drops
            DropInfo drop = getDropInfo(bt, true);

            List<ItemStack> baseDrops = BlockHarvestUtils.getDrops(
                    bt, drop.quantity(), drop.itemId(), drop.dropListId());

            BlockHarvestUtils.naturallyRemoveBlock(
                    new Vector3i(x, y, z), bt,
                    0, 0, null, null,
                    SetBlockSettings.PERFORM_BLOCK_UPDATE | SetBlockSettings.NO_DROP_ITEMS,
                    cRef, esStore, csStore
            );

            if (!baseDrops.isEmpty()) {
                List<ItemStack> multiplied = baseDrops.stream()
                        .map(s -> new ItemStack(s.getItemId(), s.getQuantity() * multiplier))
                        .toList();
                Vector3d dropPos = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
                Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                        esStore, multiplied, dropPos, new Vector3f());
                for (Holder<EntityStore> holder : holders) {
                    if (holder != null) {
                        esStore.addEntity(holder, AddReason.SPAWN);
                    }
                }
            }

            broken++;

            // Expand BFS to neighbors at or above the break point
            for (int[] off : NEIGHBORS) {
                int nx = x + off[0], ny = y + off[1], nz = z + off[2];
                if (ny >= originY) {
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        if (broken > 0) {
            log("Pre-emptive cascade: broke " + broken + " connected natural blocks with "
                    + multiplier + "x drops");
        }
    }

    private static long packPos(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
