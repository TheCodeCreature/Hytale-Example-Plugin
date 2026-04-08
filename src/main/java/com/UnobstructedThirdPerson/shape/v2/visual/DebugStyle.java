package com.UnobstructedThirdPerson.shape.v2.visual;

import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DebugStyle {
    @Nonnull
    public static final DebugStyle NONE = new DebugStyle(false, null, 0.0f, DebugVisualization.CUBE);
    @Nonnull
    public static final DebugStyle COLOR_BLACK_STYLE = new DebugStyle(true, DebugUtils.COLOR_BLACK, 0.15f, DebugVisualization.CUBE);
    @Nonnull
    public static final DebugStyle COLOR_WHITE_STYLE = new DebugStyle(true, DebugUtils.COLOR_WHITE, 0.15f, DebugVisualization.CUBE);
    @Nonnull
    public static final DebugStyle COLOR_GRAY_STYLE = new DebugStyle(true, DebugUtils.COLOR_GRAY, 0.15f, DebugVisualization.CUBE);
    @Nonnull
    public static final DebugStyle COLOR_RED_STYLE = new DebugStyle(true, DebugUtils.COLOR_RED, 0.15f, DebugVisualization.CUBE);
    @Nonnull
    public static final DebugStyle COLOR_LIME_STYLE = new DebugStyle(true, DebugUtils.COLOR_LIME, 0.15f, DebugVisualization.CUBE);
    
    private final boolean enabled;
    private final Vector3f color;
    private final float opacity;
    private final DebugVisualization visualization;
    private final DebugShape debugShape;
    
    public DebugStyle(boolean enabled, @Nullable Vector3f color, float opacity,
                      @Nonnull DebugVisualization visualization, @Nullable DebugShape debugShape) {
        this.enabled = enabled;
        this.color = color;
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        this.visualization = visualization;
        this.debugShape = debugShape;
    }

    public DebugStyle(boolean enabled, @Nullable Vector3f color, float opacity, @Nonnull DebugVisualization visualization) {
        this(enabled, color, opacity, visualization, null);
    }

    public DebugStyle(boolean enabled, @Nullable Vector3f color, float opacity) {
        this(enabled, color, opacity, DebugVisualization.CUBE);
    }

    public DebugStyle(@Nullable Vector3f color) {
        this(true, color, 0.05f, DebugVisualization.CUBE);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    @Nullable
    public Vector3f getColor() {
        return color;
    }
    
    public float getOpacity() {
        return opacity;
    }
    
    @Nonnull
    public DebugVisualization getVisualization() {
        return visualization;
    }
    
    @Nonnull
    public DebugStyle withVisualization(@Nonnull DebugVisualization visualization) {
        return new DebugStyle(this.enabled, this.color, this.opacity, visualization, this.debugShape);
    }
    
    @Nullable
    public DebugShape getDebugShape() {
        return debugShape;
    }
    
    @Nonnull
    public DebugStyle withDebugShape(@Nullable DebugShape debugShape) {
        return new DebugStyle(this.enabled, this.color, this.opacity, this.visualization, debugShape);
    }
    
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }
    
    @Nonnull
    public static DebugStyle enabled(@Nonnull Vector3f color) {
        return new DebugStyle(true, color, 0.05f, DebugVisualization.CUBE);
    }
    
    @Nonnull
    public static DebugStyle enabled(@Nonnull Vector3f color, float opacity) {
        return new DebugStyle(true, color, opacity, DebugVisualization.CUBE);
    }
    
    @Nonnull
    public static DebugStyle disabled() {
        return NONE;
    }
    
    public static class Builder {
        private boolean enabled = true;
        private Vector3f color = DebugUtils.COLOR_GRAY;
        private float opacity = 0.01f;
        private DebugVisualization visualization = DebugVisualization.CUBE;
        private DebugShape debugShape = null;
        
        @Nonnull
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        @Nonnull
        public Builder color(@Nonnull Vector3f color) {
            this.color = color;
            return this;
        }
        
        @Nonnull
        public Builder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }
        
        @Nonnull
        public Builder visualization(@Nonnull DebugVisualization visualization) {
            this.visualization = visualization;
            return this;
        }
        
        @Nonnull
        public Builder debugShape(@Nullable DebugShape debugShape) {
            this.debugShape = debugShape;
            return this;
        }
        
        @Nonnull
        public DebugStyle build() {
            return new DebugStyle(enabled, color, opacity, visualization, debugShape);
        }
    }
    
    @Override
    public String toString() {
        if (!enabled) {
            return "DebugStyle{disabled}";
        }
        return "DebugStyle{" + visualization
                + (debugShape != null ? ", shape=" + debugShape : "")
                + ", color=" + color + ", opacity=" + opacity + "}";
    }
}
