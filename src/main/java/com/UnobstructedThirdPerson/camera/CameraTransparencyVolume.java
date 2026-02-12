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
import java.util.concurrent.CompletableFuture;
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
    private static final int APPLY_DELAY_MILLIS = 500;
    private static final int FEET_Y_OFFSET = 1;
    private static final long UPDATE_INTERVAL_MILLIS = 100;

    private static final Map<UUID, CameraTransparencyVolume> INSTANCES = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final World world;
    private final Shape shape;

    // Packed block positions currently made transparent
    private final Set<Long> currentPositions = new HashSet<>();
    // Position -> original block snapshot for restoration
    private final Map<Long, BlockSnapshot> activeBlocks = new HashMap<>();
    // Last anchor used for diff check
    private Vector3i lastAnchor = null;
    // Scheduled update task
    private ScheduledFuture<?> updateTask = null;

    public CameraTransparencyVolume(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull Shape shape) {
        this.playerRef = playerRef;
        this.world = world;
        this.shape = shape;
    }

    // ===== Static instance management =====

    @Nonnull
    public static CameraTransparencyVolume getOrCreate(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull Shape shape) {
        UUID playerId = playerRef.getUuid();
        CameraTransparencyVolume existing = INSTANCES.get(playerId);
        if (existing != null) {
            // Shut down stale instance (e.g. from a crash) and create fresh
            existing.shutdown();
            INSTANCES.remove(playerId);
            LOGGER.info("[CameraTransparency] Replaced stale volume for player: " + playerRef.getUsername());
        }
        CameraTransparencyVolume instance = new CameraTransparencyVolume(playerRef, world, shape);
        TransparentBlockUtils.preloadAllTransparentTypes(playerRef);
        instance.startUpdateLoop();
        INSTANCES.put(playerId, instance);
        LOGGER.info("[CameraTransparency] Created volume for player: " + playerRef.getUsername());
        return instance;
    }

    @Nullable
    public static CameraTransparencyVolume get(@Nonnull UUID playerId) {
        return INSTANCES.get(playerId);
    }

    public static void remove(@Nonnull UUID playerId) {
        CameraTransparencyVolume instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.shutdown();
            LOGGER.info("[CameraTransparency] Removed volume for player: " + playerId);
        }
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
                        int minY = (int) Math.floor(look.getPosition().y) - FEET_Y_OFFSET;
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

        LOGGER.info("[CameraTransparency] Anchor changed to: " + newAnchor.x + ", " + newAnchor.y + ", " + newAnchor.z);
        lastAnchor = newAnchor;

        ChunkStore chunkStore = world.getChunkStore();

        // Build new set of positions inside the volume
        Set<Long> newPositions = new HashSet<>();
        Map<Long, BlockSnapshot> newSnapshots = new HashMap<>();

        shape.forEachBlock(newAnchor.x, newAnchor.y, newAnchor.z, (x, y, z) -> {
            // Skip blocks below the player's feet
            if (y < minY) {
                return true;
            }
            BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
            if (snapshot != null && snapshot.blockId() != 0) {
                BlockType baseType = BlockType.getAssetMap().getAsset(snapshot.blockId());
                if (baseType != null) {
                    long pos = BlockUtil.packUnchecked(x, y, z);
                    newPositions.add(pos);
                    newSnapshots.put(pos, snapshot);
                }
            }
            return true; // continue iteration
        });

        // Compute diff: blocks to add (entered volume)
        Set<Long> toAdd = new HashSet<>(newPositions);
        toAdd.removeAll(currentPositions);

        // Compute diff: blocks to remove (left volume)
        Set<Long> toRemove = new HashSet<>(currentPositions);
        toRemove.removeAll(newPositions);

        // Build fake ID map for new blocks
        Map<Integer, Integer> fakeIdByBaseId = new HashMap<>();
        for (Long pos : toAdd) {
            BlockSnapshot snapshot = newSnapshots.get(pos);
            if (snapshot != null) {
                fakeIdByBaseId.computeIfAbsent(snapshot.blockId(), TransparentBlockUtils::getTransparentVariantId);
            }
        }

        // Ensure transparent types are sent to client
        boolean sentNewTypes = false;
        if (!fakeIdByBaseId.isEmpty()) {
            sentNewTypes = TransparentBlockUtils.ensureTransparentTypesSent(playerRef, fakeIdByBaseId);
        }

        LOGGER.info("[CameraTransparency] Diff: " + newPositions.size() + " total, +" + toAdd.size() + " add, -" + toRemove.size() + " remove, sentNewTypes=" + sentNewTypes);

        // Apply changes — delay if new types were sent
        if (sentNewTypes) {
            // Capture for lambda
            final Set<Long> capturedToAdd = toAdd;
            final Set<Long> capturedToRemove = toRemove;
            final Map<Long, BlockSnapshot> capturedNewSnapshots = newSnapshots;
            CompletableFuture.delayedExecutor(APPLY_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
                world.execute(() -> applyDiff(capturedToAdd, capturedToRemove, capturedNewSnapshots, fakeIdByBaseId));
            });
        } else {
            applyDiff(toAdd, toRemove, newSnapshots, fakeIdByBaseId);
        }

        // Update current state
        currentPositions.clear();
        currentPositions.addAll(newPositions);

        // Update active blocks map: remove departed, add new
        for (Long pos : toRemove) {
            activeBlocks.remove(pos);
        }
        for (Long pos : toAdd) {
            BlockSnapshot snapshot = newSnapshots.get(pos);
            if (snapshot != null) {
                activeBlocks.put(pos, snapshot);
            }
        }
    }

    private void applyDiff(@Nonnull Set<Long> toAdd, @Nonnull Set<Long> toRemove,
                           @Nonnull Map<Long, BlockSnapshot> newSnapshots,
                           @Nonnull Map<Integer, Integer> fakeIdByBaseId) {
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

        // Make new blocks transparent
        for (Long pos : toAdd) {
            BlockSnapshot snapshot = newSnapshots.get(pos);
            if (snapshot != null) {
                Integer fakeId = fakeIdByBaseId.get(snapshot.blockId());
                if (fakeId != null) {
                    playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                        snapshot.x(), snapshot.y(), snapshot.z(),
                        fakeId, snapshot.filler(), snapshot.rotation()
                    ));
                }
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
