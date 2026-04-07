package com.UnobstructedThirdPerson.camera.v2;

import com.UnobstructedThirdPerson.camera.DebugCube;
import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v1.placeholder.TransparentBlockUtils;
import com.UnobstructedThirdPerson.shape.v2.ComposedRegionV2;
import com.UnobstructedThirdPerson.shape.v2.ShapeCompositorV2;
import com.UnobstructedThirdPerson.shape.v2.VoxelEntry;
import com.UnobstructedThirdPerson.shape.v2.fill.PlaceholderFillV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.UnobstructedThirdPerson.shape.v1.placeholder.PlaceholderTransparencyUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CameraTransparencyVolumeV2 {

    private static final Logger LOGGER = Logger.getLogger("CameraTransparencyVolumeV2");
    private static final long UPDATE_INTERVAL_MILLIS = 100;

    private static final Map<UUID, CameraTransparencyVolumeV2> INSTANCES = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final World world;
    private final ShapeCompositorV2 compositor;
    private final Vector3d compositorOffset;

    private final Set<Long> currentPositions = new HashSet<>();
    private final Map<Long, BlockSnapshot> activeBlocks = new HashMap<>();
    private final Map<Long, Integer> activeBlockIds = new HashMap<>();
    private Map<Long, DebugStyle> lastRenderedDebugStyles = new HashMap<>();
    private Vector3d lastAnchor = null;
    private double lastYaw = Double.NaN;
    private double lastPitch = Double.NaN;
    private ScheduledFuture<?> updateTask = null;

    public CameraTransparencyVolumeV2(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull ShapeCompositorV2 compositor) {
        this.playerRef = playerRef;
        this.world = world;
        this.compositor = compositor;
        this.compositorOffset = compositor.getOffset();
    }

    public static void StartTransparencyVolumeLoop(@Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull ShapeCompositorV2 compositor) {
        UUID playerId = playerRef.getUuid();
        CameraTransparencyVolumeV2 existing = INSTANCES.get(playerId);
        if (existing != null) {
            existing.shutdown();
            INSTANCES.remove(playerId);
            LOGGER.info("[CameraTransparencyV2] Replaced stale volume for player: " + playerRef.getUsername());
        }
        CameraTransparencyVolumeV2 instance = new CameraTransparencyVolumeV2(playerRef, world, compositor);
        instance.startUpdateLoop();
        INSTANCES.put(playerId, instance);
        LOGGER.info("[CameraTransparencyV2] Created volume for player: " + playerRef.getUsername());
    }

    @Nonnull
    public Map<Long, BlockSnapshot> getActiveBlocks() {
        return Collections.unmodifiableMap(activeBlocks);
    }

    public static void remove(@Nonnull UUID playerId) {
        CameraTransparencyVolumeV2 instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.shutdown();
            LOGGER.info("[CameraTransparencyV2] Removed volume for player: " + playerId);
        }
    }

    private void startUpdateLoop() {
        updateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                world.execute(() -> {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        return;
                    }

                    Vector3d origin = playerRef.getTransform().getPosition();

                    Store<EntityStore> store = ref.getStore();
                    Transform look = TargetUtil.getLook(ref, store);
                    if (look != null) {
                        Vector3d lookDir = look.getDirection();
                        double cameraYaw = Math.atan2(-lookDir.x, lookDir.z);
                        double cameraPitch = Math.asin(lookDir.y);
                        compositor.setRotation(cameraYaw, cameraPitch);
                    }
                    
                    update(origin);
                });
            } catch (Exception e) {
                LOGGER.warning("[CameraTransparencyV2] Error in update loop: " + e.getMessage());
            }
        }, UPDATE_INTERVAL_MILLIS, UPDATE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void stopUpdateLoop() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
    }

    public void update(@Nonnull Vector3d newAnchor) {
        double currentYaw = compositor.getYawRotation();
        double currentPitch = compositor.getPitchRotation();
        
        boolean anchorChanged = lastAnchor == null || 
                lastAnchor.x != newAnchor.x || 
                lastAnchor.y != newAnchor.y || 
                lastAnchor.z != newAnchor.z;
        boolean yawChanged = Double.isNaN(lastYaw) || 
                Math.abs(currentYaw - lastYaw) > 0.01;
        boolean pitchChanged = Double.isNaN(lastPitch) || 
                Math.abs(currentPitch - lastPitch) > 0.01;
        
        if (!anchorChanged && !yawChanged && !pitchChanged) {
            renderCachedDebugCubes();
            return;
        }

        lastAnchor = new Vector3d(newAnchor.x, newAnchor.y, newAnchor.z);
        lastYaw = currentYaw;
        lastPitch = currentPitch;

        ChunkStore chunkStore = world.getChunkStore();
        compositor.setAnchor(newAnchor);
        
        ComposedRegionV2 region = compositor.compose(chunkStore);
        
        Map<Long, BlockSnapshot> newSnapshots = new HashMap<>();
        Set<Long> newPositions = new HashSet<>();
        Map<Long, Integer> newBlockIds = new HashMap<>();
        
        for (Map.Entry<Long, BlockSnapshot> entry : region.getOriginalBlocks().entrySet()) {
            Long pos = entry.getKey();
            BlockSnapshot snapshot = entry.getValue();
            
            if (region.getExcludedPositions().contains(pos)) {
                continue;
            }
            
            BlockType baseType = BlockType.getAssetMap().getAsset(snapshot.blockId());
            
            newPositions.add(pos);
            newSnapshots.put(pos, snapshot);
            
            Integer blockId = region.getComputedBlockIds().get(pos);
            if (blockId != null) {
                BlockType replacementType = BlockType.getAssetMap().getAsset(blockId);
                if (PlaceholderFillV2.isPlaceholderBlockId(blockId) && baseType != null) {
                    String hitboxType = baseType.getHitboxType();
                    if (hitboxType != null) {
                        Integer transparentPlaceholderId = PlaceholderTransparencyUtil.prepareTransparentPlaceholder(
                                playerRef, snapshot.blockId(), hitboxType);
                        
                        if (transparentPlaceholderId != null) {
                            newBlockIds.put(pos, transparentPlaceholderId);
                        } else {
                            newBlockIds.put(pos, blockId);
                        }
                    } else {
                        newBlockIds.put(pos, blockId);
                    }
                } else {
                    newBlockIds.put(pos, blockId);
                }
            }
        }

        Set<Long> toAdd = new HashSet<>(newPositions);
        toAdd.removeAll(currentPositions);

        Set<Long> toRemove = new HashSet<>(currentPositions);
        toRemove.removeAll(newPositions);
        
        Set<Long> toUpdate = new HashSet<>();
        for (Long pos : newPositions) {
            if (currentPositions.contains(pos)) {
                Integer oldBlockId = activeBlockIds.get(pos);
                Integer newBlockId = newBlockIds.get(pos);
                if (!Objects.equals(oldBlockId, newBlockId)) {
                    toUpdate.add(pos);
                }
            }
        }

        applyDiff(toAdd, toRemove, toUpdate, newSnapshots, newBlockIds);

        currentPositions.clear();
        currentPositions.addAll(newPositions);

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
        for (Long pos : toUpdate) {
            Integer blockId = newBlockIds.get(pos);
            if (blockId != null) {
                activeBlockIds.put(pos, blockId);
            }
        }

        // Debug styles are rebuilt fresh from the region each frame
        renderDebugCubesFromRegion(region);
    }

    public void cleanupPositions(@Nonnull Collection<Long> positionsToCleanup) {
        if (positionsToCleanup.isEmpty()) {
            return;
        }

        restorePositions(positionsToCleanup);
        for (Long pos : positionsToCleanup) {
            activeBlocks.remove(pos);
            activeBlockIds.remove(pos);
            currentPositions.remove(pos);
        }
    }

    private void renderDebugCubesFromRegion(@Nonnull ComposedRegionV2 region) {
        Map<Long, DebugStyle> newDebugStyles = new HashMap<>();
        
        // Collect debug styles from ALL voxels in the region (not just those with fills)
        for (Map.Entry<Long, VoxelEntry> entry : region.getVoxelMap().entrySet()) {
            VoxelEntry voxel = entry.getValue();
            if (voxel.isExcluded()) {
                continue;
            }
            DebugStyle style = voxel.getDebugStyle();
            if (style.isEnabled() && style.getColor() != null) {
                newDebugStyles.put(entry.getKey(), style);
            }
        }
        
        // Differentially update: stale cubes expire naturally, active cubes get refreshed
        DebugCube.updateDebugCubes(world, newDebugStyles);
        
        lastRenderedDebugStyles = newDebugStyles;
    }

    private void renderCachedDebugCubes() {
        DebugCube.renderCachedDebugCubes(world);
    }

    private int restorePositions(@Nonnull Collection<Long> positionsToRestore) {
        int restored = 0;

        ChunkStore chunkStore = world.getChunkStore();
        for (Long pos : positionsToRestore) {
            BlockSnapshot original = activeBlocks.get(pos);
            if (original == null) {
                continue;
            }

            BlockSnapshot currentBlock = TransparentBlockUtils.readBlock(
                    chunkStore,
                    original.x(),
                    original.y(),
                    original.z());

            BlockSnapshot restoreSnapshot = currentBlock != null ? currentBlock : original;
            playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                    restoreSnapshot.x(),
                    restoreSnapshot.y(),
                    restoreSnapshot.z(),
                    restoreSnapshot.blockId(),
                    restoreSnapshot.filler(),
                    restoreSnapshot.rotation()
            ));
            restored++;
        }

        return restored;
    }

    private void applyDiff(@Nonnull Set<Long> toAdd, @Nonnull Set<Long> toRemove, @Nonnull Set<Long> toUpdate,
                           @Nonnull Map<Long, BlockSnapshot> newSnapshots,
                           @Nonnull Map<Long, Integer> newBlockIds) {
        restorePositions(toRemove);

        for (Long pos : toAdd) {
            BlockSnapshot snapshot = newSnapshots.get(pos);
            if (snapshot != null) {
                Integer blockId = newBlockIds.get(pos);
                int replacementId = blockId != null ? blockId : 0;
                
                playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                    snapshot.x(), snapshot.y(), snapshot.z(),
                    replacementId, (short) 0, (byte) 0
                ));
            }
        }
        
        for (Long pos : toUpdate) {
            BlockSnapshot snapshot = activeBlocks.get(pos);
            if (snapshot != null) {
                Integer blockId = newBlockIds.get(pos);
                int replacementId = blockId != null ? blockId : 0;
                
                playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                    snapshot.x(), snapshot.y(), snapshot.z(),
                    replacementId, (short) 0, (byte) 0
                ));
            }
        }
    }

    public void shutdown() {
        stopUpdateLoop();

        Set<Long> positionsToCleanup = new HashSet<>(activeBlocks.keySet());
        cleanupPositions(positionsToCleanup);
        
        PlaceholderTransparencyUtil.restoreAllPlaceholders(playerRef);

        int restored = positionsToCleanup.size();
        activeBlocks.clear();
        activeBlockIds.clear();
        lastRenderedDebugStyles.clear();
        currentPositions.clear();
        lastAnchor = null;
        LOGGER.info("[CameraTransparencyV2] Shutdown - restored " + restored + " blocks");
    }
}
