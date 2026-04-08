package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.TransformFlags;
import com.UnobstructedThirdPerson.shape.TransformedShape;
import com.UnobstructedThirdPerson.shape.v1.placeholder.TransparentBlockUtils;
import com.UnobstructedThirdPerson.shape.v2.fill.BlockFillTypeV2;
import com.UnobstructedThirdPerson.shape.v2.fill.EmptyBlockFillV2;
import com.UnobstructedThirdPerson.shape.v2.operation.OperationTypeV2;
import com.UnobstructedThirdPerson.shape.v2.operation.ShapeOperationV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;

public class ShapeCompositorV2 {
    
    private static final Logger LOGGER = Logger.getLogger("ShapeCompositorV2");
    private static final BlockFillTypeV2 CUT_FILL = new EmptyBlockFillV2();
    private static final double PITCH_PIVOT_EYE_HEIGHT = 0;
    
    private Vector3d anchor = new Vector3d(0, 0, 0);
    private Vector3d offset;
    private double yawRotation = 0.0;
    private double pitchRotation = 0.0;
    private final Map<String, ShapeOperationV2> operations;
    private final List<String> operationOrder;
    private final Map<String, long[]> cachedShapePositions = new HashMap<>();
    private boolean shapeCacheDirty = true;
    
    public ShapeCompositorV2(@Nonnull Vector3d offset) {
        this.offset = offset;
        this.operations = new LinkedHashMap<>();
        this.operationOrder = new ArrayList<>();
    }
    
    public void setAnchor(@Nonnull Vector3d newAnchor) {
        this.anchor = newAnchor;
    }
    
    @Nonnull
    public Vector3d getAnchor() {
        return anchor;
    }
    
    public void setOffset(@Nonnull Vector3d offset) {
        this.offset = offset;
    }
    
    @Nonnull
    public Vector3d getOffset() {
        return offset;
    }
    
    public void setRotation(double yawRadians) {
        this.yawRotation = yawRadians;
    }
    
    public void setRotation(double yawRadians, double pitchRadians) {
        this.yawRotation = yawRadians;
        this.pitchRotation = pitchRadians;
    }
    
    public double getYawRotation() {
        return yawRotation;
    }
    
    public double getPitchRotation() {
        return pitchRotation;
    }
    
    @Nonnull
    public OperationBuilder addOperation(
            @Nonnull String id,
            @Nullable Shape shape,
            @Nonnull OperationTypeV2 type) {
        
        if (operations.containsKey(id)) {
            throw new IllegalArgumentException("Operation with ID '" + id + "' already exists");
        }
        
        return new OperationBuilder(id, shape, type);
    }

    @Nonnull
    public OperationBuilder addOperation(
            @Nonnull String id,
            @Nullable Shape shape,
            @Nonnull OperationTypeV2.OperationRef operationRef) {
        
        OperationBuilder builder = addOperation(id, shape, operationRef.getType());
        builder.referenceId = operationRef.getReferenceId();
        return builder;
    }
    
    public class OperationBuilder {
        private final String id;
        private final Shape shape;
        private final OperationTypeV2 type;
        private BlockFillTypeV2 fillType;
        private String referenceId;
        private TransformFlags transformFlags;
        private DebugStyle debugStyle;
        
        private OperationBuilder(String id, Shape shape, OperationTypeV2 type) {
            this.id = id;
            this.shape = shape;
            this.type = type;
        }

        @Nonnull
        public OperationBuilder withFill(@Nonnull BlockFillTypeV2 fillType) {
            this.fillType = fillType;
            return this;
        }
        
        @Nonnull
        public OperationBuilder withTransformFlags(@Nonnull TransformFlags transformFlags) {
            this.transformFlags = transformFlags;
            return this;
        }
        
        @Nonnull
        public OperationBuilder withDebugStyle(@Nonnull DebugStyle debugStyle) {
            this.debugStyle = debugStyle;
            return this;
        }

        @Nonnull
        public OperationBuilder withDebugColor(@Nonnull Vector3f color) {
            this.debugStyle = new DebugStyle(color);
            return this;
        }
        
        @Nonnull
        public ShapeOperationV2 build() {
            validateOperationInputs();

            ShapeOperationV2.Builder builder = ShapeOperationV2.builder(id, type)
                    .shape(shape)
                    .fillType(fillType);
            
            if (referenceId != null) {
                builder.withReference(referenceId);
            }
            
            if (transformFlags != null) {
                builder.withTransformFlags(transformFlags);
            }
            
            if (debugStyle != null) {
                builder.withDebugStyle(debugStyle);
            }
            
            ShapeOperationV2 operation = builder.build();
            operations.put(id, operation);
            operationOrder.add(id);
            shapeCacheDirty = true;
            
            return operation;
        }

        private void validateOperationInputs() {
            OperationTypeV2 t = type;
            if (t.isRequireShape() && shape == null) {
                throw new IllegalArgumentException(t + " operation '" + id + "' requires a shape");
            }
            if (t.isRequireReference() && referenceId == null) {
                throw new IllegalArgumentException(t + " operation '" + id + "' requires a reference");
            }
            if (t.isRequireFill() && fillType == null) {
                throw new IllegalArgumentException(t + " operation '" + id + "' requires a non-null fill type");
            }
        }
    }
    
    public void updateFill(@Nonnull String operationId, @Nullable BlockFillTypeV2 newFill) {
        ShapeOperationV2 operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Operation '" + operationId + "' not found");
        }
        operation.setFillType(newFill);
    }
    
    public void setEnabled(@Nonnull String operationId, boolean enabled) {
        ShapeOperationV2 operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Operation '" + operationId + "' not found");
        }
        operation.setEnabled(enabled);
    }
    
    public void setPriority(@Nonnull String operationId, int priority) {
        ShapeOperationV2 operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Operation '" + operationId + "' not found");
        }
        operation.setPriority(priority);
    }
    
    public void removeOperation(@Nonnull String operationId) {
        operations.remove(operationId);
        operationOrder.remove(operationId);
        shapeCacheDirty = true;
    }
    
    @Nonnull
    public List<ShapeOperationV2> getTimeline() {
        List<ShapeOperationV2> timeline = new ArrayList<>();
        for (String id : operationOrder) {
            ShapeOperationV2 op = operations.get(id);
            if (op != null) {
                timeline.add(op);
            }
        }
        timeline.sort(Comparator.comparingInt(ShapeOperationV2::getPriority));
        return timeline;
    }
    
    @Nonnull
    public ComposedRegionV2 compose(@Nonnull ChunkStore chunkStore) {
        Map<Long, VoxelEntry> voxelMap = new HashMap<>();
        Map<String, Set<Long>> operationRegions = new HashMap<>();
        
        // Compute effective anchor: anchor (orbit point) + shapeOffset
        // Y pivots by pitch (swings vertically with look direction)
        // X and Z are flat extensions (no further rotation)
        Vector3d effectiveAnchor = computeEffectiveAnchor(yawRotation, pitchRotation);

        ensureShapeCacheBuilt();

        List<ShapeOperationV2> timeline = getTimeline();

        //TODO: This re-creates the shape every "frame"
        //TODO: This needs to be changed to call the finished shape and transform/rotate
        for (ShapeOperationV2 operation : timeline) {
            if (!operation.isEnabled()) {
                continue;
            }
            
            Set<Long> operationPositions = executeOperation(operation, chunkStore, voxelMap, operationRegions, effectiveAnchor);
            operationRegions.put(operation.getId(), operationPositions);
        }

        Map<Long, Integer> computedBlockIds = new HashMap<>();
        for (Map.Entry<Long, VoxelEntry> entry : voxelMap.entrySet()) {
            VoxelEntry voxel = entry.getValue();
            if (voxel.hasFill() && voxel.getOriginal() != null && !voxel.isExcluded()) {
                int blockId = voxel.getFill().getBlockId(voxel.getOriginal(), chunkStore);
                computedBlockIds.put(entry.getKey(), blockId);
            }
        }
        
        return new ComposedRegionV2(effectiveAnchor, voxelMap, computedBlockIds, operationRegions, timeline);
    }

    @NonNull Vector3d getVector3d() {
        return computeEffectiveAnchor(yawRotation, pitchRotation);
    }

    @NonNull Vector3d computeEffectiveAnchor(double effectiveYaw, double effectivePitch) {
        double cosPitch = Math.cos(effectivePitch);
        double sinPitch = Math.sin(effectivePitch);
        double cosYaw = Math.cos(effectiveYaw);
        double sinYaw = Math.sin(effectiveYaw);

        // Pivot Y by pitch: sin(pitch) maps to world Y (up when looking up),
        // cos(pitch) maps to forward along the yaw direction (max when looking straight)
        double pivotY = offset.y * sinPitch;
        double pivotForward = offset.y * cosPitch;

        // X and Z are flat extensions relative to the player's facing direction
        double extX = offset.x * cosYaw - offset.z * sinYaw;
        double extZ = offset.x * sinYaw + offset.z * cosYaw;

        return new Vector3d(
                anchor.x + extX + pivotForward * (-sinYaw),
                anchor.y + pivotY + PITCH_PIVOT_EYE_HEIGHT,
                anchor.z + extZ + pivotForward * cosYaw
        );
    }

    @NonNull Vector3d computeEffectiveAnchor(@Nonnull TransformFlags flags) {
        double effectiveYaw = flags.shouldApplyYaw() ? yawRotation : 0.0;
        double effectivePitch = flags.shouldApplyPitch() ? pitchRotation : 0.0;
        return computeEffectiveAnchor(effectiveYaw, effectivePitch);
    }

    private void ensureShapeCacheBuilt() {
        if (!shapeCacheDirty) {
            return;
        }
        cachedShapePositions.clear();
        
        for (String id : operationOrder) {
            ShapeOperationV2 op = operations.get(id);
            if (op == null) continue;
            if (op.getType().getPositionSource() != OperationTypeV2.PositionSource.SHAPE) continue;
            Shape baseShape = op.getShape();
            if (baseShape == null) continue;
            
            List<Long> positions = new ArrayList<>();
            baseShape.forEachBlock(0, 0, 0, (x, y, z) -> {
                positions.add(BlockUtil.packUnchecked(x, y, z));
                return true;
            });
            
            long[] arr = new long[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                arr[i] = positions.get(i);
            }
            cachedShapePositions.put(id, arr);
        }
        shapeCacheDirty = false;
    }

    @Nonnull
    private Set<Long> executeOperation(@Nonnull ShapeOperationV2 operation,
                                       @Nonnull ChunkStore chunkStore,
                                       @Nonnull Map<Long, VoxelEntry> voxelMap,
                                       @Nonnull Map<String, Set<Long>> operationRegions,
                                       @Nonnull Vector3d effectiveAnchor) {
        OperationTypeV2 type = operation.getType();
        Set<Long> operationPositions = new HashSet<>();
        
        // Resolve fill: CUT always uses CUT_FILL, others use operation's fill
        BlockFillTypeV2 fillType = (type == OperationTypeV2.CUT) ? CUT_FILL : operation.getFillType();
        DebugStyle debugStyle = resolveDebugStyle(operation, fillType);
        
        // Resolve reference positions
        String referenceId = operation.getReferenceId();
        Set<Long> referencePositions = (referenceId != null)
                ? operationRegions.getOrDefault(referenceId, Collections.emptySet())
                : Collections.emptySet();
        
        // Compute per-operation anchor respecting transform flags
        TransformFlags flags = operation.getTransformFlags();
        Vector3d operationAnchor = computeEffectiveAnchor(flags);

        // Source positions and apply filters + actions
        if (type.getPositionSource() == OperationTypeV2.PositionSource.SHAPE) {
            long[] basePositions = cachedShapePositions.get(operation.getId());
            if (basePositions == null || basePositions.length == 0) {
                LOGGER.warning(type + " operation '" + operation.getId() + "' has no cached shape positions");
                return operationPositions;
            }
            
            double dynYaw = flags.shouldApplyYaw() ? yawRotation : 0.0;
            double dynPitch = flags.shouldApplyPitch() ? pitchRotation : 0.0;
            double cosY = Math.cos(dynYaw);
            double sinY = Math.sin(dynYaw);
            double cosP = Math.cos(dynPitch);
            double sinP = Math.sin(dynPitch);
            
            for (long basePos : basePositions) {
                double cx = BlockUtil.unpackX(basePos) + 0.5;
                double cy = BlockUtil.unpackY(basePos) + 0.5;
                double cz = BlockUtil.unpackZ(basePos) + 0.5;
                
                // Apply pitch rotation (around X axis)
                double py = cy * cosP + cz * sinP;
                double pz = -cy * sinP + cz * cosP;
                if (dynPitch != 0.0) {
                    py += PITCH_PIVOT_EYE_HEIGHT;
                }
                
                // Apply yaw rotation (around Y axis)
                double rx = cx * cosY - pz * sinY;
                double rz = cx * sinY + pz * cosY;
                
                int x = (int) Math.floor(rx + operationAnchor.x);
                int y = (int) Math.floor(py + operationAnchor.y);
                int z = (int) Math.floor(rz + operationAnchor.z);
                long pos = BlockUtil.packUnchecked(x, y, z);
                
                if (!passesFilters(pos, x, y, z, type, voxelMap, referencePositions, chunkStore)) {
                    continue;
                }
                
                applyAction(pos, x, y, z, type, operation, voxelMap, referenceId,
                        fillType, debugStyle, chunkStore, operationPositions);
            }
        } else {
            // REFERENCE source: iterate the reference region
            for (Long pos : referencePositions) {
                int x = BlockUtil.unpackX(pos);
                int y = BlockUtil.unpackY(pos);
                int z = BlockUtil.unpackZ(pos);
                
                if (!passesFilters(pos, x, y, z, type, voxelMap, referencePositions, chunkStore)) {
                    continue;
                }
                
                applyAction(pos, x, y, z, type, operation, voxelMap, referenceId,
                        fillType, debugStyle, chunkStore, operationPositions);
            }
        }
        
        return operationPositions;
    }
    
    private boolean passesFilters(long pos, int x, int y, int z,
                                  @Nonnull OperationTypeV2 type,
                                  @Nonnull Map<Long, VoxelEntry> voxelMap,
                                  @Nonnull Set<Long> referencePositions,
                                  @Nonnull ChunkStore chunkStore) {
        VoxelEntry existing = voxelMap.get(pos);
        
        if (type.isSkipExcluded() && existing != null && existing.isExcluded()) {
            return false;
        }
        
        if (type.isRequireInReference() && !referencePositions.contains(pos)) {
            return false;
        }
        
        if (type.isRequireUnowned() && existing != null && existing.getOwnerId() != null) {
            return false;
        }
        
        if (type.isRequireNonAir()) {
            BlockSnapshot snapshot = (existing != null) ? existing.getOriginal() : null;
            if (snapshot == null || snapshot.blockId() == 0) {
                snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
            }
            if (snapshot == null || snapshot.blockId() == 0) {
                return false;
            }
        }
        
        return true;
    }
    
    private void applyAction(long pos, int x, int y, int z,
                             @Nonnull OperationTypeV2 type,
                             @Nonnull ShapeOperationV2 operation,
                             @Nonnull Map<Long, VoxelEntry> voxelMap,
                             @Nullable String referenceId,
                             @Nullable BlockFillTypeV2 fillType,
                             @Nonnull DebugStyle debugStyle,
                             @Nonnull ChunkStore chunkStore,
                             @Nonnull Set<Long> operationPositions) {
        switch (type.getAction()) {
            case WRITE: {
                VoxelEntry entry = voxelMap.computeIfAbsent(pos, k -> new VoxelEntry());
                operationPositions.add(pos);
                
                // Ensure we have an original snapshot
                if (entry.getOriginal() == null) {
                    BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
                    if (snapshot != null) {
                        entry.setOriginal(snapshot);
                    }
                }
                
                if (fillType != null) {
                    entry.write(null, fillType, operation.getId(), debugStyle);
                }
                break;
            }
            case REMOVE: {
                VoxelEntry entry = voxelMap.get(pos);
                operationPositions.add(pos);
                if (entry != null) {
                    String owner = entry.getOwnerId();
                    if (owner == null || owner.equals(referenceId)) {
                        entry.clearFill();
                    }
                }
                break;
            }
            case MARK_EXCLUDED: {
                VoxelEntry entry = voxelMap.computeIfAbsent(pos, k -> new VoxelEntry());
                entry.setExcluded(true);
                operationPositions.add(pos);
                break;
            }
        }
    }
    
    private Shape applyTransformations(Shape shape, TransformFlags flags) {
        if (shape == null) {
            return shape;
        }
        
        double effectiveYaw = flags.shouldApplyYaw() ? yawRotation : 0.0;
        double effectivePitch = flags.shouldApplyPitch() ? pitchRotation : 0.0;
        double effectiveRoll = flags.shouldApplyRoll() ? 0.0 : 0.0;
        
        if (effectiveYaw == 0.0 && effectivePitch == 0.0 && effectiveRoll == 0.0) {
            return shape;
        }

        Shape transformed = shape;

        if (effectiveRoll != 0.0) {
            transformed = new TransformedShape(transformed, 0.0, 0.0, 0.0, 0.0, 0.0, effectiveRoll);
        }

        if (effectivePitch != 0.0) {
            transformed = new TransformedShape(transformed, 0.0, 0.0, 0.0, 0.0, effectivePitch, 0.0);
            transformed = new TransformedShape(transformed, 0.0, PITCH_PIVOT_EYE_HEIGHT, 0.0);
        }

        if (effectiveYaw != 0.0) {
            transformed = new TransformedShape(transformed, 0.0, 0.0, 0.0, effectiveYaw, 0.0, 0.0);
        }

        return transformed;
    }
    
    private DebugStyle resolveDebugStyle(ShapeOperationV2 operation, BlockFillTypeV2 fillType) {
        DebugStyle override = operation.getDebugStyleOverride();
        if (override != null) {
            return override;
        }
        
        if (fillType != null) {
            return fillType.getDebugStyle();
        }
        
        return DebugStyle.NONE;
    }
    
    @Override
    public String toString() {
        return "ShapeCompositorV2{" +
                "anchor=" + anchor +
                ", operations=" + operations.size() +
                '}';
    }
}
