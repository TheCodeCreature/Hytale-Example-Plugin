package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Final computed result from the ShapeCompositor.
 * Contains all block assignments with their fill types, ready for packet sending.
 */
public class ComposedRegion {
    
    private final Vector3i anchor;
    private final Map<Long, BlockSnapshot> originalBlocks;
    private final Map<Long, BlockFillType> blockFills;
    private final Map<Long, Integer> computedBlockIds;
    private final Set<Long> excludedPositions;
    private final Map<String, Set<Long>> operationRegions;
    private final List<ShapeOperation> timeline;
    
    public ComposedRegion(
            @Nonnull Vector3i anchor,
            @Nonnull Map<Long, BlockSnapshot> originalBlocks,
            @Nonnull Map<Long, BlockFillType> blockFills,
            @Nonnull Map<Long, Integer> computedBlockIds,
            @Nonnull Set<Long> excludedPositions,
            @Nonnull Map<String, Set<Long>> operationRegions,
            @Nonnull List<ShapeOperation> timeline) {
        this.anchor = anchor;
        this.originalBlocks = new HashMap<>(originalBlocks);
        this.blockFills = new HashMap<>(blockFills);
        this.computedBlockIds = new HashMap<>(computedBlockIds);
        this.excludedPositions = new HashSet<>(excludedPositions);
        this.operationRegions = new HashMap<>(operationRegions);
        this.timeline = new ArrayList<>(timeline);
    }
    
    @Nonnull
    public Vector3i getAnchor() {
        return anchor;
    }
    
    @Nonnull
    public Map<Long, BlockSnapshot> getOriginalBlocks() {
        return Collections.unmodifiableMap(originalBlocks);
    }
    
    @Nonnull
    public Map<Long, BlockFillType> getBlockFills() {
        return Collections.unmodifiableMap(blockFills);
    }
    
    @Nonnull
    public Map<Long, Integer> getComputedBlockIds() {
        return Collections.unmodifiableMap(computedBlockIds);
    }
    
    @Nonnull
    public Set<Long> getExcludedPositions() {
        return Collections.unmodifiableSet(excludedPositions);
    }
    
    @Nonnull
    public Set<Long> getOperationPositions(@Nonnull String operationId) {
        return Collections.unmodifiableSet(
                operationRegions.getOrDefault(operationId, Collections.emptySet())
        );
    }
    
    @Nonnull
    public Map<String, Set<Long>> getAllOperationRegions() {
        return Collections.unmodifiableMap(operationRegions);
    }
    
    @Nonnull
    public List<ShapeOperation> getTimeline() {
        return Collections.unmodifiableList(timeline);
    }
    
    public int getTotalBlockCount() {
        return originalBlocks.size();
    }
    
    public int getExcludedBlockCount() {
        return excludedPositions.size();
    }
    
    public int getFilledBlockCount() {
        return computedBlockIds.size();
    }
    
    @Override
    public String toString() {
        return "ComposedRegion{" +
                "anchor=" + anchor +
                ", totalBlocks=" + getTotalBlockCount() +
                ", filledBlocks=" + getFilledBlockCount() +
                ", excludedBlocks=" + getExcludedBlockCount() +
                ", operations=" + timeline.size() +
                '}';
    }
}
