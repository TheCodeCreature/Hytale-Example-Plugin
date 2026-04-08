package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.TransformFlags;
import com.UnobstructedThirdPerson.shape.v1.placeholder.TransparentBlockUtils;
import com.UnobstructedThirdPerson.shape.v2.composite.CompositeShape;
import com.UnobstructedThirdPerson.shape.v2.fill.BlockFillTypeV2;
import com.UnobstructedThirdPerson.shape.v2.fill.EmptyBlockFillV2;
import com.UnobstructedThirdPerson.shape.v2.operation.OperationTypeV2;
import com.UnobstructedThirdPerson.shape.v2.operation.ShapeOperationV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
    private final Map<String, CompositeShape> compositeShapes = new HashMap<>();
    private final List<ShapeOperationV2> deferredOps = new ArrayList<>();
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
        builder.referenceIds = operationRef.getReferenceIds();
        return builder;
    }
    
    public class OperationBuilder {
        private final String id;
        private final Shape shape;
        private final OperationTypeV2 type;
        private BlockFillTypeV2 fillType;
        private String[] referenceIds;
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
            
            if (referenceIds != null && referenceIds.length > 0) {
                builder.withReferences(referenceIds);
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
            if (t.isRequireReference() && (referenceIds == null || referenceIds.length == 0)) {
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
        
        Vector3d effectiveAnchor = computeEffectiveAnchor(yawRotation, pitchRotation);

        ensureCompositeShapesBuilt();

        List<ShapeOperationV2> timeline = getTimeline();
        Set<Long> excludedWorldPositions = new HashSet<>();

        // Phase 1: Place composite shapes and standalone DEFINEs into world space
        for (ShapeOperationV2 operation : timeline) {
            if (!operation.isEnabled()) continue;
            OperationTypeV2 type = operation.getType();
            
            // Skip deferred ops (FILL_REMAINING) — handled in phase 3
            if (type == OperationTypeV2.FILL_REMAINING) continue;
            
            CompositeShape composite = compositeShapes.get(operation.getId());
            if (composite == null) continue;
            
            TransformFlags flags = operation.getTransformFlags();
            Vector3d operationAnchor = computeEffectiveAnchor(flags);
            
            double dynYaw = flags.shouldApplyYaw() ? yawRotation : 0.0;
            double dynPitch = flags.shouldApplyPitch() ? pitchRotation : 0.0;
            double cosY = Math.cos(dynYaw);
            double sinY = Math.sin(dynYaw);
            double cosP = Math.cos(dynPitch);
            double sinP = Math.sin(dynPitch);
            
            long[] localPositions = composite.getLocalPositions();
            BlockFillTypeV2[] fills = composite.getFills();
            String[] originIds = composite.getOriginIds();
            DebugStyle[] debugStyles = composite.getDebugStyles();
            
            Set<Long> opPositions = new HashSet<>();
            
            for (int i = 0; i < composite.size(); i++) {
                long basePos = localPositions[i];
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
                
                if (type == OperationTypeV2.EXCLUDE) {
                    excludedWorldPositions.add(pos);
                    opPositions.add(pos);
                    continue;
                }
                
                // Non-air check for WRITE actions
                if (type.isRequireNonAir()) {
                    VoxelEntry existing = voxelMap.get(pos);
                    BlockSnapshot snapshot = (existing != null) ? existing.getOriginal() : null;
                    if (snapshot == null || snapshot.blockId() == 0) {
                        snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
                    }
                    if (snapshot == null || snapshot.blockId() == 0) {
                        continue;
                    }
                }
                
                VoxelEntry entry = voxelMap.computeIfAbsent(pos, k -> new VoxelEntry());
                opPositions.add(pos);
                
                if (entry.getOriginal() == null) {
                    BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
                    if (snapshot != null) {
                        entry.setOriginal(snapshot);
                    }
                }
                
                BlockFillTypeV2 fill = fills[i];
                if (fill != null) {
                    entry.write(null, fill, originIds[i], debugStyles[i]);
                }
                
                // Track per original operation
                String originId = originIds[i];
                if (originId != null) {
                    operationRegions.computeIfAbsent(originId, k -> new HashSet<>()).add(pos);
                }
            }
            
            operationRegions.put(operation.getId(), opPositions);
        }

        // Phase 2: Apply exclusions
        for (long excludedPos : excludedWorldPositions) {
            VoxelEntry entry = voxelMap.computeIfAbsent(excludedPos, k -> new VoxelEntry());
            entry.setExcluded(true);
        }

        // Phase 3: FILL_REMAINING post-pass (deferred ops using world positions)
        for (ShapeOperationV2 operation : deferredOps) {
            if (!operation.isEnabled()) continue;
            
            OperationTypeV2 type = operation.getType();
            BlockFillTypeV2 fillType = operation.getFillType();
            DebugStyle debugStyle = resolveDebugStyle(operation, fillType);
            
            // Collect reference positions from all referenced operations
            Set<Long> referencePositions = new HashSet<>();
            for (String refId : operation.getReferenceIds()) {
                referencePositions.addAll(operationRegions.getOrDefault(refId, Collections.emptySet()));
            }
            
            Set<Long> opPositions = new HashSet<>();
            
            for (long pos : referencePositions) {
                int x = BlockUtil.unpackX(pos);
                int y = BlockUtil.unpackY(pos);
                int z = BlockUtil.unpackZ(pos);
                
                VoxelEntry existing = voxelMap.get(pos);
                
                // requireUnowned
                if (type.isRequireUnowned() && existing != null && existing.getOwnerId() != null) {
                    continue;
                }
                // requireNonAir
                if (type.isRequireNonAir()) {
                    BlockSnapshot snapshot = (existing != null) ? existing.getOriginal() : null;
                    if (snapshot == null || snapshot.blockId() == 0) {
                        snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
                    }
                    if (snapshot == null || snapshot.blockId() == 0) {
                        continue;
                    }
                }
                
                VoxelEntry entry = voxelMap.computeIfAbsent(pos, k -> new VoxelEntry());
                opPositions.add(pos);
                
                if (entry.getOriginal() == null) {
                    BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
                    if (snapshot != null) {
                        entry.setOriginal(snapshot);
                    }
                }
                
                if (fillType != null) {
                    entry.write(null, fillType, operation.getId(), debugStyle);
                }
            }
            
            operationRegions.put(operation.getId(), opPositions);
        }

        // Phase 4: Compute block IDs
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

    private void ensureCompositeShapesBuilt() {
        if (!shapeCacheDirty) {
            return;
        }
        compositeShapes.clear();
        deferredOps.clear();
        
        // First pass: voxelize all SHAPE-sourced operations at origin
        Map<String, long[]> rawPositions = new HashMap<>();
        for (String id : operationOrder) {
            ShapeOperationV2 op = operations.get(id);
            if (op == null || !op.isEnabled()) continue;
            if (op.getType().getPositionSource() != OperationTypeV2.PositionSource.SHAPE) continue;
            if (op.getType() == OperationTypeV2.UNION) continue;
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
            rawPositions.put(id, arr);
        }
        
        // Second pass: process timeline in priority order to build composite shapes
        List<ShapeOperationV2> timeline = getTimeline();
        
        for (ShapeOperationV2 op : timeline) {
            if (!op.isEnabled()) continue;
            OperationTypeV2 type = op.getType();
            String opId = op.getId();
            
            BlockFillTypeV2 fillType = (type == OperationTypeV2.CUT) ? CUT_FILL : op.getFillType();
            DebugStyle debugStyle = resolveDebugStyle(op, fillType);
            
            switch (type) {
                case DEFINE, FILL -> {
                    long[] positions = rawPositions.get(opId);
                    if (positions == null) continue;
                    CompositeShape.Builder builder = CompositeShape.builder();
                    for (long pos : positions) {
                        builder.addPoint(pos, fillType, opId, debugStyle);
                    }
                    compositeShapes.put(opId, builder.build());
                }
                case UNION -> {
                    CompositeShape.Builder builder = CompositeShape.builder();
                    for (String refId : op.getReferenceIds()) {
                        CompositeShape ref = compositeShapes.get(refId);
                        if (ref == null) {
                            LOGGER.warning("UNION '" + opId + "' references unknown shape '" + refId + "'");
                            continue;
                        }
                        for (int i = 0; i < ref.size(); i++) {
                            // Only add if not already present (first-write wins for union)
                            if (!builder.containsPoint(ref.getPosition(i))) {
                                builder.addPoint(ref.getPosition(i), ref.getFill(i),
                                        ref.getOriginId(i), ref.getDebugStyle(i));
                            }
                        }
                    }
                    compositeShapes.put(opId, builder.build());
                }
                case CUT -> {
                    long[] positions = rawPositions.get(opId);
                    if (positions == null) continue;
                    Set<Long> cutPositions = new HashSet<>();
                    for (long pos : positions) {
                        cutPositions.add(pos);
                    }
                    for (String refId : op.getReferenceIds()) {
                        CompositeShape ref = compositeShapes.get(refId);
                        if (ref == null) continue;
                        
                        // Rebuild the reference composite with CUT applied
                        CompositeShape.Builder builder = CompositeShape.builder();
                        for (int i = 0; i < ref.size(); i++) {
                            long pos = ref.getPosition(i);
                            if (cutPositions.contains(pos)) {
                                builder.addPoint(pos, CUT_FILL, opId, debugStyle);
                            } else {
                                builder.addPoint(pos, ref.getFill(i), ref.getOriginId(i), ref.getDebugStyle(i));
                            }
                        }
                        compositeShapes.put(refId, builder.build());
                    }
                    // CUT itself also gets a composite for its own positions
                    CompositeShape.Builder cutBuilder = CompositeShape.builder();
                    for (long pos : positions) {
                        cutBuilder.addPoint(pos, CUT_FILL, opId, debugStyle);
                    }
                    compositeShapes.put(opId, cutBuilder.build());
                }
                case INTERSECT -> {
                    long[] positions = rawPositions.get(opId);
                    if (positions == null) continue;
                    Set<Long> shapePositions = new HashSet<>();
                    for (long pos : positions) {
                        shapePositions.add(pos);
                    }
                    
                    // Only keep positions that exist in both shape and all references
                    CompositeShape.Builder builder = CompositeShape.builder();
                    for (String refId : op.getReferenceIds()) {
                        CompositeShape ref = compositeShapes.get(refId);
                        if (ref == null) continue;
                        for (int i = 0; i < ref.size(); i++) {
                            long pos = ref.getPosition(i);
                            if (shapePositions.contains(pos)) {
                                builder.addPoint(pos, fillType != null ? fillType : ref.getFill(i),
                                        opId, debugStyle);
                            }
                        }
                    }
                    compositeShapes.put(opId, builder.build());
                }
                case SUBTRACT -> {
                    long[] positions = rawPositions.get(opId);
                    if (positions == null) continue;
                    Set<Long> subtractPositions = new HashSet<>();
                    for (long pos : positions) {
                        subtractPositions.add(pos);
                    }
                    
                    for (String refId : op.getReferenceIds()) {
                        CompositeShape ref = compositeShapes.get(refId);
                        if (ref == null) continue;
                        
                        // Rebuild reference without the subtracted positions
                        CompositeShape.Builder builder = CompositeShape.builder();
                        for (int i = 0; i < ref.size(); i++) {
                            long pos = ref.getPosition(i);
                            if (!subtractPositions.contains(pos)) {
                                builder.addPoint(pos, ref.getFill(i), ref.getOriginId(i), ref.getDebugStyle(i));
                            }
                        }
                        compositeShapes.put(refId, builder.build());
                    }
                    // SUBTRACT itself stores its shape positions (for operationRegions)
                    CompositeShape.Builder subBuilder = CompositeShape.builder();
                    for (long pos : positions) {
                        subBuilder.addPoint(pos, null, opId, debugStyle);
                    }
                    compositeShapes.put(opId, subBuilder.build());
                }
                case EXCLUDE -> {
                    long[] positions = rawPositions.get(opId);
                    if (positions == null) continue;
                    CompositeShape.Builder builder = CompositeShape.builder();
                    for (long pos : positions) {
                        builder.addPoint(pos, null, opId, debugStyle);
                    }
                    compositeShapes.put(opId, builder.build());
                }
                case FILL_REMAINING -> {
                    deferredOps.add(op);
                }
            }
        }
        shapeCacheDirty = false;
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
