package com.UnobstructedThirdPerson.shape.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/**
 * Fills blocks with a specific custom block ID.
 */
public class CustomBlockFill implements BlockFillType {
    
    private final int blockId;
    
    public CustomBlockFill(int blockId) {
        this.blockId = blockId;
    }
    
    @Override
    public int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore) {
        return blockId;
    }
    
    @Override
    public boolean requiresPacketUpdate() {
        return false; // Simple block ID replacement
    }
    
    @Nonnull
    @Override
    public String getDescription() {
        return "Custom Block (ID: " + blockId + ")";
    }
}
