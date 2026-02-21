package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parametric shape compositor - CAD-style timeline manager for building complex block regions.
 * Separates geometry operations (shapes, boolean ops) from fill types (block materials).
 * Allows editing the timeline (change fills, enable/disable, reorder) with reproducible results.
 */
public class ShapeCompositor {
    
    private static final Logger LOGGER = Logger.getLogger("ShapeCompositor");
    
    private Vector3i anchor;
    private double yawRotation = 0.0; // Player's yaw rotation in radians
    private final Map<String, ShapeOperation> operations;
    private final List<String> operationOrder;
    
    public ShapeCompositor(@Nonnull Vector3i anchor) {
        this.anchor = anchor;
        this.operations = new LinkedHashMap<>();
        this.operationOrder = new ArrayList<>();
    }
    
    /**
     * Update the anchor position for shape evaluation.
     * This should be called before compose() if the anchor has changed.
     */
    public void setAnchor(@Nonnull Vector3i newAnchor) {
        this.anchor = newAnchor;
    }
    
    @Nonnull
    public Vector3i getAnchor() {
        return anchor;
    }
    
    /**
     * Set the rotation to apply to all shapes (typically player's yaw).
     * This allows shapes to rotate with the player's view direction.
     * 
     * @param yawRadians Yaw rotation in radians (0 = facing +Z, π/2 = facing +X)
     */
    public void setRotation(double yawRadians) {
        this.yawRotation = yawRadians;
    }
    
    /**
     * Get the current rotation in radians.
     */
    public double getRotation() {
        return yawRotation;
    }
    
    /**
     * Add a new operation to the timeline.
     * Returns a builder for fluent API chaining.
     */
    @Nonnull
    public OperationBuilder addOperation(
            @Nonnull String id,
            @Nullable Shape shape,
            @Nonnull OperationType type,
            @Nullable BlockFillType fillType) {
        
        if (operations.containsKey(id)) {
            throw new IllegalArgumentException("Operation with ID '" + id + "' already exists");
        }
        
        return new OperationBuilder(id, shape, type, fillType);
    }
    
    /**
     * Builder for adding operations with fluent API.
     */
    public class OperationBuilder {
        private final String id;
        private final Shape shape;
        private final OperationType type;
        private final BlockFillType fillType;
        private String referenceId;
        
        private OperationBuilder(String id, Shape shape, OperationType type, BlockFillType fillType) {
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
        public ShapeOperation build() {
            ShapeOperation.Builder builder = ShapeOperation.builder(id, type)
                    .shape(shape)
                    .fillType(fillType);
            
            if (referenceId != null) {
                builder.withReference(referenceId);
            }
            
            ShapeOperation operation = builder.build();
            operations.put(id, operation);
            operationOrder.add(id);
            
            return operation;
        }
    }
    
    // Auto-build when operation builder goes out of scope (for simple cases without withReference)
    private void ensureBuilt(String id) {
        if (!operations.containsKey(id)) {
            // Operation was created but never built - this shouldn't happen in normal usage
            LOGGER.warning("Operation '" + id + "' was created but never built");
        }
    }
    
    /**
     * Update the fill type for an existing operation (parametric editing).
     */
    public void updateFill(@Nonnull String operationId, @Nullable BlockFillType newFill) {
        ShapeOperation operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Operation '" + operationId + "' not found");
        }
        operation.setFillType(newFill);
    }
    
    /**
     * Enable or disable an operation without removing it (parametric editing).
     */
    public void setEnabled(@Nonnull String operationId, boolean enabled) {
        ShapeOperation operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Operation '" + operationId + "' not found");
        }
        operation.setEnabled(enabled);
    }
    
    /**
     * Change the priority of an operation (parametric editing).
     */
    public void setPriority(@Nonnull String operationId, int priority) {
        ShapeOperation operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Operation '" + operationId + "' not found");
        }
        operation.setPriority(priority);
    }
    
    /**
     * Remove an operation from the timeline.
     */
    public void removeOperation(@Nonnull String operationId) {
        operations.remove(operationId);
        operationOrder.remove(operationId);
    }
    
    /**
     * Get the ordered timeline of operations.
     */
    @Nonnull
    public List<ShapeOperation> getTimeline() {
        List<ShapeOperation> timeline = new ArrayList<>();
        for (String id : operationOrder) {
            ShapeOperation op = operations.get(id);
            if (op != null) {
                timeline.add(op);
            }
        }
        // Sort by priority
        timeline.sort(Comparator.comparingInt(ShapeOperation::getPriority));
        return timeline;
    }
    
    /**
     * Compose the final region by executing all enabled operations in priority order.
     * This is the main "replay" method that produces reproducible results.
     */
    @Nonnull
    public ComposedRegion compose(@Nonnull ChunkStore chunkStore) {
        Map<Long, BlockSnapshot> originalBlocks = new HashMap<>();
        Map<Long, BlockFillType> blockFills = new HashMap<>();
        Map<Long, Integer> computedBlockIds = new HashMap<>();
        Set<Long> excludedPositions = new HashSet<>();
        Map<String, Set<Long>> operationRegions = new HashMap<>();
        
        // Get sorted timeline
        List<ShapeOperation> timeline = getTimeline();
        
        // Track which operation assigned each block (for FILL_REMAINING)
        Map<Long, String> blockOwners = new HashMap<>();
        
        // Execute each enabled operation in priority order
        for (ShapeOperation operation : timeline) {
            if (!operation.isEnabled()) {
                continue;
            }
            
            Set<Long> operationPositions = new HashSet<>();
            
            switch (operation.getType()) {
                case DEFINE:
                    executeDefine(operation, chunkStore, originalBlocks, blockFills, 
                            blockOwners, operationPositions, excludedPositions);
                    break;
                    
                case INTERSECT:
                    executeIntersect(operation, chunkStore, originalBlocks, blockFills,
                            blockOwners, operationPositions, operationRegions, excludedPositions);
                    break;
                    
                case SUBTRACT:
                    executeSubtract(operation, blockFills, blockOwners, operationPositions,
                            operationRegions);
                    break;
                    
                case FILL_REMAINING:
                    executeFillRemaining(operation, chunkStore, originalBlocks, blockFills,
                            blockOwners, operationPositions, operationRegions);
                    break;
                    
                case EXCLUDE:
                    executeExclude(operation, chunkStore, excludedPositions, operationPositions);
                    break;
            }
            
            operationRegions.put(operation.getId(), operationPositions);
        }
        
        // Compute final block IDs from fill types
        for (Map.Entry<Long, BlockFillType> entry : blockFills.entrySet()) {
            Long pos = entry.getKey();
            BlockFillType fillType = entry.getValue();
            BlockSnapshot original = originalBlocks.get(pos);
            
            if (original != null && fillType != null) {
                int blockId = fillType.getBlockId(original, chunkStore);
                computedBlockIds.put(pos, blockId);
            }
        }
        
        return new ComposedRegion(anchor, originalBlocks, blockFills, computedBlockIds,
                excludedPositions, operationRegions, timeline);
    }
    
    /**
     * Apply the current rotation to a shape if rotation is set.
     * Returns the original shape if no rotation is needed.
     */
    private Shape applyRotation(Shape shape) {
        if (shape == null || yawRotation == 0.0) {
            return shape;
        }
        // Apply yaw rotation around Y axis
        // Negate yaw to match Hytale's coordinate system (east/west flip)
        return new TransformedShape(shape, 0, 0, 0, -yawRotation, 0, 0);
    }
    
    private void executeDefine(ShapeOperation operation, ChunkStore chunkStore,
                               Map<Long, BlockSnapshot> originalBlocks,
                               Map<Long, BlockFillType> blockFills,
                               Map<Long, String> blockOwners,
                               Set<Long> operationPositions,
                               Set<Long> excludedPositions) {
        Shape baseShape = operation.getShape();
        if (baseShape == null) {
            LOGGER.warning("DEFINE operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        // Apply rotation to the shape
        Shape shape = applyRotation(baseShape);
        
        BlockFillType fillType = operation.getFillType();
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            
            // Skip excluded positions
            if (excludedPositions.contains(pos)) {
                return true;
            }
            
            BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
            if (snapshot != null && snapshot.blockId() != 0) {
                originalBlocks.put(pos, snapshot);
                operationPositions.add(pos);
                
                if (fillType != null) {
                    blockFills.put(pos, fillType);
                    blockOwners.put(pos, operation.getId());
                }
            }
            
            return true;
        });
    }
    
    private void executeIntersect(ShapeOperation operation, ChunkStore chunkStore,
                                  Map<Long, BlockSnapshot> originalBlocks,
                                  Map<Long, BlockFillType> blockFills,
                                  Map<Long, String> blockOwners,
                                  Set<Long> operationPositions,
                                  Map<String, Set<Long>> operationRegions,
                                  Set<Long> excludedPositions) {
        Shape baseShape = operation.getShape();
        String referenceId = operation.getReferenceId();
        
        if (baseShape == null) {
            LOGGER.warning("INTERSECT operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        // Apply rotation to the shape
        Shape shape = applyRotation(baseShape);
        
        // Get reference region
        Set<Long> referencePositions = referenceId != null ? 
                operationRegions.getOrDefault(referenceId, Collections.emptySet()) :
                Collections.emptySet();
        
        BlockFillType fillType = operation.getFillType();
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            
            // Skip excluded positions
            if (excludedPositions.contains(pos)) {
                return true;
            }
            
            // Only include if in reference region
            if (!referencePositions.contains(pos)) {
                return true;
            }
            
            BlockSnapshot snapshot = originalBlocks.get(pos);
            if (snapshot == null) {
                snapshot = TransparentBlockUtils.readBlock(chunkStore, x, y, z);
                if (snapshot != null && snapshot.blockId() != 0) {
                    originalBlocks.put(pos, snapshot);
                }
            }
            
            if (snapshot != null) {
                operationPositions.add(pos);
                
                if (fillType != null) {
                    blockFills.put(pos, fillType);
                    blockOwners.put(pos, operation.getId());
                }
            }
            
            return true;
        });
    }
    
    private void executeSubtract(ShapeOperation operation,
                                Map<Long, BlockFillType> blockFills,
                                Map<Long, String> blockOwners,
                                Set<Long> operationPositions,
                                Map<String, Set<Long>> operationRegions) {
        Shape shape = operation.getShape();
        String referenceId = operation.getReferenceId();
        
        if (shape == null) {
            LOGGER.warning("SUBTRACT operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        // Get reference region
        Set<Long> referencePositions = referenceId != null ?
                operationRegions.getOrDefault(referenceId, Collections.emptySet()) :
                Collections.emptySet();
        
        Set<Long> toRemove = new HashSet<>();
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            
            // If in reference region, mark for removal
            if (referencePositions.contains(pos)) {
                toRemove.add(pos);
            }
            
            return true;
        });
        
        // Remove from fills and owners
        for (Long pos : toRemove) {
            blockFills.remove(pos);
            blockOwners.remove(pos);
        }
        
        operationPositions.addAll(toRemove);
    }
    
    private void executeFillRemaining(ShapeOperation operation, ChunkStore chunkStore,
                                     Map<Long, BlockSnapshot> originalBlocks,
                                     Map<Long, BlockFillType> blockFills,
                                     Map<Long, String> blockOwners,
                                     Set<Long> operationPositions,
                                     Map<String, Set<Long>> operationRegions) {
        String referenceId = operation.getReferenceId();
        
        if (referenceId == null) {
            LOGGER.warning("FILL_REMAINING operation '" + operation.getId() + "' has no reference");
            return;
        }
        
        Set<Long> referencePositions = operationRegions.getOrDefault(referenceId, Collections.emptySet());
        BlockFillType fillType = operation.getFillType();
        
        if (fillType == null) {
            LOGGER.warning("FILL_REMAINING operation '" + operation.getId() + "' has no fill type");
            return;
        }
        
        // Fill all unassigned blocks in reference region
        for (Long pos : referencePositions) {
            if (!blockOwners.containsKey(pos)) {
                blockFills.put(pos, fillType);
                blockOwners.put(pos, operation.getId());
                operationPositions.add(pos);
            }
        }
    }
    
    private void executeExclude(ShapeOperation operation, ChunkStore chunkStore,
                               Set<Long> excludedPositions, Set<Long> operationPositions) {
        Shape baseShape = operation.getShape();
        
        if (baseShape == null) {
            LOGGER.warning("EXCLUDE operation '" + operation.getId() + "' has no shape");
            return;
        }
        
        // Apply rotation to the shape
        Shape shape = applyRotation(baseShape);
        
        shape.forEachBlock(anchor.x, anchor.y, anchor.z, (x, y, z) -> {
            long pos = BlockUtil.packUnchecked(x, y, z);
            excludedPositions.add(pos);
            operationPositions.add(pos);
            
            return true;
        });
    }
    
    @Override
    public String toString() {
        return "ShapeCompositor{" +
                "anchor=" + anchor +
                ", operations=" + operations.size() +
                '}';
    }
}
