package com.UnobstructedThirdPerson.shape;

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
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double yaw;   // Rotation around Y axis (radians)
    private final double pitch; // Rotation around X axis (radians)
    private final double roll;  // Rotation around Z axis (radians)
    
    /**
     * Create a transformed shape with translation only.
     */
    public TransformedShape(@Nonnull Shape baseShape, int offsetX, int offsetY, int offsetZ) {
        this(baseShape, (double) offsetX, (double) offsetY, (double) offsetZ, 0.0, 0.0, 0.0);
    }

    /**
     * Create a transformed shape with translation only.
     */
    public TransformedShape(@Nonnull Shape baseShape, double offsetX, double offsetY, double offsetZ) {
        this(baseShape, offsetX, offsetY, offsetZ, 0.0, 0.0, 0.0);
    }

    /**
     * Create a transformed shape with translation and rotation.
     */
    public TransformedShape(@Nonnull Shape baseShape, int offsetX, int offsetY, int offsetZ,
                           double yaw, double pitch, double roll) {
        this(baseShape, (double) offsetX, (double) offsetY, (double) offsetZ, yaw, pitch, roll);
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
    public TransformedShape(@Nonnull Shape baseShape, double offsetX, double offsetY, double offsetZ,
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
        // Fast path when no rotation is applied.
        if (yaw == 0.0 && pitch == 0.0 && roll == 0.0) {
            return baseShape.getBox(x + offsetX, y + offsetY, z + offsetZ);
        }

        Box baseBox = baseShape.getBox(0, 0, 0);
        double[] xValues = {baseBox.min.x, baseBox.max.x};
        double[] yValues = {baseBox.min.y, baseBox.max.y};
        double[] zValues = {baseBox.min.z, baseBox.max.z};

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (double localX : xValues) {
            for (double localY : yValues) {
                for (double localZ : zValues) {
                    double[] rotated = applyForwardRotation(localX, localY, localZ);
                    double worldX = x + offsetX + rotated[0];
                    double worldY = y + offsetY + rotated[1];
                    double worldZ = z + offsetZ + rotated[2];

                    minX = Math.min(minX, worldX);
                    minY = Math.min(minY, worldY);
                    minZ = Math.min(minZ, worldZ);
                    maxX = Math.max(maxX, worldX);
                    maxY = Math.max(maxY, worldY);
                    maxZ = Math.max(maxZ, worldZ);
                }
            }
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Apply forward rotation to a local-space point.
     * Order matches containsPosition inverse math: roll -> pitch -> yaw.
     */
    private double[] applyForwardRotation(double x, double y, double z) {
        double rotatedX = x;
        double rotatedY = y;
        double rotatedZ = z;

        if (roll != 0.0) {
            double cosRoll = Math.cos(roll);
            double sinRoll = Math.sin(roll);
            double xTemp = rotatedX * cosRoll + rotatedY * sinRoll;
            double yTemp = -rotatedX * sinRoll + rotatedY * cosRoll;
            rotatedX = xTemp;
            rotatedY = yTemp;
        }

        if (pitch != 0.0) {
            double cosPitch = Math.cos(pitch);
            double sinPitch = Math.sin(pitch);
            double yTemp = rotatedY * cosPitch + rotatedZ * sinPitch;
            double zTemp = -rotatedY * sinPitch + rotatedZ * cosPitch;
            rotatedY = yTemp;
            rotatedZ = zTemp;
        }

        if (yaw != 0.0) {
            double cosYaw = Math.cos(yaw);
            double sinYaw = Math.sin(yaw);
            double xTemp = rotatedX * cosYaw - rotatedZ * sinYaw;
            double zTemp = rotatedX * sinYaw + rotatedZ * cosYaw;
            rotatedX = xTemp;
            rotatedZ = zTemp;
        }

        return new double[] {rotatedX, rotatedY, rotatedZ};
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
        int maxX = (int) Math.floor(worldBox.max.x + epsilon);
        int maxY = (int) Math.floor(worldBox.max.y + epsilon);
        int maxZ = (int) Math.floor(worldBox.max.z + epsilon);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!containsPosition(x - anchorX, y - anchorY, z - anchorZ)) {
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
        int maxX = (int) Math.floor(worldBox.max.x + epsilon);
        int maxY = (int) Math.floor(worldBox.max.y + epsilon);
        int maxZ = (int) Math.floor(worldBox.max.z + epsilon);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!containsPosition(x - anchorX, y - anchorY, z - anchorZ)) {
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
        private double offsetX = 0.0;
        private double offsetY = 0.0;
        private double offsetZ = 0.0;
        private double yaw = 0.0;
        private double pitch = 0.0;
        private double roll = 0.0;
        
        public Builder(@Nonnull Shape baseShape) {
            this.baseShape = baseShape;
        }
        
        public Builder translate(double x, double y, double z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public Builder translate(int x, int y, int z) {
            return translate((double) x, (double) y, (double) z);
        }

        public Builder translateX(double x) {
            this.offsetX = x;
            return this;
        }
        
        public Builder translateX(int x) {
            return translateX((double) x);
        }

        public Builder translateY(double y) {
            this.offsetY = y;
            return this;
        }
        
        public Builder translateY(int y) {
            return translateY((double) y);
        }

        public Builder translateZ(double z) {
            this.offsetZ = z;
            return this;
        }
        
        public Builder translateZ(int z) {
            return translateZ((double) z);
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
