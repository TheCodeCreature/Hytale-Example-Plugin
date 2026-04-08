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
    
    private CompositeShape(long[] localPositions, BlockFillTypeV2[] fills,
                           String[] originIds, DebugStyle[] debugStyles) {
        this.localPositions = localPositions;
        this.fills = fills;
        this.originIds = originIds;
        this.debugStyles = debugStyles;
    }
    
    public int size() {
        return localPositions.length;
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
    
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final Map<Long, PointData> points = new LinkedHashMap<>();
        
        /**
         * Add a point to the composite. If a point at the same position already
         * exists, it is overwritten (last-write wins, matching operation priority ordering).
         */
        @Nonnull
        public Builder addPoint(long packedPos, @Nullable BlockFillTypeV2 fill,
                                @Nullable String originId, @Nonnull DebugStyle debugStyle) {
            points.put(packedPos, new PointData(fill, originId, debugStyle));
            return this;
        }
        
        /**
         * Remove a point from the composite (used by SUBTRACT).
         */
        @Nonnull
        public Builder removePoint(long packedPos) {
            points.remove(packedPos);
            return this;
        }
        
        /**
         * Check if a position exists in the current point set.
         */
        public boolean containsPoint(long packedPos) {
            return points.containsKey(packedPos);
        }
        
        /**
         * Get the origin operation ID for a position, or null if not present.
         */
        @Nullable
        public String getOriginId(long packedPos) {
            PointData data = points.get(packedPos);
            return data != null ? data.originId : null;
        }
        
        /**
         * Get the current number of points.
         */
        public int size() {
            return points.size();
        }
        
        /**
         * Get all current positions as a set (for intersection/reference checks).
         */
        @Nonnull
        public Set<Long> getPositionSet() {
            return Collections.unmodifiableSet(points.keySet());
        }
        
        @Nonnull
        public CompositeShape build() {
            int size = points.size();
            long[] positions = new long[size];
            BlockFillTypeV2[] fills = new BlockFillTypeV2[size];
            String[] origins = new String[size];
            DebugStyle[] styles = new DebugStyle[size];
            
            int i = 0;
            for (Map.Entry<Long, PointData> entry : points.entrySet()) {
                positions[i] = entry.getKey();
                PointData data = entry.getValue();
                fills[i] = data.fill;
                origins[i] = data.originId;
                styles[i] = data.debugStyle;
                i++;
            }
            
            return new CompositeShape(positions, fills, origins, styles);
        }
        
        private record PointData(@Nullable BlockFillTypeV2 fill, @Nullable String originId,
                                 @Nonnull DebugStyle debugStyle) {
        }
    }
    
    @Override
    public String toString() {
        return "CompositeShape{points=" + localPositions.length + "}";
    }
}
