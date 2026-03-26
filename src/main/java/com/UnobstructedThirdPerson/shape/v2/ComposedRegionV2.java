package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v2.fill.BlockFillTypeV2;
import com.UnobstructedThirdPerson.shape.v2.operation.ShapeOperationV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.*;

public class ComposedRegionV2 {
    
    private final Vector3i anchor;
    private final Map<Long, BlockSnapshot> originalBlocks;
    private final Map<Long, BlockFillTypeV2> blockFills;
    private final Map<Long, Integer> computedBlockIds;
    private final Map<Long, DebugStyle> debugStyles;
    private final Set<Long> excludedPositions;
    private final Map<String, Set<Long>> operationRegions;
    private final List<ShapeOperationV2> timeline;
    
    public ComposedRegionV2(
            @Nonnull Vector3i anchor,
            @Nonnull Map<Long, BlockSnapshot> originalBlocks,
            @Nonnull Map<Long, BlockFillTypeV2> blockFills,
            @Nonnull Map<Long, Integer> computedBlockIds,
            @Nonnull Map<Long, DebugStyle> debugStyles,
            @Nonnull Set<Long> excludedPositions,
            @Nonnull Map<String, Set<Long>> operationRegions,
            @Nonnull List<ShapeOperationV2> timeline) {
        this.anchor = anchor;
        this.originalBlocks = new HashMap<>(originalBlocks);
        this.blockFills = new HashMap<>(blockFills);
        this.computedBlockIds = new HashMap<>(computedBlockIds);
        this.debugStyles = new HashMap<>(debugStyles);
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
    public Map<Long, BlockFillTypeV2> getBlockFills() {
        return Collections.unmodifiableMap(blockFills);
    }
    
    @Nonnull
    public Map<Long, Integer> getComputedBlockIds() {
        return Collections.unmodifiableMap(computedBlockIds);
    }
    
    @Nonnull
    public Map<Long, DebugStyle> getDebugStyles() {
        return Collections.unmodifiableMap(debugStyles);
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
    public List<ShapeOperationV2> getTimeline() {
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
        return "ComposedRegionV2{" +
                "anchor=" + anchor +
                ", totalBlocks=" + getTotalBlockCount() +
                ", filledBlocks=" + getFilledBlockCount() +
                ", excludedBlocks=" + getExcludedBlockCount() +
                ", operations=" + timeline.size() +
                '}';
    }
}
