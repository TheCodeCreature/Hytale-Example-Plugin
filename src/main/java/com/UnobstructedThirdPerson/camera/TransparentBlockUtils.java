package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Ref;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared utilities for creating and managing fake transparent block types on the client.
 * Extracted from TransparentAreaCommand so both it and CameraTransparencyVolume can share.
 */
public final class TransparentBlockUtils {

    private static final String TRANSPARENT_TEXTURE = "hytale:block/debug/alpha_test";
    private static final Map<Integer, Integer> TRANSPARENT_VARIANT_IDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Integer>> SENT_FAKE_IDS = new ConcurrentHashMap<>();
    private static volatile AtomicInteger nextFakeId;
    private static final Object ID_LOCK = new Object();

    private TransparentBlockUtils() {
        // Utility class
    }

    public static int getTransparentVariantId(int baseId) {
        return TRANSPARENT_VARIANT_IDS.computeIfAbsent(baseId, _id -> allocateFakeId());
    }

    public static int allocateFakeId() {
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

    public static boolean ensureTransparentTypesSent(@Nonnull PlayerRef playerRef, @Nonnull Map<Integer, Integer> fakeIdByBaseId) {
        return ensureTransparentTypesSent(playerRef, fakeIdByBaseId, false);
    }

    public static boolean ensureTransparentTypesSent(@Nonnull PlayerRef playerRef, @Nonnull Map<Integer, Integer> fakeIdByBaseId, boolean rebuildTextures) {
        if (!rebuildTextures) {
            // Fall back to per-type sending
            return ensureTransparentTypesSent(playerRef, fakeIdByBaseId);
        }

        Set<Integer> sent = SENT_FAKE_IDS.computeIfAbsent(playerRef.getUuid(), _id -> ConcurrentHashMap.newKeySet());

        // Build a single batched UpdateBlockTypes packet with all new types
        Map<Integer, com.hypixel.hytale.protocol.BlockType> batchedTypes = new HashMap<>();
        int maxFakeId = BlockType.getAssetMap().getNextIndex();

        for (Map.Entry<Integer, Integer> entry : fakeIdByBaseId.entrySet()) {
            int fakeId = entry.getValue();
            if (sent.contains(fakeId)) {
                continue;
            }

            BlockType baseType = BlockType.getAssetMap().getAsset(entry.getKey());
            if (baseType == null) {
                continue;
            }

            com.hypixel.hytale.protocol.BlockType packetBlock = buildTransparentPacketBlock(baseType);
            batchedTypes.put(fakeId, packetBlock);
            sent.add(fakeId);
            maxFakeId = Math.max(maxFakeId, fakeId + 1);
        }

        if (batchedTypes.isEmpty()) {
            return false;
        }

        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = maxFakeId;
        update.blockTypes = batchedTypes;
        update.updateBlockTextures = true;
        update.updateModelTextures = false;
        update.updateModels = false;
        update.updateMapGeometry = false;

        playerRef.getPacketHandler().writeNoCache(update);
        return true;
    }

    private static com.hypixel.hytale.protocol.BlockType buildTransparentPacketBlock(@Nonnull BlockType baseType) {
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
        return packetBlock;
    }

    public static void sendTransparentBlockType(
        @Nonnull PlayerRef playerRef,
        int fakeId,
        @Nonnull BlockType baseType,
        boolean rebuildTextures
    ) {
        com.hypixel.hytale.protocol.BlockType packetBlock = buildTransparentPacketBlock(baseType);

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

    @Nullable
    public static BlockSnapshot readBlock(@Nonnull ChunkStore chunkStore, int x, int y, int z) {
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

    public static boolean isEligibleBlockType(@Nonnull BlockType type) {
        return (type.getState() != null);
    }

    @Nullable
    public static BlockType findAnyBlockType() {
        int max = BlockType.getAssetMap().getNextIndex();
        for (int i = 0; i < max; i++) {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            if (type != null && !type.isUnknown() && isEligibleBlockType(type)) {
                return type;
            }
        }
        return null;
    }

    public static void preloadTransparentType(@Nonnull PlayerRef playerRef) {
        int fakeId = allocateFakeId();
        BlockType baseType = findAnyBlockType();
        if (baseType == null) {
            return;
        }
        sendTransparentBlockType(playerRef, fakeId, baseType, true);
    }

    public static void resetPlayer(@Nonnull UUID playerId) {
        SENT_FAKE_IDS.remove(playerId);
    }
}
