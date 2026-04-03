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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;

public class ShapeCompositorV2 {
    
    private static final Logger LOGGER = Logger.getLogger("ShapeCompositorV2");
    private static final BlockFillTypeV2 CUT_FILL = new EmptyBlockFillV2();
    private static final double PITCH_PIVOT_EYE_HEIGHT = 1.8;
    
    private Vector3i anchor;
    private double yawRotation = 0.0;
    private double pitchRotation = 0.0;
    private final Map<String, ShapeOperationV2> operations;
    private final List<String> operationOrder;
    
    public ShapeCompositorV2(@Nonnull Vector3i anchor) {
        this.anchor = anchor;
        this.operations = new LinkedHashMap<>();
        this.operationOrder = new ArrayList<>();
    }
    
    public void setAnchor(@Nonnull Vector3i newAnchor) {
        this.anchor = newAnchor;
    }
    
    @Nonnull
    public Vector3i getAnchor() {
        return anchor;
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
            @Nonnull OperationTypeV2 type,
            @Nullable BlockFillTypeV2 fillType) {
        
        if (operations.containsKey(id)) {
            throw new IllegalArgumentException("Operation with ID '" + id + "' already exists");
        }
        
        return new OperationBuilder(id, shape, type, fillType);
    }
    
    public class OperationBuilder {
        private final String id;
        private final Shape shape;
        private final OperationTypeV2 type;
        private final BlockFillTypeV2 fillType;
        private String referenceId;
        private TransformFlags transformFlags;
        private DebugStyle debugStyle;
        
        private OperationBuilder(String id, Shape shape, OperationTypeV2 type, BlockFillTypeV2 fillType) {
            this.id = id;
            this.shape = shape;
            this.type = type;
            this.fillType = fillType;
        }
        
        @Nonnull
        public OperationBuilder withReference(@Nullable String referenceId) {
            this.referenceId = referenceId;
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
            
            return operation;
        }

        private void validateOperationInputs() {
            switch (type) {
                case DEFINE:
                    if (shape == null) {
                        throw new IllegalArgumentException("DEFINE operation '" + id + "' requires a shape");
                    }
                    break;

                case FILL:
                    if (shape == null) {
                        throw new IllegalArgumentException("FILL operation '" + id + "' requires a shape");
                    }
                    if (fillType == null) {
                        throw new IllegalArgumentException("FILL operation '" + id + "' requires a non-null fill type");
                    }
                    break;

                case CUT:
                    if (shape == null) {
                        throw new IllegalArgumentException("CUT operation '" + id + "' requires a shape");
                    }
                    break;

                case INTERSECT:
                    if (shape == null) {
                        throw new IllegalArgumentException("INTERSECT operation '" + id + "' requires a shape");
                    }
                    if (referenceId == null) {
                        throw new IllegalArgumentException("INTERSECT operation '" + id + "' requires a reference");
                    }
                    break;

                case SUBTRACT:
                    if (shape == null) {
                        throw new IllegalArgumentException("SUBTRACT operation '" + id + "' requires a shape");
                    }
                    if (referenceId == null) {
                        throw new IllegalArgumentException("SUBTRACT operation '" + id + "' requires a reference");
                    }
                    break;

                case FILL_REMAINING:
                    if (referenceId == null) {
                        throw new IllegalArgumentException("FILL_REMAINING operation '" + id + "' requires a reference");
                    }
                    if (fillType == null) {
                        throw new IllegalArgumentException("FILL_REMAINING operation '" + id + "' requires a non-null fill type");
                    }
                    break;

                case EXCLUDE:
                    if (shape == null) {
                        throw new IllegalArgumentException("EXCLUDE operation '" + id + "' requires a shape");
                    }
                    break;
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
        Map<Long, BlockSnapshot> originalBlocks = new HashMap<>();
        Map<Long, BlockFillTypeV2> blockFills = new HashMap<>();
        Map<Long, Integer> computedBlockIds = new HashMap<>();
        Map<Long, DebugStyle> debugStyles = new HashMap<>();
        Set<Long> excludedPositions = new HashSet<>();
        Map<String, Set<Long>> operationRegions = new HashMap<>();
        
        List<ShapeOperationV2> timeline = getTimeline();
        Map<Long, String> blockOwners = new HashMap<>();
        
        for (ShapeOperationV2 operation : timeline) {
            if (!operation.isEnabled()) {
                continue;
            }
            
            Set<Long> operationPositions = new HashSet<>();
            
            switch (operation.getType()) {
                case DEFINE:
                    executeDefine(operation, chunkStore, originalBlocks, blockFills, 
                            blockOwners, operationPositions, excludedPositions, debugStyles);
                    break;

                case FILL:
                    executeFill(operation, chunkStore, originalBlocks, blockFills,
                            blockOwners, operationPositions, excludedPositions, debugStyles);
                    break;

                case CUT:
                    executeCut(operation, chunkStore, originalBlocks, blockFills,
                            blockOwners, operationPositions, excludedPositions, debugStyles);
                    break;
                    
                case INTERSECT:
                    executeIntersect(operation, chunkStore, originalBlocks, blockFills,
                            blockOwners, operationPositions, operationRegions, excludedPositions, debugStyles);
                    break;
                    
                case SUBTRACT:
                    executeSubtract(operation, blockFills, blockOwners, operationPositions,
                            operationRegions, debugStyles);
                    break;
                    
                case FILL_REMAINING:
                    executeFillRemaining(operation, chunkStore, originalBlocks, blockFills,
                            blockOwners, operationPositions, operationRegions, debugStyles);
                    break;
                    
                case EXCLUDE:
                    executeExclude(operation, chunkStore, excludedPositions, operationPositions);
                    break;
            }
            
            operationRegions.put(operation.getId(), operationPositions);
        }

        originalBlocks.keySet().retainAll(blockFills.keySet());
        
        for (Map.Entry<Long, BlockFillTypeV2> entry : blockFills.entrySet()) {
            Long pos = entry.getKey();
            BlockFillTypeV2 fillType = entry.getValue();
            BlockSnapshot original = originalBlocks.get(pos);
            
            if (original != null && fillType != null) {
                int blockId = fillType.getBlockId(original, chunkStore);
                computedBlockIds.put(pos, blockId);
            }
        }
        
        return new ComposedRegionV2(anchor, originalBlocks, blockFills, computedBlockIds,
                debugStyles, excludedPositions, operationRegions, timeline);
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
            transformed = new TransformedShape(transformed, 0.0, -PITCH_PIVOT_EYE_HEIGHT, 0.0);
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
    
    private void executeDefine(ShapeOperationV2 operation, ChunkStore chunkStore,
                               Map<Long, BlockSnapshot> originalBlocks,
                               Map<Long, BlockFillTypeV2> blockFills,
                               Map<Long, String> blockOwners,
                               Set<Long> operationPositions,
                               Set<Long> excludedPositions,
                               Map<Long, DebugStyle> debugStyles) {
        Shape baseShape = operation.getShape();
        if (baseShape == null) {
            LOGGER.warning("DEFINE operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        Shape shape = applyTransformations(baseShape, operation.getTransformFlags());
        BlockFillTypeV2 fillType = operation.getFillType();
        DebugStyle debugStyle = resolveDebugStyle(operation, fillType);
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            
            if (excludedPositions.contains(pos)) {
                return true;
            }
            
            BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
            if (snapshot != null && snapshot.blockId() != 0) {
                operationPositions.add(pos);
                
                if (fillType != null) {
                    originalBlocks.put(pos, snapshot);
                    blockFills.put(pos, fillType);
                    blockOwners.put(pos, operation.getId());
                    debugStyles.put(pos, debugStyle);
                }
            }
            
            return true;
        });
    }

    private void executeFill(ShapeOperationV2 operation, ChunkStore chunkStore,
                             Map<Long, BlockSnapshot> originalBlocks,
                             Map<Long, BlockFillTypeV2> blockFills,
                             Map<Long, String> blockOwners,
                             Set<Long> operationPositions,
                             Set<Long> excludedPositions,
                             Map<Long, DebugStyle> debugStyles) {
        BlockFillTypeV2 fillType = operation.getFillType();
        if (fillType == null) {
            LOGGER.warning("FILL operation '" + operation.getId() + "' has no fill type");
            return;
        }

        executeFilledShape(operation, fillType, chunkStore, originalBlocks, blockFills,
                blockOwners, operationPositions, excludedPositions, debugStyles);
    }

    private void executeCut(ShapeOperationV2 operation, ChunkStore chunkStore,
                            Map<Long, BlockSnapshot> originalBlocks,
                            Map<Long, BlockFillTypeV2> blockFills,
                            Map<Long, String> blockOwners,
                            Set<Long> operationPositions,
                            Set<Long> excludedPositions,
                            Map<Long, DebugStyle> debugStyles) {
        executeFilledShape(operation, CUT_FILL, chunkStore, originalBlocks, blockFills,
                blockOwners, operationPositions, excludedPositions, debugStyles);
    }

    private void executeFilledShape(ShapeOperationV2 operation, BlockFillTypeV2 fillType, ChunkStore chunkStore,
                                    Map<Long, BlockSnapshot> originalBlocks,
                                    Map<Long, BlockFillTypeV2> blockFills,
                                    Map<Long, String> blockOwners,
                                    Set<Long> operationPositions,
                                    Set<Long> excludedPositions,
                                    Map<Long, DebugStyle> debugStyles) {
        Shape baseShape = operation.getShape();
        if (baseShape == null) {
            LOGGER.warning("Operation '" + operation.getId() + "' has no shape");
            return;
        }

        Shape shape = applyTransformations(baseShape, operation.getTransformFlags());
        DebugStyle debugStyle = resolveDebugStyle(operation, fillType);

        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);

            if (excludedPositions.contains(pos)) {
                return true;
            }

            BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
            if (snapshot != null && snapshot.blockId() != 0) {
                originalBlocks.put(pos, snapshot);
                operationPositions.add(pos);
                blockFills.put(pos, fillType);
                blockOwners.put(pos, operation.getId());
                debugStyles.put(pos, debugStyle);
            }

            return true;
        });
    }
    
    private void executeIntersect(ShapeOperationV2 operation, ChunkStore chunkStore,
                                  Map<Long, BlockSnapshot> originalBlocks,
                                  Map<Long, BlockFillTypeV2> blockFills,
                                  Map<Long, String> blockOwners,
                                  Set<Long> operationPositions,
                                  Map<String, Set<Long>> operationRegions,
                                  Set<Long> excludedPositions,
                                  Map<Long, DebugStyle> debugStyles) {
        Shape baseShape = operation.getShape();
        String referenceId = operation.getReferenceId();
        
        if (baseShape == null) {
            LOGGER.warning("INTERSECT operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        Shape shape = applyTransformations(baseShape, operation.getTransformFlags());
        Set<Long> referencePositions = referenceId != null ? 
                operationRegions.getOrDefault(referenceId, Collections.emptySet()) :
                Collections.emptySet();
        
        BlockFillTypeV2 fillType = operation.getFillType();
        DebugStyle debugStyle = resolveDebugStyle(operation, fillType);
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            
            if (excludedPositions.contains(pos)) {
                return true;
            }
            
            if (!referencePositions.contains(pos)) {
                return true;
            }
            
            BlockSnapshot snapshot = originalBlocks.get(pos);
            if (snapshot == null || snapshot.blockId() == 0) {
                snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
            }

            if (snapshot == null || snapshot.blockId() == 0) {
                return true;
            }
            
            operationPositions.add(pos);
            
            if (fillType != null) {
                originalBlocks.put(pos, snapshot);
                blockFills.put(pos, fillType);
                blockOwners.put(pos, operation.getId());
                debugStyles.put(pos, debugStyle);
            }
            
            return true;
        });
    }
    
    private void executeSubtract(ShapeOperationV2 operation,
                                Map<Long, BlockFillTypeV2> blockFills,
                                Map<Long, String> blockOwners,
                                Set<Long> operationPositions,
                                Map<String, Set<Long>> operationRegions,
                                Map<Long, DebugStyle> debugStyles) {
        Shape baseShape = operation.getShape();
        String referenceId = operation.getReferenceId();
        
        if (baseShape == null) {
            LOGGER.warning("SUBTRACT operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        Shape shape = applyTransformations(baseShape, operation.getTransformFlags());
        Set<Long> referencePositions = referenceId != null ?
                operationRegions.getOrDefault(referenceId, Collections.emptySet()) :
                Collections.emptySet();
        
        Set<Long> toRemove = new HashSet<>();
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            
            if (referencePositions.contains(pos)) {
                toRemove.add(pos);
            }
            
            return true;
        });
        
        for (Long pos : toRemove) {
            blockFills.remove(pos);
            blockOwners.remove(pos);
            debugStyles.remove(pos);
        }
        
        operationPositions.addAll(toRemove);
    }
    
    private void executeFillRemaining(ShapeOperationV2 operation, ChunkStore chunkStore,
                                     Map<Long, BlockSnapshot> originalBlocks,
                                     Map<Long, BlockFillTypeV2> blockFills,
                                     Map<Long, String> blockOwners,
                                     Set<Long> operationPositions,
                                     Map<String, Set<Long>> operationRegions,
                                     Map<Long, DebugStyle> debugStyles) {
        String referenceId = operation.getReferenceId();
        
        if (referenceId == null) {
            LOGGER.warning("FILL_REMAINING operation '" + operation.getId() + "' has no reference");
            return;
        }
        
        Set<Long> referencePositions = operationRegions.getOrDefault(referenceId, Collections.emptySet());
        BlockFillTypeV2 fillType = operation.getFillType();
        
        if (fillType == null) {
            LOGGER.warning("FILL_REMAINING operation '" + operation.getId() + "' has no fill type");
            return;
        }
        
        DebugStyle debugStyle = resolveDebugStyle(operation, fillType);
        
        for (Long pos : referencePositions) {
            if (!blockOwners.containsKey(pos)) {
                BlockSnapshot snapshot = originalBlocks.get(pos);
                if (snapshot == null || snapshot.blockId() == 0) {
                    int x = BlockUtil.unpackX(pos);
                    int y = BlockUtil.unpackY(pos);
                    int z = BlockUtil.unpackZ(pos);
                    snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
                }

                if (snapshot == null || snapshot.blockId() == 0) {
                    continue;
                }

                originalBlocks.put(pos, snapshot);
                blockFills.put(pos, fillType);
                blockOwners.put(pos, operation.getId());
                debugStyles.put(pos, debugStyle);
                operationPositions.add(pos);
            }
        }
    }
    
    private void executeExclude(ShapeOperationV2 operation, ChunkStore chunkStore,
                               Set<Long> excludedPositions, Set<Long> operationPositions) {
        Shape baseShape = operation.getShape();
        
        if (baseShape == null) {
            LOGGER.warning("EXCLUDE operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        Shape shape = applyTransformations(baseShape, operation.getTransformFlags());
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            excludedPositions.add(pos);
            operationPositions.add(pos);
            
            return true;
        });
    }
    
    @Override
    public String toString() {
        return "ShapeCompositorV2{" +
                "anchor=" + anchor +
                ", operations=" + operations.size() +
                '}';
    }
}
