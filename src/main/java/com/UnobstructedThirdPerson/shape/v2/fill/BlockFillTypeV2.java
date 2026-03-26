package com.UnobstructedThirdPerson.shape.v2.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public interface BlockFillTypeV2 {
    
    int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore);
    
    boolean requiresPacketUpdate();
    
    @Nonnull
    String getDescription();
    
    @Nonnull
    DebugStyle getDebugStyle();
    
    @Nonnull
    default BlockFillTypeV2 withDebugStyle(@Nonnull DebugStyle debugStyle) {
        return new BlockFillTypeV2Wrapper(this, debugStyle);
    }
}
