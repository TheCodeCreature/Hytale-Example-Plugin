package com.UnobstructedThirdPerson.shape.v2.operation;

import javax.annotation.Nonnull;

public enum OperationTypeV2 {
    
    DEFINE(10, PositionSource.SHAPE, VoxelAction.WRITE, true, false, false, true, false),
    FILL(20, PositionSource.SHAPE, VoxelAction.WRITE, true, false, false, true, true),
    CUT(20, PositionSource.SHAPE, VoxelAction.WRITE, true, false, false, true, false),
    INTERSECT(30, PositionSource.SHAPE, VoxelAction.WRITE, true, true, false, true, false),
    SUBTRACT(40, PositionSource.SHAPE, VoxelAction.REMOVE, false, true, false, false, false),
    FILL_REMAINING(50, PositionSource.REFERENCE, VoxelAction.WRITE, false, true, true, true, true),
    EXCLUDE(100, PositionSource.SHAPE, VoxelAction.MARK_EXCLUDED, false, false, false, false, false);
    
    private final int defaultPriority;
    private final PositionSource positionSource;
    private final VoxelAction action;
    private final boolean skipExcluded;
    private final boolean requireInReference;
    private final boolean requireUnowned;
    private final boolean requireNonAir;
    private final boolean requireFill;
    
    OperationTypeV2(int defaultPriority, PositionSource positionSource, VoxelAction action,
                    boolean skipExcluded, boolean requireInReference, boolean requireUnowned,
                    boolean requireNonAir, boolean requireFill) {
        this.defaultPriority = defaultPriority;
        this.positionSource = positionSource;
        this.action = action;
        this.skipExcluded = skipExcluded;
        this.requireInReference = requireInReference;
        this.requireUnowned = requireUnowned;
        this.requireNonAir = requireNonAir;
        this.requireFill = requireFill;
    }
    
    public int getDefaultPriority() {
        return defaultPriority;
    }
    
    public PositionSource getPositionSource() {
        return positionSource;
    }
    
    public VoxelAction getAction() {
        return action;
    }
    
    public boolean isSkipExcluded() {
        return skipExcluded;
    }
    
    public boolean isRequireInReference() {
        return requireInReference;
    }
    
    public boolean isRequireUnowned() {
        return requireUnowned;
    }
    
    public boolean isRequireNonAir() {
        return requireNonAir;
    }
    
    public boolean isRequireFill() {
        return requireFill;
    }
    
    public boolean isRequireShape() {
        return positionSource == PositionSource.SHAPE;
    }
    
    public boolean isRequireReference() {
        return requireInReference;
    }
    
    public enum PositionSource {
        SHAPE,
        REFERENCE
    }
    
    public enum VoxelAction {
        WRITE,
        REMOVE,
        MARK_EXCLUDED
    }

    @Nonnull
    public static OperationRef Fill(@Nonnull String referenceId) {
        return new OperationRef(FILL, referenceId);
    }

    @Nonnull
    public static OperationRef Cut(@Nonnull String referenceId) {
        return new OperationRef(CUT, referenceId);
    }

    @Nonnull
    public static OperationRef Intersect(@Nonnull String referenceId) {
        return new OperationRef(INTERSECT, referenceId);
    }

    @Nonnull
    public static OperationRef Subtract(@Nonnull String referenceId) {
        return new OperationRef(SUBTRACT, referenceId);
    }

    @Nonnull
    public static OperationRef FillRemaining(@Nonnull String referenceId) {
        return new OperationRef(FILL_REMAINING, referenceId);
    }

    @Nonnull
    public static OperationRef Exclude(@Nonnull String referenceId) {
        return new OperationRef(EXCLUDE, referenceId);
    }

    public static class OperationRef {
        private final OperationTypeV2 type;
        private final String referenceId;

        public OperationRef(OperationTypeV2 type, String referenceId) {
            this.type = type;
            this.referenceId = referenceId;
        }

        public OperationTypeV2 getType() {
            return type;
        }

        public String getReferenceId() {
            return referenceId;
        }
    }
}
