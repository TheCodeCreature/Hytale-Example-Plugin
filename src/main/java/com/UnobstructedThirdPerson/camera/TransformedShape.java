package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.function.predicate.TriIntObjPredicate;
import com.hypixel.hytale.function.predicate.TriIntPredicate;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Shape;

import javax.annotation.Nonnull;

/**
 * A wrapper around a Shape that applies translation and rotation transformations.
 * This allows shapes to be positioned and oriented in 3D space.
 * 
 * Rotation is applied using Euler angles (yaw, pitch, roll) in radians.
 * Translation is applied as an offset from the anchor point.
 */
public class TransformedShape implements Shape {
    
    private final Shape baseShape;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    private final double yaw;   // Rotation around Y axis (radians)
    private final double pitch; // Rotation around X axis (radians)
    private final double roll;  // Rotation around Z axis (radians)
    
    /**
     * Create a transformed shape with translation only.
     */
    public TransformedShape(@Nonnull Shape baseShape, int offsetX, int offsetY, int offsetZ) {
        this(baseShape, offsetX, offsetY, offsetZ, 0.0, 0.0, 0.0);
    }
    
    /**
     * Create a transformed shape with translation and rotation.
     * 
     * @param baseShape The base shape to transform
     * @param offsetX Translation offset in X
     * @param offsetY Translation offset in Y
     * @param offsetZ Translation offset in Z
     * @param yaw Rotation around Y axis in radians (horizontal rotation)
     * @param pitch Rotation around X axis in radians (vertical tilt)
     * @param roll Rotation around Z axis in radians (barrel roll)
     */
    public TransformedShape(@Nonnull Shape baseShape, int offsetX, int offsetY, int offsetZ,
                           double yaw, double pitch, double roll) {
        this.baseShape = baseShape;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }
    
    @Override
    public Box getBox(double x, double y, double z) {
        // Get the base shape's bounding box and apply transformations
        Box baseBox = baseShape.getBox(0, 0, 0);
        
        // For simplicity, we'll create a conservative bounding box that encompasses
        // the rotated shape. This is not perfectly tight but works for our purposes.
        double maxExtent = Math.max(Math.max(
            Math.abs(baseBox.min.x) + Math.abs(baseBox.max.x),
            Math.abs(baseBox.min.y) + Math.abs(baseBox.max.y)),
            Math.abs(baseBox.min.z) + Math.abs(baseBox.max.z));
        
        return new Box(
            x + offsetX - maxExtent, y + offsetY - maxExtent, z + offsetZ - maxExtent,
            x + offsetX + maxExtent, y + offsetY + maxExtent, z + offsetZ + maxExtent
        );
    }
    
    @Override
    public boolean containsPosition(double x, double y, double z) {
        // Transform the world position back to local coordinates
        double localX = x - offsetX;
        double localY = y - offsetY;
        double localZ = z - offsetZ;
        
        // Apply inverse rotation
        if (yaw != 0.0 || pitch != 0.0 || roll != 0.0) {
            double cosYaw = Math.cos(-yaw);
            double sinYaw = Math.sin(-yaw);
            double cosPitch = Math.cos(-pitch);
            double sinPitch = Math.sin(-pitch);
            double cosRoll = Math.cos(-roll);
            double sinRoll = Math.sin(-roll);
            
            // Inverse yaw
            double xTemp = localX * cosYaw - localZ * sinYaw;
            double zTemp = localX * sinYaw + localZ * cosYaw;
            localX = xTemp;
            localZ = zTemp;
            
            // Inverse pitch
            double yTemp = localY * cosPitch + localZ * sinPitch;
            zTemp = -localY * sinPitch + localZ * cosPitch;
            localY = yTemp;
            localZ = zTemp;
            
            // Inverse roll
            xTemp = localX * cosRoll + localY * sinRoll;
            yTemp = -localX * sinRoll + localY * cosRoll;
            localX = xTemp;
            localY = yTemp;
        }
        
        return baseShape.containsPosition(localX, localY, localZ);
    }
    
    @Override
    public void expand(double amount) {
        baseShape.expand(amount);
    }
    
    @Override
    public boolean forEachBlock(double anchorX, double anchorY, double anchorZ, double epsilon, TriIntPredicate consumer) {
        Box worldBox = getBox(anchorX, anchorY, anchorZ);
        
        int minX = (int) Math.floor(worldBox.min.x - epsilon);
        int minY = (int) Math.floor(worldBox.min.y - epsilon);
        int minZ = (int) Math.floor(worldBox.min.z - epsilon);
        int maxX = (int) Math.ceil(worldBox.max.x + epsilon);
        int maxY = (int) Math.ceil(worldBox.max.y + epsilon);
        int maxZ = (int) Math.ceil(worldBox.max.z + epsilon);
        
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (!containsPosition(x + 0.5, y + 0.5, z + 0.5)) {
                        continue;
                    }
                    if (!consumer.test(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    @Override
    public <T> boolean forEachBlock(double anchorX, double anchorY, double anchorZ, double epsilon, T context, TriIntObjPredicate<T> consumer) {
        Box worldBox = getBox(anchorX, anchorY, anchorZ);
        
        int minX = (int) Math.floor(worldBox.min.x - epsilon);
        int minY = (int) Math.floor(worldBox.min.y - epsilon);
        int minZ = (int) Math.floor(worldBox.min.z - epsilon);
        int maxX = (int) Math.ceil(worldBox.max.x + epsilon);
        int maxY = (int) Math.ceil(worldBox.max.y + epsilon);
        int maxZ = (int) Math.ceil(worldBox.max.z + epsilon);
        
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (!containsPosition(x + 0.5, y + 0.5, z + 0.5)) {
                        continue;
                    }
                    if (!consumer.test(x, y, z, context)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Builder for creating transformed shapes with a fluent API.
     */
    public static class Builder {
        private final Shape baseShape;
        private int offsetX = 0;
        private int offsetY = 0;
        private int offsetZ = 0;
        private double yaw = 0.0;
        private double pitch = 0.0;
        private double roll = 0.0;
        
        public Builder(@Nonnull Shape baseShape) {
            this.baseShape = baseShape;
        }
        
        public Builder translate(int x, int y, int z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }
        
        public Builder translateX(int x) {
            this.offsetX = x;
            return this;
        }
        
        public Builder translateY(int y) {
            this.offsetY = y;
            return this;
        }
        
        public Builder translateZ(int z) {
            this.offsetZ = z;
            return this;
        }
        
        /**
         * Set rotation around Y axis (horizontal rotation).
         * @param radians Angle in radians
         */
        public Builder rotateYaw(double radians) {
            this.yaw = radians;
            return this;
        }
        
        /**
         * Set rotation around X axis (vertical tilt).
         * @param radians Angle in radians
         */
        public Builder rotatePitch(double radians) {
            this.pitch = radians;
            return this;
        }
        
        /**
         * Set rotation around Z axis (barrel roll).
         * @param radians Angle in radians
         */
        public Builder rotateRoll(double radians) {
            this.roll = radians;
            return this;
        }
        
        /**
         * Set rotation using degrees instead of radians.
         */
        public Builder rotateYawDegrees(double degrees) {
            this.yaw = Math.toRadians(degrees);
            return this;
        }
        
        public Builder rotatePitchDegrees(double degrees) {
            this.pitch = Math.toRadians(degrees);
            return this;
        }
        
        public Builder rotateRollDegrees(double degrees) {
            this.roll = Math.toRadians(degrees);
            return this;
        }
        
        public TransformedShape build() {
            return new TransformedShape(baseShape, offsetX, offsetY, offsetZ, yaw, pitch, roll);
        }
    }
    
    /**
     * Create a builder for this shape.
     */
    public static Builder builder(@Nonnull Shape baseShape) {
        return new Builder(baseShape);
    }
}
