package com.UnobtrusiveThirdPerson.camera;

import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class TransparentBlockManager {
    private final PlayerRef playerRef;
    private final World world;
    private final Map<Long, ManagedBlock> activeBlocks = new ConcurrentHashMap<>();
    private final long decayThresholdMillis;
    private final int applyDelayMillis;

    TransparentBlockManager(PlayerRef playerRef, World world, long decayThresholdMillis, int applyDelayMillis) {
        this.playerRef = playerRef;
        this.world = world;
        this.decayThresholdMillis = decayThresholdMillis;
        this.applyDelayMillis = applyDelayMillis;
    }

    void submitBlocks(@Nonnull List<BlockSnapshot> blocks, TransparentVariantProvider variantProvider, TypeSender typeSender) {
        // Build fake ID map for new blocks
        Map<Integer, Integer> fakeIdByBaseId = new HashMap<>();
        for (BlockSnapshot snapshot : blocks) {
            fakeIdByBaseId.computeIfAbsent(snapshot.blockId(), variantProvider::getTransparentVariantId);
        }

        // Ensure transparent types are sent to client
        boolean sentAny = typeSender.ensureTransparentTypesSent(playerRef, fakeIdByBaseId);
        
        // If we sent new types, delay application slightly
        if (sentAny) {
            CompletableFuture.delayedExecutor(applyDelayMillis, TimeUnit.MILLISECONDS).execute(() -> {
                world.execute(() -> applyBlocks(blocks, fakeIdByBaseId));
            });
        } else {
            applyBlocks(blocks, fakeIdByBaseId);
        }
    }

    private void applyBlocks(@Nonnull List<BlockSnapshot> blocks, @Nonnull Map<Integer, Integer> fakeIdByBaseId) {
        Set<Long> submittedPositions = new HashSet<>();
        
        for (BlockSnapshot snapshot : blocks) {
            long pos = BlockUtil.packUnchecked(snapshot.x(), snapshot.y(), snapshot.z());
            submittedPositions.add(pos);
            
            ManagedBlock existing = activeBlocks.get(pos);
            if (existing != null) {
                // Block already managed - just refresh timestamp
                existing.refresh();
            } else {
                // New block - apply transparent variant
                int fakeId = fakeIdByBaseId.get(snapshot.blockId());
                ManagedBlock managed = new ManagedBlock(snapshot, fakeId);
                activeBlocks.put(pos, managed);
                
                playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                    snapshot.x(), snapshot.y(), snapshot.z(), fakeId, snapshot.filler(), snapshot.rotation()
                ));
            }
        }
    }

    void cleanup() {
        List<Long> toRemove = new ArrayList<>();
        
        for (Map.Entry<Long, ManagedBlock> entry : activeBlocks.entrySet()) {
            if (entry.getValue().isExpired(decayThresholdMillis)) {
                toRemove.add(entry.getKey());
            }
        }
        
        if (!toRemove.isEmpty()) {
            world.execute(() -> {
                for (Long pos : toRemove) {
                    ManagedBlock managed = activeBlocks.remove(pos);
                    if (managed != null) {
                        BlockSnapshot original = managed.original;
                        playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                            original.x(), original.y(), original.z(), original.blockId(), original.filler(), original.rotation()
                        ));
                    }
                }
            });
        }
    }

    void restoreAll() {
        List<ManagedBlock> toRestore = new ArrayList<>(activeBlocks.values());
        activeBlocks.clear();
        
        if (!toRestore.isEmpty()) {
            world.execute(() -> {
                for (ManagedBlock managed : toRestore) {
                    BlockSnapshot original = managed.original;
                    playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                        original.x(), original.y(), original.z(), original.blockId(), original.filler(), original.rotation()
                    ));
                }
            });
        }
    }

    void shutdown() {
        restoreAll();
    }

    @FunctionalInterface
    interface TransparentVariantProvider {
        int getTransparentVariantId(int baseId);
    }

    @FunctionalInterface
    interface TypeSender {
        boolean ensureTransparentTypesSent(PlayerRef playerRef, Map<Integer, Integer> fakeIdByBaseId);
    }
}
