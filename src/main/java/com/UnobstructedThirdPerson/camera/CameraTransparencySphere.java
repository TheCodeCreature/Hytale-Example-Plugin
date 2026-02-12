package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Makes blocks transparent in a sphere around the camera origin.
 * Uses a diff-based cache: only sends packets for blocks entering/leaving the sphere
 * when the camera crosses a whole-number coordinate boundary.
 */
public class CameraTransparencySphere {

    private static final Logger LOGGER = Logger.getLogger("CameraTransparencySphere");
    private static final double DEFAULT_RADIUS = 10.0;
    private static final int APPLY_DELAY_MILLIS = 50;

    private static final Map<UUID, CameraTransparencySphere> INSTANCES = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final World world;
    private final Ellipsoid sphere;

    // Packed block positions currently made transparent
    private final Set<Long> currentPositions = new HashSet<>();
    // Position -> original block snapshot for restoration
    private final Map<Long, BlockSnapshot> activeBlocks = new HashMap<>();
    // Last anchor used for diff check
    private Vector3i lastAnchor = null;

    public CameraTransparencySphere(@Nonnull PlayerRef playerRef, @Nonnull World world, double radius) {
        this.playerRef = playerRef;
        this.world = world;
        this.sphere = new Ellipsoid(radius);
    }

    // ===== Static instance management =====

    @Nonnull
    public static CameraTransparencySphere getOrCreate(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerId = playerRef.getUuid();
        CameraTransparencySphere existing = INSTANCES.get(playerId);
        if (existing != null) {
            return existing;
        }
        CameraTransparencySphere instance = new CameraTransparencySphere(playerRef, world, DEFAULT_RADIUS);
        INSTANCES.put(playerId, instance);
        LOGGER.info("[CameraTransparency] Created sphere for player: " + playerRef.getUsername());
        return instance;
    }

    @Nullable
    public static CameraTransparencySphere get(@Nonnull UUID playerId) {
        return INSTANCES.get(playerId);
    }

    public static void remove(@Nonnull UUID playerId) {
        CameraTransparencySphere instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.shutdown();
            LOGGER.info("[CameraTransparency] Removed sphere for player: " + playerId);
        }
    }

    // ===== External trigger =====

    /**
     * Called from external packages (e.g. InteractionPositionFixer) to trigger a sphere update.
     * Computes the camera origin via CameraPositionUtil and calls update() if a sphere exists.
     * Must be called on the world thread.
     */
    public static void updateForPlayer(@Nonnull UUID playerId, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        CameraTransparencySphere sphere = INSTANCES.get(playerId);
        if (sphere == null) {
            return;
        }
        Vector3i origin = CameraPositionUtil.getCameraOriginBlock(ref, store);
        if (origin != null) {
            sphere.update(origin);
        }
    }

    // ===== Core logic =====

    /**
     * Called when the camera position may have changed.
     * Only runs the diff if the integer block anchor has changed.
     * Must be called on the world thread.
     */
    public void update(@Nonnull Vector3i newAnchor) {
        // Early exit if anchor hasn't changed
        if (lastAnchor != null && lastAnchor.x == newAnchor.x && lastAnchor.y == newAnchor.y && lastAnchor.z == newAnchor.z) {
            return;
        }

        lastAnchor = newAnchor;

        ChunkStore chunkStore = world.getChunkStore();

        // Build new set of positions inside the sphere
        Set<Long> newPositions = new HashSet<>();
        Map<Long, BlockSnapshot> newSnapshots = new HashMap<>();

        sphere.forEachBlock(newAnchor.x, newAnchor.y, newAnchor.z, (x, y, z) -> {
            BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
            if (snapshot != null && snapshot.blockId() != 0) {
                BlockType baseType = BlockType.getAssetMap().getAsset(snapshot.blockId());
                if (baseType != null && TransparentBlockUtils.isEligibleBlockType(baseType)) {
                    long pos = BlockUtil.packUnchecked(x, y, z);
                    newPositions.add(pos);
                    newSnapshots.put(pos, snapshot);
                }
            }
            return true; // continue iteration
        });

        // Compute diff: blocks to add (entered sphere)
        Set<Long> toAdd = new HashSet<>(newPositions);
        toAdd.removeAll(currentPositions);

        // Compute diff: blocks to remove (left sphere)
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
        // Restore blocks that left the sphere
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
        // Restore all active blocks
        for (BlockSnapshot original : activeBlocks.values()) {
            playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                original.x(), original.y(), original.z(),
                original.blockId(), original.filler(), original.rotation()
            ));
        }

        activeBlocks.clear();
        currentPositions.clear();
        lastAnchor = null;
        LOGGER.info("[CameraTransparency] Shutdown - restored " + activeBlocks.size() + " blocks");
    }
}
