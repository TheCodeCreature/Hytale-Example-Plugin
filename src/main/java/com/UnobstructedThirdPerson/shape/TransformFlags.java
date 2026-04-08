package com.UnobstructedThirdPerson.shape;

import javax.annotation.Nonnull;

/**
 * Controls which transformations (anchor offset and rotations) should be applied to a shape.
 * By default, all transformations are enabled.
 */
public class TransformFlags {
    
    private final boolean applyAnchorX;
    private final boolean applyAnchorY;
    private final boolean applyAnchorZ;
    private final boolean applyYaw;
    private final boolean applyPitch;
    private final boolean applyRoll;
    
    private TransformFlags(Builder builder) {
        this.applyAnchorX = builder.applyAnchorX;
        this.applyAnchorY = builder.applyAnchorY;
        this.applyAnchorZ = builder.applyAnchorZ;
        this.applyYaw = builder.applyYaw;
        this.applyPitch = builder.applyPitch;
        this.applyRoll = builder.applyRoll;
    }
    
    /**
     * Default flags - all transformations enabled.
     */
    public static final TransformFlags ALL = builder().build();
    
    /**
     * No transformations applied - shape stays in world space.
     */
    public static final TransformFlags NONE = builder()
            .ignoreAnchor()
            .ignoreRotations()
            .build();
    
    /**
     * Only apply rotations, ignore anchor offset.
     */
    public static final TransformFlags ROTATIONS_ONLY = builder()
            .ignoreAnchor()
            .build();
    
    /**
     * Only apply anchor offset, ignore rotations.
     */
    public static final TransformFlags ANCHOR_ONLY = builder()
            .ignoreRotations()
            .build();
    
    public boolean shouldApplyAnchorX() {
        return applyAnchorX;
    }
    
    public boolean shouldApplyAnchorY() {
        return applyAnchorY;
    }
    
    public boolean shouldApplyAnchorZ() {
        return applyAnchorZ;
    }
    
    public boolean shouldApplyYaw() {
        return applyYaw;
    }
    
    public boolean shouldApplyPitch() {
        return applyPitch;
    }
    
    public boolean shouldApplyRoll() {
        return applyRoll;
    }
    
    public boolean shouldApplyAnyRotation() {
        return applyYaw || applyPitch || applyRoll;
    }
    
    public boolean shouldApplyAnyAnchor() {
        return applyAnchorX || applyAnchorY || applyAnchorZ;
    }
    
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private boolean applyAnchorX = true;
        private boolean applyAnchorY = true;
        private boolean applyAnchorZ = true;
        private boolean applyYaw = true;
        private boolean applyPitch = true;
        private boolean applyRoll = true;
        
        /**
         * Ignore all anchor offset transformations (X, Y, Z).
         */
        @Nonnull
        public Builder ignoreAnchor() {
            this.applyAnchorX = false;
            this.applyAnchorY = false;
            this.applyAnchorZ = false;
            return this;
        }
        
        /**
         * Ignore anchor X offset.
         */
        @Nonnull
        public Builder ignoreAnchorX() {
            this.applyAnchorX = false;
            return this;
        }
        
        /**
         * Ignore anchor Y offset.
         */
        @Nonnull
        public Builder ignoreAnchorY() {
            this.applyAnchorY = false;
            return this;
        }
        
        /**
         * Ignore anchor Z offset.
         */
        @Nonnull
        public Builder ignoreAnchorZ() {
            this.applyAnchorZ = false;
            return this;
        }
        
        /**
         * Ignore all rotation transformations (yaw, pitch, roll).
         */
        @Nonnull
        public Builder ignoreRotations() {
            this.applyYaw = false;
            this.applyPitch = false;
            this.applyRoll = false;
            return this;
        }
        
        /**
         * Ignore yaw rotation (side-to-side).
         */
        @Nonnull
        public Builder ignoreYaw() {
            this.applyYaw = false;
            return this;
        }
        
        /**
         * Ignore pitch rotation (up-down).
         */
        @Nonnull
        public Builder ignorePitch() {
            this.applyPitch = false;
            return this;
        }
        
        /**
         * Ignore roll rotation (barrel roll).
         */
        @Nonnull
        public Builder ignoreRoll() {
            this.applyRoll = false;
            return this;
        }
        
        @Nonnull
        public TransformFlags build() {
            return new TransformFlags(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransformFlags that)) return false;
        return applyAnchorX == that.applyAnchorX
                && applyAnchorY == that.applyAnchorY
                && applyAnchorZ == that.applyAnchorZ
                && applyYaw == that.applyYaw
                && applyPitch == that.applyPitch
                && applyRoll == that.applyRoll;
    }
    
    @Override
    public int hashCode() {
        int result = Boolean.hashCode(applyAnchorX);
        result = 31 * result + Boolean.hashCode(applyAnchorY);
        result = 31 * result + Boolean.hashCode(applyAnchorZ);
        result = 31 * result + Boolean.hashCode(applyYaw);
        result = 31 * result + Boolean.hashCode(applyPitch);
        result = 31 * result + Boolean.hashCode(applyRoll);
        return result;
    }
    
    @Override
    public String toString() {
        return "TransformFlags{" +
                "anchor=[" + (applyAnchorX ? "X" : "") + (applyAnchorY ? "Y" : "") + (applyAnchorZ ? "Z" : "") + "], " +
                "rotation=[" + (applyYaw ? "Yaw" : "") + (applyPitch ? "Pitch" : "") + (applyRoll ? "Roll" : "") + "]" +
                '}';
    }
}
