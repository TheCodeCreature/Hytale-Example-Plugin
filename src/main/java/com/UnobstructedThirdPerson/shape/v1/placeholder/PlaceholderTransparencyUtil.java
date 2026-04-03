package com.UnobstructedThirdPerson.shape.v1.placeholder;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Utility for creating transparent placeholder blocks that preserve the original block's
 * geometry while replacing textures with Editor_Empty.png for transparency.
 * 
 * This handles sending UpdateBlockTypes packets to modify placeholder block definitions
 * on the client side.
 */
public class PlaceholderTransparencyUtil {
    
    private static final Logger LOGGER = Logger.getLogger("PlaceholderTransparencyUtil");
    private static final String EDITOR_EMPTY_TEXTURE = "BlockTextures/Editor_Empty.png";
    
    // Cache of modified placeholder packets per player
    // Key: PlayerUUID -> (PlaceholderBlockId -> ModifiedPacket)
    private static final Map<String, Map<Integer, com.hypixel.hytale.protocol.BlockType>> playerModifiedPlaceholders = 
            new ConcurrentHashMap<>();
    
    // Cache of original placeholder packets for restoration
    // Key: PlaceholderBlockId -> OriginalPacket
    private static final Map<Integer, com.hypixel.hytale.protocol.BlockType> originalPlaceholderPackets = 
            new ConcurrentHashMap<>();
    
    /**
     * Prepares a placeholder block to be transparent by sending an UpdateBlockTypes packet
     * that modifies the placeholder's textures to Editor_Empty.png while preserving the
     * original block's geometry.
     * 
     * @param playerRef The player to send the packet to
     * @param originalBlockId The original block ID whose geometry should be preserved
     * @param hitboxType The hitbox type to select the appropriate placeholder
     * @return The numeric ID of the placeholder block to use, or null if failed
     */
    @Nullable
    public static Integer prepareTransparentPlaceholder(
            @Nonnull PlayerRef playerRef,
            int originalBlockId,
            @Nonnull String hitboxType) {
        
        BlockType originalType = BlockType.getAssetMap().getAsset(originalBlockId);
        if (originalType == null) {
            LOGGER.warning("Original block type not found for ID: " + originalBlockId);
            return null;
        }
        
        // Get the appropriate placeholder for this hitbox type
        String placeholderId = PlaceholderBlockManager.getPlaceholderForHitbox(hitboxType);
        BlockType placeholderType = BlockType.getAssetMap().getAsset(placeholderId);
        if (placeholderType == null) {
            LOGGER.warning("Placeholder block " + placeholderId + " not found for hitbox " + hitboxType);
            return null;
        }
        
        int placeholderNumericId = BlockType.getAssetMap().getIndex(placeholderId);
        String playerUuid = playerRef.getUuid().toString();
        
        // Check if we've already modified this placeholder for this player
        Map<Integer, com.hypixel.hytale.protocol.BlockType> playerCache = 
                playerModifiedPlaceholders.get(playerUuid);
        if (playerCache != null && playerCache.containsKey(placeholderNumericId)) {
            // Already sent, no need to send again
            return placeholderNumericId;
        }
        
        // Cache the original placeholder packet if not already cached
        if (!originalPlaceholderPackets.containsKey(placeholderNumericId)) {
            originalPlaceholderPackets.put(placeholderNumericId, placeholderType.toPacket());
        }
        
        // Clone the original block's packet to preserve geometry
        com.hypixel.hytale.protocol.BlockType basePacket = originalType.toPacket();
        com.hypixel.hytale.protocol.BlockType modifiedPacket = 
                new com.hypixel.hytale.protocol.BlockType(basePacket);
        
        // Override textures to Editor_Empty
        BlockTextures emptyTextures = new BlockTextures(
            EDITOR_EMPTY_TEXTURE, // top
            EDITOR_EMPTY_TEXTURE, // bottom
            EDITOR_EMPTY_TEXTURE, // front
            EDITOR_EMPTY_TEXTURE, // back
            EDITOR_EMPTY_TEXTURE, // left
            EDITOR_EMPTY_TEXTURE, // right
            1.0f // weight
        );
        modifiedPacket.cubeTextures = new BlockTextures[] { emptyTextures };
        
//        ModelTexture emptyModelTexture = new ModelTexture(EDITOR_EMPTY_TEXTURE, 1.0f);
//        modifiedPacket.modelTexture = new ModelTexture[] { emptyModelTexture };
        
        // Preserve original drawType and enable alpha blending
//        modifiedPacket.drawType = basePacket.drawType;
        modifiedPacket.drawType = DrawType.Cube;
        modifiedPacket.requiresAlphaBlending = true;
        modifiedPacket.material = BlockMaterial.Empty;

        // Send the modified placeholder definition to the client
        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = BlockType.getAssetMap().getNextIndex();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
        blockTypes.put(placeholderNumericId, modifiedPacket);
        update.blockTypes = blockTypes;
        update.updateBlockTextures = true;
        update.updateModelTextures = true;
        update.updateModels = true;
        update.updateMapGeometry = true;
        playerRef.getPacketHandler().writeNoCache(update);
        
        // Cache this modified packet for this player
        playerModifiedPlaceholders.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(placeholderNumericId, modifiedPacket);
        
        LOGGER.fine("Prepared transparent placeholder " + placeholderId + " (id=" + placeholderNumericId + 
                ") for block " + originalType.getId() + " (hitbox=" + hitboxType + ")");
        
        return placeholderNumericId;
    }
    
    /**
     * Restores all modified placeholder blocks for a player to their original definitions.
     * Should be called when the player disconnects or the transparency volume is shut down.
     * 
     * @param playerRef The player to restore placeholders for
     */
    public static void restoreAllPlaceholders(@Nonnull PlayerRef playerRef) {
        String playerUuid = playerRef.getUuid().toString();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> playerCache = 
                playerModifiedPlaceholders.remove(playerUuid);
        
        if (playerCache == null || playerCache.isEmpty()) {
            return;
        }
        
        // Send restore packet with all original placeholder definitions
        UpdateBlockTypes restore = new UpdateBlockTypes();
        restore.type = UpdateType.AddOrUpdate;
        restore.maxId = BlockType.getAssetMap().getNextIndex();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> restoreTypes = new HashMap<>();
        
        for (Integer placeholderId : playerCache.keySet()) {
            com.hypixel.hytale.protocol.BlockType original = originalPlaceholderPackets.get(placeholderId);
            if (original != null) {
                restoreTypes.put(placeholderId, original);
            }
        }
        
        if (!restoreTypes.isEmpty()) {
            restore.blockTypes = restoreTypes;
            restore.updateBlockTextures = true;
            restore.updateModelTextures = true;
            restore.updateModels = true;
            restore.updateMapGeometry = true;
            playerRef.getPacketHandler().writeNoCache(restore);
            
            LOGGER.info("Restored " + restoreTypes.size() + " placeholder definitions for player " + 
                    playerRef.getUsername());
        }
    }
    
    /**
     * Clears all cached data for a player.
     * Should be called when a player disconnects.
     * 
     * @param playerRef The player to clear cache for
     */
    public static void clearPlayerCache(@Nonnull PlayerRef playerRef) {
        String playerUuid = playerRef.getUuid().toString();
        playerModifiedPlaceholders.remove(playerUuid);
    }
}
