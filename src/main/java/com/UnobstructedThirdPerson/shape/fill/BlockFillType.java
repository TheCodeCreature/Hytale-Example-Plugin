package com.UnobstructedThirdPerson.shape.fill;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/**
 * Defines what block to use when filling a region - the "material" parameter.
 * This is separate from geometry operations, allowing parametric editing
 * (change fill without changing geometry, like changing material in CAD).
 */
public interface BlockFillType {
    /**
     * Get the block ID to use for this position.
     * 
     * @param original The original block at this position
     * @param chunkStore The chunk store for reading additional block data if needed
     * @return The block ID to replace with
     */
    int getBlockId(@Nonnull BlockSnapshot original, @Nonnull ChunkStore chunkStore);
    
    /**
     * Whether this fill type requires sending UpdateBlockTypes packets.
     * True for placeholder blocks that need texture/geometry modifications.
     * False for simple block ID replacements.
     */
    boolean requiresPacketUpdate();
    
    /**
     * Get a description of this fill type for debugging/UI.
     */
    @Nonnull
    String getDescription();
}
