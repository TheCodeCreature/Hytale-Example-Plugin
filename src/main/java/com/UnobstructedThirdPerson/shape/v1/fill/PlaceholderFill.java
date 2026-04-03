package com.UnobstructedThirdPerson.shape.v1.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v1.placeholder.PlaceholderBlockManager;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Auto-selects placeholder blocks based on the original block's hitbox type.
 * Uses PlaceholderBlockManager to intelligently map hitbox types to appropriate placeholders.
 */
public class PlaceholderFill implements BlockFillType {

    public static final String PLACEHOLDER_PREFIX = "Placeholder_";
    
    @Override
    public int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore) {
        BlockType baseType = BlockType.getAssetMap().getAsset(original.blockId());
        if (baseType == null) {
            return 0; // Fallback to air if block type not found
        }
        
        String hitboxType = baseType.getHitboxType();
        String placeholderId = PlaceholderBlockManager.getPlaceholderForHitbox(hitboxType);
        
        int placeholderNumericId = BlockType.getAssetMap().getIndex(placeholderId);
        if (placeholderNumericId == Integer.MIN_VALUE) {
            return 0; // Fallback to air if placeholder not found
        }
        
        return placeholderNumericId;
    }

    public static boolean isPlaceholderBlockId(int blockId) {
        BlockType replacementType = BlockType.getAssetMap().getAsset(blockId);
        return isPlaceholderBlockType(replacementType);
    }

    public static boolean isPlaceholderBlockType(@Nullable BlockType blockType) {
        return blockType != null
                && blockType.getId() != null
                && blockType.getId().startsWith(PLACEHOLDER_PREFIX);
    }
    
    @Override
    public boolean requiresPacketUpdate() {
        return true; // Placeholders need UpdateBlockTypes packets for texture modifications
    }
    
    @Nonnull
    @Override
    public String getDescription() {
        return "Auto-Placeholder (hitbox-based)";
    }
}
