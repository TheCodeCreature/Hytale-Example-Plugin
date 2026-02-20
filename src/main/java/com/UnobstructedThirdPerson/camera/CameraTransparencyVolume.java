package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Makes blocks transparent in an arbitrary volume around the camera origin.
 * Uses a diff-based cache: only sends packets for blocks entering/leaving the volume
 * when the camera crosses a whole-number coordinate boundary.
 * Accepts any {@link Shape} (Ellipsoid, Cylinder, Box, etc.).
 */
public class CameraTransparencyVolume {

    private static final Logger LOGGER = Logger.getLogger("CameraTransparencyVolume");
    private static final int FEET_Y_OFFSET = 1;
    private static final long UPDATE_INTERVAL_MILLIS = 100;

    private static final Map<UUID, CameraTransparencyVolume> INSTANCES = new ConcurrentHashMap<>();

    // ===== Ignored block filtering =====
    private static final Set<String> IGNORED_GROUPS = ConcurrentHashMap.newKeySet();
    private static final Set<String> IGNORED_BLOCK_IDS = ConcurrentHashMap.newKeySet();

    private final PlayerRef playerRef;
    private final World world;
    private final ShapeCompositor compositor;

    // Packed block positions currently made transparent
    private final Set<Long> currentPositions = new HashSet<>();
    // Position -> original block snapshot for restoration
    private final Map<Long, BlockSnapshot> activeBlocks = new HashMap<>();
    // Position -> computed block ID for this position
    private final Map<Long, Integer> activeBlockIds = new HashMap<>();
    // Last anchor used for diff check
    private Vector3i lastAnchor = null;
    // Scheduled update task
    private ScheduledFuture<?> updateTask = null;

    public CameraTransparencyVolume(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull ShapeCompositor compositor) {
        this.playerRef = playerRef;
        this.world = world;
        this.compositor = compositor;
    }
    
    // Legacy constructor for backward compatibility
    @Deprecated
    public CameraTransparencyVolume(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull Shape[] shapes) {
        this.playerRef = playerRef;
        this.world = world;
        // Create simple compositor with empty blocks for all shapes
        this.compositor = new ShapeCompositor(new Vector3i(0, 0, 0));
        for (int i = 0; i < shapes.length; i++) {
            compositor.addOperation("shape_" + i, shapes[i], OperationType.DEFINE, new EmptyBlockFill());
        }
    }

    // ===== Static instance management =====

    /**
     * Get or create a CameraTransparencyVolume using the parametric ShapeCompositor API.
     * This is the preferred method for creating new volumes.
     */
    @Nonnull
    public static CameraTransparencyVolume getOrCreate(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull ShapeCompositor compositor) {
        UUID playerId = playerRef.getUuid();
        CameraTransparencyVolume existing = INSTANCES.get(playerId);
        if (existing != null) {
            // Shut down stale instance (e.g. from a crash) and create fresh
            existing.shutdown();
            INSTANCES.remove(playerId);
            LOGGER.info("[CameraTransparency] Replaced stale volume for player: " + playerRef.getUsername());
        }
        CameraTransparencyVolume instance = new CameraTransparencyVolume(playerRef, world, compositor);
        instance.startUpdateLoop();
        INSTANCES.put(playerId, instance);
        LOGGER.info("[CameraTransparency] Created volume for player: " + playerRef.getUsername());
        return instance;
    }
    
    /**
     * Legacy method - creates a simple compositor with empty blocks for all shapes.
     * Use getOrCreate(PlayerRef, World, ShapeCompositor) for parametric control.
     */
    @Deprecated
    @Nonnull
    public static CameraTransparencyVolume getOrCreate(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull Shape[] shape) {
        UUID playerId = playerRef.getUuid();
        CameraTransparencyVolume existing = INSTANCES.get(playerId);
        if (existing != null) {
            // Shut down stale instance (e.g. from a crash) and create fresh
            existing.shutdown();
            INSTANCES.remove(playerId);
            LOGGER.info("[CameraTransparency] Replaced stale volume for player: " + playerRef.getUsername());
        }
        CameraTransparencyVolume instance = new CameraTransparencyVolume(playerRef, world, shape);
        instance.startUpdateLoop();
        INSTANCES.put(playerId, instance);
        LOGGER.info("[CameraTransparency] Created volume for player: " + playerRef.getUsername());
        return instance;
    }

    @Nullable
    public static CameraTransparencyVolume get(@Nonnull UUID playerId) {
        return INSTANCES.get(playerId);
    }

    /**
     * Returns a snapshot of the currently transparent block positions (packed longs)
     * for use by the server raycast to skip blocks the client can see through.
     */
    @Nonnull
    public it.unimi.dsi.fastutil.longs.LongOpenHashSet getTransparentPositions() {
        return new it.unimi.dsi.fastutil.longs.LongOpenHashSet(currentPositions);
    }

    public static void remove(@Nonnull UUID playerId) {
        CameraTransparencyVolume instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.shutdown();
            LOGGER.info("[CameraTransparency] Removed volume for player: " + playerId);
        }
    }

    // ===== Ignored block helpers =====

    private static boolean shouldIgnoreBlock(@Nonnull BlockType type) {
        // Climbable (ladders, vines, etc.)
        if (type.getMovementSettings().isClimbable()) return true;
        // Crafting stations / workbenches
        if (type.getBench() != null) return true;
        // General interactable blocks
        if (type.getFlags().isUsable) return true;
        // Seats
        if (type.getSeats() != null) return true;
        // Beds
        if (type.getBeds() != null) return true;
        // Group-based exclusion
        String group = type.getGroup();
        if (group != null && IGNORED_GROUPS.contains(group)) return true;
        // Specific block ID fallback
        if (IGNORED_BLOCK_IDS.contains(type.getId())) return true;
        return false;
    }

    public static void addIgnoredGroup(@Nonnull String group) {
        IGNORED_GROUPS.add(group);
    }

    public static void removeIgnoredGroup(@Nonnull String group) {
        IGNORED_GROUPS.remove(group);
    }

    @Nonnull
    public static Set<String> getIgnoredGroups() {
        return Collections.unmodifiableSet(IGNORED_GROUPS);
    }

    public static void addIgnoredBlockId(@Nonnull String blockTypeId) {
        IGNORED_BLOCK_IDS.add(blockTypeId);
    }

    public static void removeIgnoredBlockId(@Nonnull String blockTypeId) {
        IGNORED_BLOCK_IDS.remove(blockTypeId);
    }

    @Nonnull
    public static Set<String> getIgnoredBlockIds() {
        return Collections.unmodifiableSet(IGNORED_BLOCK_IDS);
    }

    // ===== Scheduled update loop =====

    private void startUpdateLoop() {
        updateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                world.execute(() -> {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        return;
                    }
                    var store = ref.getStore();

                    Vector3i origin = CameraPositionUtil.getCameraOriginBlock(ref, store);
                    if (origin != null) {
                        // Get player foot-level Y to filter out blocks below feet
                        var look = com.hypixel.hytale.server.core.util.TargetUtil.getLook(ref, store);
                        int minY = (int) Math.floor(look.getPosition().y) + FEET_Y_OFFSET;
                        update(origin, minY);
                    } else {
                        LOGGER.fine("[CameraTransparency] getCameraOriginBlock returned null");
                    }
                });
            } catch (Exception e) {
                LOGGER.warning("[CameraTransparency] Error in update loop: " + e.getMessage());
            }
        }, 200, UPDATE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void stopUpdateLoop() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
    }

    // ===== Core logic =====

    /**
     * Called when the camera position may have changed.
     * Only runs the diff if the integer block anchor has changed.
     * Must be called on the world thread.
     */
    public void update(@Nonnull Vector3i newAnchor, int minY) {
        // Early exit if anchor hasn't changed
        if (lastAnchor != null && lastAnchor.x == newAnchor.x && lastAnchor.y == newAnchor.y && lastAnchor.z == newAnchor.z) {
            return;
        }

//        LOGGER.info("[CameraTransparency] Anchor changed to: " + newAnchor.x + ", " + newAnchor.y + ", " + newAnchor.z);
        lastAnchor = newAnchor;

        ChunkStore chunkStore = world.getChunkStore();

        // Update compositor anchor to current camera position
        compositor.setAnchor(newAnchor);
        
        // Compose the region using the parametric compositor
        ComposedRegion region = compositor.compose(chunkStore, minY);
        
        // Filter out ignored blocks
        Map<Long, BlockSnapshot> newSnapshots = new HashMap<>();
        Set<Long> newPositions = new HashSet<>();
        Map<Long, Integer> newBlockIds = new HashMap<>();
        
        for (Map.Entry<Long, BlockSnapshot> entry : region.getOriginalBlocks().entrySet()) {
            Long pos = entry.getKey();
            BlockSnapshot snapshot = entry.getValue();
            
            // Skip excluded positions
            if (region.getExcludedPositions().contains(pos)) {
                continue;
            }
            
            // Check if block should be ignored
            BlockType baseType = BlockType.getAssetMap().getAsset(snapshot.blockId());
            if (baseType != null && shouldIgnoreBlock(baseType)) {
                continue;
            }
            
            newPositions.add(pos);
            newSnapshots.put(pos, snapshot);
            
            // Get computed block ID if available
            Integer blockId = region.getComputedBlockIds().get(pos);
            if (blockId != null) {
                newBlockIds.put(pos, blockId);
            }
        }

        // Compute diff: blocks to add (entered volume)
        Set<Long> toAdd = new HashSet<>(newPositions);
        toAdd.removeAll(currentPositions);

        // Compute diff: blocks to remove (left volume)
        Set<Long> toRemove = new HashSet<>(currentPositions);
        toRemove.removeAll(newPositions);

//        LOGGER.info("[CameraTransparency] Diff: " + newPositions.size() + " total, +" + toAdd.size() + " add, -" + toRemove.size() + " remove");

        // Apply changes immediately
        applyDiff(toAdd, toRemove, newSnapshots, newBlockIds);

        // Update current state
        currentPositions.clear();
        currentPositions.addAll(newPositions);

        // Update active blocks map: remove departed, add new
        for (Long pos : toRemove) {
            activeBlocks.remove(pos);
            activeBlockIds.remove(pos);
        }
        for (Long pos : toAdd) {
            BlockSnapshot snapshot = newSnapshots.get(pos);
            if (snapshot != null) {
                activeBlocks.put(pos, snapshot);
                Integer blockId = newBlockIds.get(pos);
                if (blockId != null) {
                    activeBlockIds.put(pos, blockId);
                }
            }
        }
    }

    private void applyDiff(@Nonnull Set<Long> toAdd, @Nonnull Set<Long> toRemove,
                           @Nonnull Map<Long, BlockSnapshot> newSnapshots,
                           @Nonnull Map<Long, Integer> newBlockIds) {
        // Restore blocks that left the volume
        for (Long pos : toRemove) {
            BlockSnapshot original = activeBlocks.get(pos);
            if (original != null) {
                playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                    original.x(), original.y(), original.z(),
                    original.blockId(), original.filler(), original.rotation()
                ));
            }
        }

        // Replace blocks in volume with computed block IDs from compositor
        for (Long pos : toAdd) {
            BlockSnapshot snapshot = newSnapshots.get(pos);
            if (snapshot != null) {
                // Use computed block ID if available, otherwise default to air (0)
                Integer blockId = newBlockIds.get(pos);
                int replacementId = blockId != null ? blockId : 0;
                
                playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                    snapshot.x(), snapshot.y(), snapshot.z(),
                    replacementId, (short) 0, (byte) 0
                ));
            }
        }
    }

    /**
     * Restores all transparent blocks to their originals and clears state.
     */
    public void shutdown() {
        stopUpdateLoop();

        // Restore all active blocks
        for (BlockSnapshot original : activeBlocks.values()) {
            playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                original.x(), original.y(), original.z(),
                original.blockId(), original.filler(), original.rotation()
            ));
        }

        int restored = activeBlocks.size();
        activeBlocks.clear();
        currentPositions.clear();
        lastAnchor = null;
        LOGGER.info("[CameraTransparency] Shutdown - restored " + restored + " blocks");
    }
}
