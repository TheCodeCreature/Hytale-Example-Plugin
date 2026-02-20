package com.UnobstructedThirdPerson.camera;

/**
 * Defines geometry operations for the parametric shape compositor.
 * These operations define "what" and "where" blocks are affected,
 * separate from the fill type which defines "how" they are rendered.
 */
public enum OperationType {
    /**
     * Define a new region with this shape geometry.
     * Creates a new set of block positions.
     */
    DEFINE(10),
    
    /**
     * Boolean AND with referenced operation.
     * Only keeps blocks that exist in both this shape and the reference.
     */
    INTERSECT(30),
    
    /**
     * Boolean NOT from referenced operation.
     * Removes blocks in this shape from the referenced operation.
     */
    SUBTRACT(40),
    
    /**
     * Fill unassigned blocks in referenced operation.
     * Applies fill to blocks in reference that haven't been assigned yet.
     */
    FILL_REMAINING(50),
    
    /**
     * Mark blocks as untouchable by all operations.
     * Prevents any block modifications in this region.
     */
    EXCLUDE(100);
    
    private final int defaultPriority;
    
    OperationType(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }
    
    /**
     * Get the default priority for this operation type.
     * Higher priority operations are processed later.
     */
    public int getDefaultPriority() {
        return defaultPriority;
    }
}
