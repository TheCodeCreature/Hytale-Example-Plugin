package com.UnobstructedThirdPerson.shape.v2.operation;

import com.UnobstructedThirdPerson.shape.TransformFlags;
import com.UnobstructedThirdPerson.shape.v2.fill.BlockFillTypeV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.shape.Shape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ShapeOperationV2 {
    
    private final String id;
    private final Shape shape;
    private final OperationTypeV2 type;
    private final String referenceId;
    private final TransformFlags transformFlags;
    private final DebugStyle debugStyleOverride;
    
    private BlockFillTypeV2 fillType;
    private int priority;
    private boolean enabled;
    
    private ShapeOperationV2(Builder builder) {
        this.id = builder.id;
        this.shape = builder.shape;
        this.type = builder.type;
        this.fillType = builder.fillType;
        this.referenceId = builder.referenceId;
        this.transformFlags = builder.transformFlags != null ? builder.transformFlags : TransformFlags.ALL;
        this.debugStyleOverride = builder.debugStyleOverride;
        this.priority = builder.priority != null ? builder.priority : type.getDefaultPriority();
        this.enabled = builder.enabled;
    }
    
    @Nonnull
    public String getId() {
        return id;
    }
    
    @Nullable
    public Shape getShape() {
        return shape;
    }
    
    @Nonnull
    public OperationTypeV2 getType() {
        return type;
    }
    
    @Nullable
    public BlockFillTypeV2 getFillType() {
        return fillType;
    }
    
    @Nullable
    public String getReferenceId() {
        return referenceId;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    @Nonnull
    public TransformFlags getTransformFlags() {
        return transformFlags;
    }
    
    @Nullable
    public DebugStyle getDebugStyleOverride() {
        return debugStyleOverride;
    }
    
    public void setFillType(@Nullable BlockFillTypeV2 fillType) {
        this.fillType = fillType;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    @Nonnull
    public static Builder builder(@Nonnull String id, @Nonnull OperationTypeV2 type) {
        return new Builder(id, type);
    }
    
    public static class Builder {
        private final String id;
        private final OperationTypeV2 type;
        private Shape shape;
        private BlockFillTypeV2 fillType;
        private String referenceId;
        private TransformFlags transformFlags;
        private DebugStyle debugStyleOverride;
        private Integer priority;
        private boolean enabled = true;
        
        private Builder(String id, OperationTypeV2 type) {
            this.id = id;
            this.type = type;
        }
        
        @Nonnull
        public Builder shape(@Nullable Shape shape) {
            this.shape = shape;
            return this;
        }
        
        @Nonnull
        public Builder fillType(@Nullable BlockFillTypeV2 fillType) {
            this.fillType = fillType;
            return this;
        }
        
        @Nonnull
        public Builder withReference(@Nullable String referenceId) {
            this.referenceId = referenceId;
            return this;
        }
        
        @Nonnull
        public Builder withPriority(int priority) {
            this.priority = priority;
            return this;
        }
        
        @Nonnull
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        @Nonnull
        public Builder withTransformFlags(@Nonnull TransformFlags transformFlags) {
            this.transformFlags = transformFlags;
            return this;
        }
        
        @Nonnull
        public Builder withDebugStyle(@Nonnull DebugStyle debugStyle) {
            this.debugStyleOverride = debugStyle;
            return this;
        }
        
        @Nonnull
        public ShapeOperationV2 build() {
            return new ShapeOperationV2(this);
        }
    }
    
    @Override
    public String toString() {
        return "ShapeOperationV2{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", fillType=" + (fillType != null ? fillType.getDescription() : "null") +
                ", priority=" + priority +
                ", enabled=" + enabled +
                ", debugOverride=" + (debugStyleOverride != null) +
                '}';
    }
}
