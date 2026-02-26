package com.UnobstructedThirdPerson.preview;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.placeholder.TransparentBlockUtils;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages preview/ghost blocks shown to individual players.
 * Preview blocks are client-side only and don't affect the actual world state.
 * Multiple preview blocks can be shown simultaneously.
 */
public class PreviewBlockManager {
    
    private static final Logger LOGGER = Logger.getLogger("PreviewBlockManager");
    
    // Per-player instances
    private static final Map<UUID, PreviewBlockManager> INSTANCES = new ConcurrentHashMap<>();
    
    private final PlayerRef playerRef;
    private final World world;
    
    // Position -> original block snapshot for restoration
    private final Map<Vector3i, BlockSnapshot> activePreviewBlocks = new HashMap<>();
    
    // Position -> preview block ID to display
    private final Map<Vector3i, Integer> previewBlockIds = new HashMap<>();
    
    public PreviewBlockManager(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        this.playerRef = playerRef;
        this.world = world;
    }
    
    /**
     * Get or create a preview block manager for a player.
     */
    @Nonnull
    public static PreviewBlockManager getOrCreate(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        return INSTANCES.computeIfAbsent(playerRef.getUuid(), uuid -> {
            LOGGER.info("[PreviewBlocks] Created manager for player: " + playerRef.getUsername());
            return new PreviewBlockManager(playerRef, world);
        });
    }
    
    /**
     * Get an existing preview block manager for a player.
     */
    @Nullable
    public static PreviewBlockManager get(@Nonnull UUID playerId) {
        return INSTANCES.get(playerId);
    }
    
    /**
     * Remove and cleanup a player's preview block manager.
     */
    public static void remove(@Nonnull UUID playerId) {
        PreviewBlockManager instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.clearAll();
            LOGGER.info("[PreviewBlocks] Removed manager for player: " + playerId);
        }
    }
    
    /**
     * Add a single preview block at the specified position.
     * 
     * @param position World position for the preview block
     * @param previewBlockId Block type ID to display as preview
     * @return true if the preview was added successfully
     */
    public boolean addPreview(@Nonnull Vector3i position, int previewBlockId) {
        ChunkStore chunkStore = world.getChunkStore();
        
        // Read the current block snapshot at this position
        BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, position.x, position.y, position.z);
        if (snapshot == null) {
            LOGGER.warning("[PreviewBlocks] Failed to read block at " + position);
            return false;
        }
        
        // Store the original block for restoration
        activePreviewBlocks.put(position, snapshot);
        previewBlockIds.put(position, previewBlockId);
        
        // Send the preview block to the client
        sendBlockUpdate(position, previewBlockId, (short) 0, (byte) 0);
        
        return true;
    }
    
    /**
     * Add multiple preview blocks at once.
     * 
     * @param previews Map of positions to preview block IDs
     */
    public void addPreviews(@Nonnull Map<Vector3i, Integer> previews) {
        for (Map.Entry<Vector3i, Integer> entry : previews.entrySet()) {
            addPreview(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Remove a preview block at the specified position and restore the original.
     * 
     * @param position Position to remove preview from
     * @return true if a preview was removed
     */
    public boolean removePreview(@Nonnull Vector3i position) {
        BlockSnapshot snapshot = activePreviewBlocks.remove(position);
        previewBlockIds.remove(position);
        
        if (snapshot != null) {
            // Restore the original block
            sendBlockUpdate(position, snapshot.blockId(), snapshot.filler(), snapshot.rotation());
            return true;
        }
        
        return false;
    }
    
    /**
     * Remove multiple preview blocks at once.
     * 
     * @param positions Positions to remove previews from
     */
    public void removePreviews(@Nonnull Collection<Vector3i> positions) {
        for (Vector3i position : positions) {
            removePreview(position);
        }
    }
    
    /**
     * Clear all preview blocks and restore originals.
     */
    public void clearAll() {
        // Restore all original blocks
        for (Map.Entry<Vector3i, BlockSnapshot> entry : activePreviewBlocks.entrySet()) {
            BlockSnapshot snapshot = entry.getValue();
            sendBlockUpdate(entry.getKey(), snapshot.blockId(), snapshot.filler(), snapshot.rotation());
        }
        
        activePreviewBlocks.clear();
        previewBlockIds.clear();
    }
    
    /**
     * Update an existing preview block to a different block type.
     * 
     * @param position Position of the preview to update
     * @param newPreviewBlockId New block type ID to display
     * @return true if the preview was updated
     */
    public boolean updatePreview(@Nonnull Vector3i position, int newPreviewBlockId) {
        if (!activePreviewBlocks.containsKey(position)) {
            return false;
        }
        
        previewBlockIds.put(position, newPreviewBlockId);
        sendBlockUpdate(position, newPreviewBlockId, (short) 0, (byte) 0);
        return true;
    }
    
    /**
     * Check if a preview block exists at the given position.
     */
    public boolean hasPreview(@Nonnull Vector3i position) {
        return activePreviewBlocks.containsKey(position);
    }
    
    /**
     * Get the number of active preview blocks.
     */
    public int getPreviewCount() {
        return activePreviewBlocks.size();
    }
    
    /**
     * Get all active preview positions.
     */
    @Nonnull
    public Set<Vector3i> getPreviewPositions() {
        return new HashSet<>(activePreviewBlocks.keySet());
    }
    
    /**
     * Send a block update packet to the client.
     */
    private void sendBlockUpdate(@Nonnull Vector3i position, int blockId, short filler, byte rotation) {
        playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
            position.x, position.y, position.z,
            blockId, filler, rotation
        ));
    }
    
    /**
     * Add a preview block using a block type name instead of ID.
     * 
     * @param position World position for the preview block
     * @param blockTypeName Block type name (e.g., "Stone", "Glass")
     * @return true if the preview was added successfully
     */
    public boolean addPreviewByName(@Nonnull Vector3i position, @Nonnull String blockTypeName) {
        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeName);
        if (blockType == null) {
            LOGGER.warning("[PreviewBlocks] Block type not found: " + blockTypeName);
            return false;
        }
        
        int blockId = BlockType.getAssetMap().getIndex(blockTypeName);
        return addPreview(position, blockId);
    }
    
    /**
     * Add a box of preview blocks.
     * 
     * @param min Minimum corner of the box
     * @param max Maximum corner of the box
     * @param previewBlockId Block type ID to display
     */
    public void addPreviewBox(@Nonnull Vector3i min, @Nonnull Vector3i max, int previewBlockId) {
        for (int x = min.x; x <= max.x; x++) {
            for (int y = min.y; y <= max.y; y++) {
                for (int z = min.z; z <= max.z; z++) {
                    addPreview(new Vector3i(x, y, z), previewBlockId);
                }
            }
        }
    }
    
    /**
     * Add a hollow box outline of preview blocks.
     * 
     * @param min Minimum corner of the box
     * @param max Maximum corner of the box
     * @param previewBlockId Block type ID to display
     */
    public void addPreviewBoxOutline(@Nonnull Vector3i min, @Nonnull Vector3i max, int previewBlockId) {
        for (int x = min.x; x <= max.x; x++) {
            for (int y = min.y; y <= max.y; y++) {
                for (int z = min.z; z <= max.z; z++) {
                    // Only add blocks on the edges
                    boolean isEdge = x == min.x || x == max.x || 
                                   y == min.y || y == max.y || 
                                   z == min.z || z == max.z;
                    
                    if (isEdge) {
                        addPreview(new Vector3i(x, y, z), previewBlockId);
                    }
                }
            }
        }
    }
}
