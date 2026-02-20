package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/**
 * Fills blocks with air (ID 0) - fully transparent.
 */
public class EmptyBlockFill implements BlockFillType {
    
    @Override
    public int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore) {
        return 0; // Air
    }
    
    @Override
    public boolean requiresPacketUpdate() {
        return false; // Simple block ID replacement, no packet updates needed
    }
    
    @Nonnull
    @Override
    public String getDescription() {
        return "Empty (Air)";
    }
}
