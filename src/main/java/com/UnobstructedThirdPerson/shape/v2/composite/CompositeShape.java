package com.UnobstructedThirdPerson.shape.v2.composite;

import com.UnobstructedThirdPerson.shape.v2.fill.BlockFillTypeV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * A vector-based composite shape — the result of combining multiple shape operations
 * (typically via UNION) into a single referenceable point set.
 * <p>
 * Stores parallel arrays of local-space packed block positions alongside per-point
 * metadata (fill, origin operation ID, debug style). Immutable once built.
 * Created during the build phase, reused every frame without recomputation.
 */
public class CompositeShape {
    
    private final long[] localPositions;
    private final BlockFillTypeV2[] fills;
    private final String[] originIds;
    private final DebugStyle[] debugStyles;
    private final long[] excludedPositions;
    private final String[] excludedOriginIds;
    private final DebugStyle[] excludedDebugStyles;
    private final Map<Long, Integer> positionIndex;
    private final Set<Long> excludedPositionSet;
    
    private CompositeShape(long[] localPositions, BlockFillTypeV2[] fills,
                           String[] originIds, DebugStyle[] debugStyles,
                           long[] excludedPositions, String[] excludedOriginIds,
                           DebugStyle[] excludedDebugStyles) {
        this.localPositions = localPositions;
        this.fills = fills;
        this.originIds = originIds;
        this.debugStyles = debugStyles;
        this.excludedPositions = excludedPositions;
        this.excludedOriginIds = excludedOriginIds;
        this.excludedDebugStyles = excludedDebugStyles;
        
        this.positionIndex = new HashMap<>(localPositions.length * 2);
        for (int i = 0; i < localPositions.length; i++) {
            positionIndex.put(localPositions[i], i);
        }
        this.excludedPositionSet = new HashSet<>(excludedPositions.length * 2);
        for (long pos : excludedPositions) {
            excludedPositionSet.add(pos);
        }
    }
    
    public int size() {
        return localPositions.length;
    }
    
    public int excludedSize() {
        return excludedPositions.length;
    }
    
    @Nonnull
    public long[] getLocalPositions() {
        return localPositions;
    }
    
    @Nonnull
    public BlockFillTypeV2[] getFills() {
        return fills;
    }
    
    @Nonnull
    public String[] getOriginIds() {
        return originIds;
    }
    
    @Nonnull
    public DebugStyle[] getDebugStyles() {
        return debugStyles;
    }
    
    @Nonnull
    public long[] getExcludedPositions() {
        return excludedPositions;
    }
    
    @Nonnull
    public String[] getExcludedOriginIds() {
        return excludedOriginIds;
    }
    
    @Nonnull
    public DebugStyle[] getExcludedDebugStyles() {
        return excludedDebugStyles;
    }
    
    public long getPosition(int index) {
        return localPositions[index];
    }
    
    @Nullable
    public BlockFillTypeV2 getFill(int index) {
        return fills[index];
    }
    
    @Nullable
    public String getOriginId(int index) {
        return originIds[index];
    }
    
    @Nonnull
    public DebugStyle getDebugStyle(int index) {
        return debugStyles[index];
    }
    
    public int indexOf(long packedPos) {
        Integer idx = positionIndex.get(packedPos);
        return idx != null ? idx : -1;
    }
    
    public boolean containsExcluded(long packedPos) {
        return excludedPositionSet.contains(packedPos);
    }
    
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final Map<Long, PointData> points = new LinkedHashMap<>();
        private final Map<Long, PointData> excludedPoints = new LinkedHashMap<>();
        
        @Nonnull
        public Builder addPoint(long packedPos, @Nullable BlockFillTypeV2 fill,
                                @Nullable String originId, @Nonnull DebugStyle debugStyle) {
            points.put(packedPos, new PointData(fill, originId, debugStyle));
            return this;
        }
        
        @Nonnull
        public Builder addExcludedPoint(long packedPos, @Nullable String originId,
                                        @Nonnull DebugStyle debugStyle) {
            excludedPoints.put(packedPos, new PointData(null, originId, debugStyle));
            return this;
        }
        
        @Nonnull
        public Builder removePoint(long packedPos) {
            points.remove(packedPos);
            return this;
        }
        
        public boolean containsPoint(long packedPos) {
            return points.containsKey(packedPos);
        }
        
        @Nullable
        public String getOriginId(long packedPos) {
            PointData data = points.get(packedPos);
            return data != null ? data.originId : null;
        }
        
        public int size() {
            return points.size();
        }
        
        @Nonnull
        public Set<Long> getPositionSet() {
            return Collections.unmodifiableSet(points.keySet());
        }
        
        @Nonnull
        public CompositeShape build() {
            long[] positions = new long[points.size()];
            BlockFillTypeV2[] fills = new BlockFillTypeV2[points.size()];
            String[] origins = new String[points.size()];
            DebugStyle[] styles = new DebugStyle[points.size()];
            
            int i = 0;
            for (Map.Entry<Long, PointData> entry : points.entrySet()) {
                positions[i] = entry.getKey();
                PointData data = entry.getValue();
                fills[i] = data.fill;
                origins[i] = data.originId;
                styles[i] = data.debugStyle;
                i++;
            }
            
            long[] exPositions = new long[excludedPoints.size()];
            String[] exOrigins = new String[excludedPoints.size()];
            DebugStyle[] exStyles = new DebugStyle[excludedPoints.size()];
            
            int j = 0;
            for (Map.Entry<Long, PointData> entry : excludedPoints.entrySet()) {
                exPositions[j] = entry.getKey();
                PointData data = entry.getValue();
                exOrigins[j] = data.originId;
                exStyles[j] = data.debugStyle;
                j++;
            }
            
            return new CompositeShape(positions, fills, origins, styles,
                    exPositions, exOrigins, exStyles);
        }
        
        private record PointData(@Nullable BlockFillTypeV2 fill, @Nullable String originId,
                                 @Nonnull DebugStyle debugStyle) {
        }
    }
    
    @Override
    public String toString() {
        return "CompositeShape{points=" + localPositions.length
                + ", excluded=" + excludedPositions.length + "}";
    }
}
