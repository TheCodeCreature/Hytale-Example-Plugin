package com.UnobtrusiveThirdPerson.camera;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

public class TransparentAreaCommand extends CommandBase {
    private static final int RAYCAST_DISTANCE = 30;
    private static final int DEPTH_BLOCKS = 5;
    private static final int RADIUS = 5;
    private static final int PEEK_INTERVAL_MILLIS = 100;
    private static final int APPLY_DELAY_MILLIS = 50;
    private static final String TRANSPARENT_TEXTURE = "BlockTextures/Transparent.png";
    private static final Object ID_LOCK = new Object();
    private static final Map<Integer, Integer> TRANSPARENT_VARIANT_IDS = new ConcurrentHashMap<>();
    private static final Map<UUID, PeekState> ACTIVE_PEEKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Integer>> SENT_FAKE_IDS = new ConcurrentHashMap<>();
    private static volatile AtomicInteger nextFakeId;
    private static volatile int preloadBlockId = Integer.MIN_VALUE;
    private static final float CAMERA_DISTANCE = 6.0F;
    
    // Transparent block manager infrastructure
    private static final Map<UUID, TransparentBlockManager> MANAGERS = new ConcurrentHashMap<>();
    private static volatile ScheduledFuture<?> globalCleanupTask = null;
    private static final Object CLEANUP_LOCK = new Object();
    private static final long DECAY_THRESHOLD_MILLIS = 500;
    private static final int CLEANUP_INTERVAL_MILLIS = 100;

    public TransparentAreaCommand() {
        super("peek", "Toggles a transparent peek area on your client.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store.isInThread()) {
            apply(store, ref, ctx);
        } else {
            store.getExternalData().getWorld().execute(() -> apply(store, ref, ctx));
        }
    }

    private static void apply(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull CommandContext ctx) {
        if (!ref.isValid()) {
            ctx.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            ctx.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
            return;
        }

        UUID playerId = playerRef.getUuid();
        PeekState existing = ACTIVE_PEEKS.remove(playerId);
        if (existing != null) {
            // Disable peek: stop update task and restore all blocks immediately
            existing.stop();
            removeManager(playerId);
            applyPeekCamera(playerRef, false);
            ctx.sendMessage(Message.raw("Peek disabled."));
            return;
        }

        // Enable peek: create manager and start update task
        applyPeekCamera(playerRef, true);
        World world = store.getExternalData().getWorld();
        getOrCreateManager(playerRef, world);
        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            world.execute(() -> updatePeek(playerRef));
        }, 0L, PEEK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        ACTIVE_PEEKS.put(playerId, new PeekState(task));
        ctx.sendMessage(Message.raw("Peek enabled."));
    }

    private static void updatePeek(@Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            stopPeek(playerRef.getUuid());
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            stopPeek(playerRef.getUuid());
            return;
        }

        Vector3i cameraOrigin = getCameraOriginBlock(ref, store);
        if (cameraOrigin == null) {
            return;
        }

        Transform look = TargetUtil.getLook(ref, store);
        Vector3i axisDir = look.getAxisDirection();
        if (axisDir.x == 0 && axisDir.y == 0 && axisDir.z == 0) {
            return;
        }

        World world = store.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        List<BlockSnapshot> blocks = collectBlocks(chunkStore, cameraOrigin, axisDir);
        
        if (blocks.isEmpty()) {
            return;
        }

        // Send-and-forget: submit blocks to manager, it handles everything else
        TransparentBlockManager manager = MANAGERS.get(playerRef.getUuid());
        if (manager != null) {
            manager.submitBlocks(
                blocks, 
                TransparentAreaCommand::getTransparentVariantId,
                TransparentAreaCommand::ensureTransparentTypesSent
            );
        }
    }

    private static void stopPeek(@Nonnull UUID playerId) {
        PeekState state = ACTIVE_PEEKS.remove(playerId);
        if (state != null) {
            state.stop();
        }
    }

    private static Vector3i getCameraTarget(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        CameraManager cameraManager = store.getComponent(ref, CameraManager.getComponentType());
        if (cameraManager != null) {
            Vector3i target = cameraManager.getLastTargetBlock();
            if (target != null) {
                return target;
            }
        }

        return TargetUtil.getTargetBlock(ref, RAYCAST_DISTANCE, store);
    }

    private static Vector3i getCameraOriginBlock(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Transform look = TargetUtil.getLook(ref, store);
        if (look == null) {
            return null;
        }

        // Get player position (eye level)
        com.hypixel.hytale.math.vector.Vector3d playerPos = look.getPosition();
        if (playerPos == null) {
            return null;
        }

        // Get look direction normalized
        com.hypixel.hytale.math.vector.Vector3d lookDir = look.getDirection();
        if (lookDir == null) {
            return null;
        }

        // Calculate camera position: player position - (look direction * camera distance)
        // Camera distance matches the peek camera settings (10f)
        double cameraX = playerPos.x - (lookDir.x * CAMERA_DISTANCE);
        double cameraY = playerPos.y - (lookDir.y * CAMERA_DISTANCE);
        double cameraZ = playerPos.z - (lookDir.z * CAMERA_DISTANCE);

        // Convert to block coordinates (floor to int)
        return new Vector3i((int) Math.floor(cameraX), (int) Math.floor(cameraY), (int) Math.floor(cameraZ));
    }

    private static List<BlockSnapshot> collectBlocks(@Nonnull ChunkStore chunkStore, @Nonnull Vector3i target, @Nonnull Vector3i axisDir) {
        Set<Long> seen = new HashSet<>();
        List<BlockSnapshot> out = new ArrayList<>();

        for (int depth = 0; depth < DEPTH_BLOCKS; depth++) {
            int centerX = target.x + axisDir.x * depth;
            int centerY = target.y + axisDir.y * depth;
            int centerZ = target.z + axisDir.z * depth;

            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    int x = centerX + dx;
                    int y = centerY;
                    int z = centerZ + dz;
                    long key = BlockUtil.packUnchecked(x, y, z);
                    if (!seen.add(key)) {
                        continue;
                    }

                    BlockSnapshot snapshot = readBlock(chunkStore, x, y, z);
                    if (snapshot != null && snapshot.blockId() != 0) {
                        BlockType baseType = BlockType.getAssetMap().getAsset(snapshot.blockId());
                        if (baseType != null && isEligibleBlockType(baseType)) {
                            out.add(snapshot);
                        }
                    }
                }
            }
        }

        return out;
    }

    private static BlockSnapshot readBlock(@Nonnull ChunkStore chunkStore, int x, int y, int z) {
        if (y < 0 || y >= ChunkUtil.HEIGHT) {
            return null;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }

        BlockChunk blockChunk = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return null;
        }

        BlockSection section = blockChunk.getSectionAtBlockY(y);
        int localX = x & 31;
        int localY = y & 31;
        int localZ = z & 31;
        int blockId = section.get(localX, localY, localZ);
        int filler = section.getFiller(localX, localY, localZ);
        int rotation = section.getRotationIndex(localX, localY, localZ);
        return new BlockSnapshot(x, y, z, blockId, (short) filler, (byte) rotation);
    }

//    private static void sendTransparentBlockType(@Nonnull PlayerRef playerRef, int fakeId, @Nonnull BlockType baseType) {
//        sendTransparentBlockType(playerRef, fakeId, baseType, false);
//    }

    public static void preloadTransparentType(@Nonnull PlayerRef playerRef) {
        int fakeId = getPreloadBlockId();
        BlockType baseType = findAnyBlockType();
        if (baseType == null) {
            return;
        }

        sendTransparentBlockType(playerRef, fakeId, baseType, true);
    }

    private static void sendTransparentBlockType(
        @Nonnull PlayerRef playerRef,
        int fakeId,
        @Nonnull BlockType baseType,
        boolean rebuildTextures
    ) {
        com.hypixel.hytale.protocol.BlockType packetBlock = new com.hypixel.hytale.protocol.BlockType(baseType.toPacket());
        if (packetBlock.drawType == DrawType.Model || packetBlock.drawType == DrawType.CubeWithModel) {
            packetBlock.drawType = DrawType.Cube;
            packetBlock.model = null;
            packetBlock.modelTexture = null;
        }
        packetBlock.name = null;
        packetBlock.item = null;
        packetBlock.modelAnimation = null;
        packetBlock.cubeSideMaskTexture = null;
        packetBlock.blockParticleSetId = null;
        packetBlock.blockBreakingDecalId = null;
        packetBlock.transitionTexture = null;
        packetBlock.interactionHint = null;
        packetBlock.states = null;
        packetBlock.tagIndexes = null;
        packetBlock.opacity = Opacity.Transparent;
        packetBlock.requiresAlphaBlending = true;

        BlockTextures transparent = new BlockTextures();
        transparent.top = TRANSPARENT_TEXTURE;
        transparent.bottom = TRANSPARENT_TEXTURE;
        transparent.front = TRANSPARENT_TEXTURE;
        transparent.back = TRANSPARENT_TEXTURE;
        transparent.left = TRANSPARENT_TEXTURE;
        transparent.right = TRANSPARENT_TEXTURE;
        transparent.weight = 1.0F;
        packetBlock.cubeTextures = new BlockTextures[] { transparent };

        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = Math.max(BlockType.getAssetMap().getNextIndex(), fakeId + 1);
        Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
        blockTypes.put(fakeId, packetBlock);
        update.blockTypes = blockTypes;
        update.updateBlockTextures = rebuildTextures;
        update.updateModelTextures = false;
        update.updateModels = false;
        update.updateMapGeometry = false;

        playerRef.getPacketHandler().writeNoCache(update);
    }

    private static int getTransparentVariantId(int baseId) {
        return TRANSPARENT_VARIANT_IDS.computeIfAbsent(baseId, _id -> allocateFakeId());
    }

    public static void resetPlayer(@Nonnull UUID playerId) {
        stopPeek(playerId);
        removeManager(playerId);
        SENT_FAKE_IDS.remove(playerId);
    }

    public static void resetPlayer(@Nonnull PlayerRef playerRef) {
        resetPlayer(playerRef.getUuid());
        applyPeekCamera(playerRef, false);
    }

    private static void applyPeekCamera(@Nonnull PlayerRef playerRef, boolean enabled) {
        if (!enabled) {
            playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
            return;
        }

//        ServerCameraSettings cameraSettings = new ServerCameraSettings();
//        cameraSettings.isFirstPerson = false;
//        cameraSettings.distance = CAMERA_DISTANCE;
//        cameraSettings.allowPitchControls = true;
//        cameraSettings.displayReticle = true;
////        cameraSettings.mouseInputTargetType = MouseInputTargetType.Any;
////        cameraSettings.mouseInputType = MouseInputType.LookAtTarget;
//        cameraSettings.sendMouseMotion = true;
//        cameraSettings.skipCharacterPhysics = false;
//        cameraSettings.eyeOffset = true;
//        cameraSettings.attachedToType = AttachedToType.LocalPlayer;
//        cameraSettings.positionType = PositionType.AttachedToPlusOffset;
////        cameraSettings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
//        cameraSettings.rotationType = RotationType.AttachedToPlusOffset;
//        cameraSettings.applyLookType = ApplyLookType.LocalPlayerLookOrientation;
//        cameraSettings.movementForceRotationType = MovementForceRotationType.AttachedToHead;
//        cameraSettings.canMoveType = CanMoveType.AttachedToLocalPlayer;
//        cameraSettings.applyMovementType = ApplyMovementType.CharacterController;
////
////        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, cameraSettings));
//
////        ServerCameraSettings cameraSettings = new ServerCameraSettings();
////        cameraSettings.positionLerpSpeed = 0.2F;
////        cameraSettings.rotationLerpSpeed = 0.2F;
////        cameraSettings.distance = 20.0F;
//        cameraSettings.displayCursor = false;
////        cameraSettings.isFirstPerson = false;
////        cameraSettings.movementForceRotationType = MovementForceRotationType.Custom;
////        cameraSettings.eyeOffset = true;
//        cameraSettings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
////        cameraSettings.rotationType = RotationType.Custom;
////        cameraSettings.rotation = new Direction(0.0F, (float) (-Math.PI / 2), 0.0F);
////        cameraSettings.mouseInputType = MouseInputType.LookAtPlane;
//        cameraSettings.mouseInputType = MouseInputType.LookAtTargetBlock;
////        cameraSettings.planeNormal = new Vector3f(0.0F, 1.0F, 0.0F);
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.positionLerpSpeed = 0.1f;
        settings.rotationLerpSpeed = 0.1f;
        settings.isFirstPerson = false;
        settings.distance = 10f;
        settings.eyeOffset = true;
//        settings.allowPitchControls = false;
//        settings.displayCursor = false;
        settings.displayReticle = true;
        settings.sendMouseMotion = true;
        settings.mouseInputTargetType = MouseInputTargetType.Any;
        settings.mouseInputType = MouseInputType.LookAtTarget;
// Force the camera's rotation to be set by the server.
//        settings.applyLookType = ApplyLookType.Rotation;
// Notify that we provide a custom rotation in "settings.rotation"
        settings.rotationType = RotationType.AttachedToPlusOffset;
// Set the typical isometric rotation to the camera
        Direction direction = new Direction(
                (float) Math.toRadians(45f),  // yaw
                (float) Math.toRadians(-35f), // pitch
                0f                            // roll
        );



        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));
    }

    private static int getPreloadBlockId() {
        int current = preloadBlockId;
        if (current != Integer.MIN_VALUE) {
            return current;
        }

        int next = allocateFakeId();
        preloadBlockId = next;
        return next;
    }

    private static int allocateFakeId() {
        AtomicInteger allocator = nextFakeId;
        if (allocator == null) {
            synchronized (ID_LOCK) {
                if (nextFakeId == null) {
                    nextFakeId = new AtomicInteger(BlockType.getAssetMap().getNextIndex());
                }
                allocator = nextFakeId;
            }
        }

        return allocator.getAndIncrement();
    }

    private static BlockType findAnyBlockType() {
        int max = BlockType.getAssetMap().getNextIndex();
        for (int i = 0; i < max; i++) {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            if (type != null && !type.isUnknown() && isEligibleBlockType(type)) {
                return type;
            }
        }

        return null;
    }

    private static boolean isEligibleBlockType(@Nonnull BlockType type) {
        DrawType drawType = type.getDrawType();
        if (drawType != DrawType.Cube && drawType != DrawType.GizmoCube) {
            return false;
        }

        if (type.getState() != null) {
            return false;
        }

        return type.getBlockEntity() == null;
    }

    private static boolean ensureTransparentTypesSent(@Nonnull PlayerRef playerRef, @Nonnull Map<Integer, Integer> fakeIdByBaseId) {
        boolean sentAny = false;
        Set<Integer> sent = SENT_FAKE_IDS.computeIfAbsent(playerRef.getUuid(), _id -> ConcurrentHashMap.newKeySet());
        for (Map.Entry<Integer, Integer> entry : fakeIdByBaseId.entrySet()) {
            int fakeId = entry.getValue();
            if (sent.contains(fakeId)) {
                continue;
            }

            BlockType baseType = BlockType.getAssetMap().getAsset(entry.getKey());
            if (baseType == null) {
                continue;
            }

            sendTransparentBlockType(playerRef, fakeId, baseType, false);
            sent.add(fakeId);
            sentAny = true;
        }
        return sentAny;
    }


    // Global cleanup task management
    private static void ensureCleanupTaskRunning() {
        if (globalCleanupTask == null) {
            synchronized (CLEANUP_LOCK) {
                if (globalCleanupTask == null) {
                    globalCleanupTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                        TransparentAreaCommand::runGlobalCleanup,
                        CLEANUP_INTERVAL_MILLIS,
                        CLEANUP_INTERVAL_MILLIS,
                        TimeUnit.MILLISECONDS
                    );
                }
            }
        }
    }

    private static void stopCleanupTaskIfEmpty() {
        if (MANAGERS.isEmpty() && globalCleanupTask != null) {
            synchronized (CLEANUP_LOCK) {
                if (MANAGERS.isEmpty() && globalCleanupTask != null) {
                    globalCleanupTask.cancel(false);
                    globalCleanupTask = null;
                }
            }
        }
    }

    private static void runGlobalCleanup() {
        for (TransparentBlockManager manager : MANAGERS.values()) {
            manager.cleanup();
        }
    }

    private static TransparentBlockManager getOrCreateManager(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerId = playerRef.getUuid();
        TransparentBlockManager manager = MANAGERS.get(playerId);
        if (manager == null) {
            manager = new TransparentBlockManager(
                playerRef, 
                world, 
                DECAY_THRESHOLD_MILLIS, 
                APPLY_DELAY_MILLIS
            );
            MANAGERS.put(playerId, manager);
            ensureCleanupTaskRunning();
        }
        return manager;
    }

    private static void removeManager(@Nonnull UUID playerId) {
        TransparentBlockManager manager = MANAGERS.remove(playerId);
        if (manager != null) {
            manager.shutdown();
            stopCleanupTaskIfEmpty();
        }
    }
}
