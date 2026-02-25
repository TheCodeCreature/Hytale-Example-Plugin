package com.UnobstructedThirdPerson.shape.operation;

import com.UnobstructedThirdPerson.shape.fill.BlockFillType;
import com.hypixel.hytale.math.shape.Shape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single operation in the parametric shape compositor timeline.
 * Defines geometry (shape + boolean operations) separate from fill (block type).
 * Immutable after creation except for parametric properties (fill, enabled, priority).
 */
public class ShapeOperation {
    
    private final String id;
    private final Shape shape;
    private final OperationType type;
    private final String referenceId;
    private final List<String> intersectWith;
    private final List<String> subtractFrom;
    
    // Mutable parametric properties
    private BlockFillType fillType;
    private int priority;
    private boolean enabled;
    
    private ShapeOperation(Builder builder) {
        this.id = builder.id;
        this.shape = builder.shape;
        this.type = builder.type;
        this.fillType = builder.fillType;
        this.referenceId = builder.referenceId;
        this.intersectWith = new ArrayList<>(builder.intersectWith);
        this.subtractFrom = new ArrayList<>(builder.subtractFrom);
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
    public OperationType getType() {
        return type;
    }
    
    @Nullable
    public BlockFillType getFillType() {
        return fillType;
    }
    
    @Nullable
    public String getReferenceId() {
        return referenceId;
    }
    
    @Nonnull
    public List<String> getIntersectWith() {
        return Collections.unmodifiableList(intersectWith);
    }
    
    @Nonnull
    public List<String> getSubtractFrom() {
        return Collections.unmodifiableList(subtractFrom);
    }
    
    public int getPriority() {
        return priority;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    // Parametric editing methods (package-private - called by compositor)
    public void setFillType(@Nullable BlockFillType fillType) {
        this.fillType = fillType;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    @Nonnull
    public static Builder builder(@Nonnull String id, @Nonnull OperationType type) {
        return new Builder(id, type);
    }
    
    /**
     * Builder for creating ShapeOperation instances with fluent API.
     */
    public static class Builder {
        private final String id;
        private final OperationType type;
        private Shape shape;
        private BlockFillType fillType;
        private String referenceId;
        private final List<String> intersectWith = new ArrayList<>();
        private final List<String> subtractFrom = new ArrayList<>();
        private Integer priority;
        private boolean enabled = true;
        
        private Builder(String id, OperationType type) {
            this.id = id;
            this.type = type;
        }
        
        @Nonnull
        public Builder shape(@Nullable Shape shape) {
            this.shape = shape;
            return this;
        }
        
        @Nonnull
        public Builder fillType(@Nullable BlockFillType fillType) {
            this.fillType = fillType;
            return this;
        }
        
        @Nonnull
        public Builder withReference(@Nullable String referenceId) {
            this.referenceId = referenceId;
            return this;
        }
        
        @Nonnull
        public Builder intersectWith(@Nonnull String operationId) {
            this.intersectWith.add(operationId);
            return this;
        }
        
        @Nonnull
        public Builder subtract(@Nonnull String operationId) {
            this.subtractFrom.add(operationId);
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
        public ShapeOperation build() {
            return new ShapeOperation(this);
        }
    }
    
    @Override
    public String toString() {
        return "ShapeOperation{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", fillType=" + (fillType != null ? fillType.getDescription() : "null") +
                ", priority=" + priority +
                ", enabled=" + enabled +
                '}';
    }
}
