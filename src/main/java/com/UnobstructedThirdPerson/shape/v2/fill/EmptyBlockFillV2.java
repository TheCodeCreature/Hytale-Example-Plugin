package com.UnobstructedThirdPerson.shape.v2.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class EmptyBlockFillV2 implements BlockFillTypeV2 {
    
    private final DebugStyle debugStyle;
    
    public EmptyBlockFillV2() {
        this(DebugStyle.EMPTY_STYLE);
    }
    
    public EmptyBlockFillV2(@Nonnull DebugStyle debugStyle) {
        this.debugStyle = debugStyle;
    }
    
    @Override
    public int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore) {
        return 0;
    }
    
    @Override
    public boolean requiresPacketUpdate() {
        return false;
    }
    
    @Nonnull
    @Override
    public String getDescription() {
        return "Empty (Air) V2";
    }
    
    @Nonnull
    @Override
    public DebugStyle getDebugStyle() {
        return debugStyle;
    }
}
