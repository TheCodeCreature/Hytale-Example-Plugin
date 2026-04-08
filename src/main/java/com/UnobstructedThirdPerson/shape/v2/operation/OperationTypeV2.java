package com.UnobstructedThirdPerson.shape.v2.operation;

import javax.annotation.Nonnull;

public enum OperationTypeV2 {
    
    DEFINE(10, PositionSource.SHAPE, VoxelAction.WRITE, true, false, false, true, false),
    UNION(15, PositionSource.REFERENCE, VoxelAction.WRITE, false, true, false, false, false),
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
    public static OperationRef Union(@Nonnull String... referenceIds) {
        return new OperationRef(UNION, referenceIds);
    }

    @Nonnull
    public static OperationRef Fill(@Nonnull String... referenceIds) {
        return new OperationRef(FILL, referenceIds);
    }

    @Nonnull
    public static OperationRef Cut(@Nonnull String... referenceIds) {
        return new OperationRef(CUT, referenceIds);
    }

    @Nonnull
    public static OperationRef Intersect(@Nonnull String... referenceIds) {
        return new OperationRef(INTERSECT, referenceIds);
    }

    @Nonnull
    public static OperationRef Subtract(@Nonnull String... referenceIds) {
        return new OperationRef(SUBTRACT, referenceIds);
    }

    @Nonnull
    public static OperationRef FillRemaining(@Nonnull String... referenceIds) {
        return new OperationRef(FILL_REMAINING, referenceIds);
    }

    @Nonnull
    public static OperationRef Exclude(@Nonnull String... referenceIds) {
        return new OperationRef(EXCLUDE, referenceIds);
    }

    public static class OperationRef {
        private final OperationTypeV2 type;
        private final String[] referenceIds;

        public OperationRef(OperationTypeV2 type, @Nonnull String... referenceIds) {
            this.type = type;
            this.referenceIds = referenceIds.clone();
        }

        public OperationTypeV2 getType() {
            return type;
        }

        @Nonnull
        public String[] getReferenceIds() {
            return referenceIds;
        }
    }
}
