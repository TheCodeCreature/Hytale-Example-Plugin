package com.UnobstructedThirdPerson.shape.v2.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

class BlockFillTypeV2Wrapper implements BlockFillTypeV2 {
    
    private final BlockFillTypeV2 delegate;
    private final DebugStyle debugStyle;
    
    BlockFillTypeV2Wrapper(@Nonnull BlockFillTypeV2 delegate, @Nonnull DebugStyle debugStyle) {
        this.delegate = delegate;
        this.debugStyle = debugStyle;
    }
    
    @Override
    public int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore) {
        return delegate.getBlockId(original, chunkStore);
    }
    
    @Override
    public boolean requiresPacketUpdate() {
        return delegate.requiresPacketUpdate();
    }
    
    @Nonnull
    @Override
    public String getDescription() {
        return delegate.getDescription();
    }
    
    @Nonnull
    @Override
    public DebugStyle getDebugStyle() {
        return debugStyle;
    }
}
