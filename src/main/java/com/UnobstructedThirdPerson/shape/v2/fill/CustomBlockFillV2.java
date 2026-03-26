package com.UnobstructedThirdPerson.shape.v2.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class CustomBlockFillV2 implements BlockFillTypeV2 {
    
    private final int blockId;
    private final DebugStyle debugStyle;
    
    public CustomBlockFillV2(int blockId) {
        this(blockId, DebugStyle.NONE);
    }
    
    public CustomBlockFillV2(int blockId, @Nonnull DebugStyle debugStyle) {
        this.blockId = blockId;
        this.debugStyle = debugStyle;
    }
    
    @Override
    public int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore) {
        return blockId;
    }
    
    @Override
    public boolean requiresPacketUpdate() {
        return false;
    }
    
    @Nonnull
    @Override
    public String getDescription() {
        return "Custom Block (ID: " + blockId + ") V2";
    }
    
    @Nonnull
    @Override
    public DebugStyle getDebugStyle() {
        return debugStyle;
    }
}
