package com.UnobstructedThirdPerson.shape.v2.visual;

import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DebugStyle {
    
    @Nonnull
    public static final DebugStyle NONE = new DebugStyle(false, null, 0.0f);
    
    @Nonnull
    public static final Vector3f DARK_GREY = new Vector3f(0.2F, 0.2F, 0.2F);
    @Nonnull
    public static final Vector3f LIGHT_GREY = new Vector3f(0.5F, 0.5F, 0.5F);
    @Nonnull
    public static final Vector3f CYAN = new Vector3f(0.137F, 0.867F, 0.882F);
    @Nonnull
    public static final Vector3f YELLOW = new Vector3f(0.898F, 0.867F, 0.12F);
    @Nonnull
    public static final Vector3f MAGENTA = new Vector3f(0.898F, 0.15f, 0.888F);
    @Nonnull
    public static final Vector3f EMPTY_BLACK = new Vector3f(0.1F, 0.1F, 0.1F);
    
    @Nonnull
    public static final DebugStyle DEFAULT_GREY = new DebugStyle(true, DARK_GREY, 0.15f);
    @Nonnull
    public static final DebugStyle LIGHT_GREY_STYLE = new DebugStyle(true, LIGHT_GREY, 0.015f);
    @Nonnull
    public static final DebugStyle CYAN_STYLE = new DebugStyle(true, CYAN, 0.15f);
    @Nonnull
    public static final DebugStyle YELLOW_STYLE = new DebugStyle(true, YELLOW, 0.15f);
    @Nonnull
    public static final DebugStyle MAGENTA_STYLE = new DebugStyle(true, MAGENTA, 0.015f);
    @Nonnull
    public static final DebugStyle EMPTY_STYLE = new DebugStyle(true, EMPTY_BLACK, 0.15f);
    
    private final boolean enabled;
    private final Vector3f color;
    private final float opacity;
    
    public DebugStyle(boolean enabled, @Nullable Vector3f color, float opacity) {
        this.enabled = enabled;
        this.color = color;
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
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
    public static Builder builder() {
        return new Builder();
    }
    
    @Nonnull
    public static DebugStyle enabled(@Nonnull Vector3f color) {
        return new DebugStyle(true, color, 0.05f);
    }
    
    @Nonnull
    public static DebugStyle enabled(@Nonnull Vector3f color, float opacity) {
        return new DebugStyle(true, color, opacity);
    }
    
    @Nonnull
    public static DebugStyle disabled() {
        return NONE;
    }
    
    public static class Builder {
        private boolean enabled = true;
        private Vector3f color = DARK_GREY;
        private float opacity = 0.05f;
        
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
        public DebugStyle build() {
            return new DebugStyle(enabled, color, opacity);
        }
    }
    
    @Override
    public String toString() {
        if (!enabled) {
            return "DebugStyle{disabled}";
        }
        return "DebugStyle{color=" + color + ", opacity=" + opacity + "}";
    }
}
