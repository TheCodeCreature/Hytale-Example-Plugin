package com.UnobstructedThirdPerson.shape.v2.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v1.placeholder.PlaceholderBlockManager;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class PlaceholderFillV2 implements BlockFillTypeV2 {
    
    public static final String PLACEHOLDER_PREFIX = "Placeholder_";
    
    private final DebugStyle debugStyle;
    
    public PlaceholderFillV2() {
        this(DebugStyle.PLACEHOLDER_STYLE);
    }
    
    public PlaceholderFillV2(@Nonnull DebugStyle debugStyle) {
        this.debugStyle = debugStyle;
    }
    
    @Override
    public int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore) {
        BlockType baseType = BlockType.getAssetMap().getAsset(original.blockId());
        if (baseType == null) {
            return 0;
        }
        
        String hitboxType = baseType.getHitboxType();
        String placeholderId = PlaceholderBlockManager.getPlaceholderForHitbox(hitboxType);
        
        int placeholderNumericId = BlockType.getAssetMap().getIndex(placeholderId);
        if (placeholderNumericId == Integer.MIN_VALUE) {
            return 0;
        }
        
        return placeholderNumericId;
    }
    
    @Override
    public boolean requiresPacketUpdate() {
        return true;
    }
    
    @Nonnull
    @Override
    public String getDescription() {
        return "Auto-Placeholder (hitbox-based) V2";
    }
    
    @Nonnull
    @Override
    public DebugStyle getDebugStyle() {
        return debugStyle;
    }
    
    public static boolean isPlaceholderBlockId(int blockId) {
        BlockType replacementType = BlockType.getAssetMap().getAsset(blockId);
        return replacementType != null
                && replacementType.getId() != null
                && replacementType.getId().startsWith(PLACEHOLDER_PREFIX);
    }
}
