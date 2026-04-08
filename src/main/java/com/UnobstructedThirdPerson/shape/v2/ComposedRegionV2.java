package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v2.fill.BlockFillTypeV2;
import com.UnobstructedThirdPerson.shape.v2.operation.ShapeOperationV2;
import com.UnobstructedThirdPerson.shape.v2.visual.BoundingShapeDebug;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class ComposedRegionV2 {
    
    private final Vector3d anchor;
    private final Map<Long, VoxelEntry> voxelMap;
    private final Map<Long, Integer> computedBlockIds;
    private final Map<String, Set<Long>> operationRegions;
    private final List<ShapeOperationV2> timeline;
    private final List<BoundingShapeDebug> boundingShapes;
    
    public ComposedRegionV2(
            @Nonnull Vector3d anchor,
            @Nonnull Map<Long, VoxelEntry> voxelMap,
            @Nonnull Map<Long, Integer> computedBlockIds,
            @Nonnull Map<String, Set<Long>> operationRegions,
            @Nonnull List<ShapeOperationV2> timeline,
            @Nonnull List<BoundingShapeDebug> boundingShapes) {
        this.anchor = anchor;
        this.computedBlockIds = new HashMap<>(computedBlockIds);
        this.operationRegions = new HashMap<>(operationRegions);
        this.timeline = new ArrayList<>(timeline);
        this.boundingShapes = new ArrayList<>(boundingShapes);
        
        // Defensive copy of voxel entries
        this.voxelMap = new HashMap<>();
        for (Map.Entry<Long, VoxelEntry> entry : voxelMap.entrySet()) {
            this.voxelMap.put(entry.getKey(), entry.getValue().copy());
        }
    }
    
    @Nonnull
    public Vector3d getAnchor() {
        return anchor;
    }
    
    @Nonnull
    public Map<Long, VoxelEntry> getVoxelMap() {
        return Collections.unmodifiableMap(voxelMap);
    }
    
    @Nonnull
    public Map<Long, BlockSnapshot> getOriginalBlocks() {
        Map<Long, BlockSnapshot> result = new HashMap<>();
        for (Map.Entry<Long, VoxelEntry> entry : voxelMap.entrySet()) {
            VoxelEntry voxel = entry.getValue();
            if (voxel.getOriginal() != null && voxel.hasFill()) {
                result.put(entry.getKey(), voxel.getOriginal());
            }
        }
        return Collections.unmodifiableMap(result);
    }
    
    @Nonnull
    public Map<Long, BlockFillTypeV2> getBlockFills() {
        Map<Long, BlockFillTypeV2> result = new HashMap<>();
        for (Map.Entry<Long, VoxelEntry> entry : voxelMap.entrySet()) {
            VoxelEntry voxel = entry.getValue();
            if (voxel.hasFill()) {
                result.put(entry.getKey(), voxel.getFill());
            }
        }
        return Collections.unmodifiableMap(result);
    }
    
    @Nonnull
    public Map<Long, Integer> getComputedBlockIds() {
        return Collections.unmodifiableMap(computedBlockIds);
    }
    
    @Nonnull
    public Map<Long, DebugStyle> getDebugStyles() {
        Map<Long, DebugStyle> result = new HashMap<>();
        for (Map.Entry<Long, VoxelEntry> entry : voxelMap.entrySet()) {
            VoxelEntry voxel = entry.getValue();
            if (voxel.getDebugStyle() != null && voxel.getDebugStyle().isEnabled()) {
                result.put(entry.getKey(), voxel.getDebugStyle());
            }
        }
        return Collections.unmodifiableMap(result);
    }
    
    @Nonnull
    public Set<Long> getExcludedPositions() {
        Set<Long> result = new HashSet<>();
        for (Map.Entry<Long, VoxelEntry> entry : voxelMap.entrySet()) {
            if (entry.getValue().isExcluded()) {
                result.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(result);
    }
    
    @Nullable
    public String getOwner(long pos) {
        VoxelEntry entry = voxelMap.get(pos);
        return entry != null ? entry.getOwnerId() : null;
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
    
    @Nonnull
    public List<BoundingShapeDebug> getBoundingShapes() {
        return Collections.unmodifiableList(boundingShapes);
    }
    
    public int getTotalBlockCount() {
        int count = 0;
        for (VoxelEntry voxel : voxelMap.values()) {
            if (voxel.getOriginal() != null && voxel.hasFill()) {
                count++;
            }
        }
        return count;
    }
    
    public int getExcludedBlockCount() {
        int count = 0;
        for (VoxelEntry voxel : voxelMap.values()) {
            if (voxel.isExcluded()) {
                count++;
            }
        }
        return count;
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
