package com.UnobstructedThirdPerson.shape;

import javax.annotation.Nonnull;

/**
 * A 3D offset in local coordinate space: X = right, Y = up (vertical), Z = forward/back.
 *
 * Used by both {@link TransformedShape} (per-shape positioning) and
 * {@link com.UnobstructedThirdPerson.shape.v2.ShapeCompositorV2} (pivot point offset)
 * to ensure both interpret axes identically and apply the same rotation math.
 *
 * Rotation order: roll → pitch → yaw (matching standard Euler convention).
 */
public record SpatialOffset(double x, double y, double z) {

    public static final SpatialOffset ZERO = new SpatialOffset(0, 0, 0);

    /**
     * Forward-rotate this offset by roll → pitch → yaw.
     *
     * @return a new SpatialOffset with rotated coordinates
     */
    @Nonnull
    public SpatialOffset rotated(double yaw, double pitch, double roll) {
        double[] r = forwardRotate(x, y, z, yaw, pitch, roll);
        return new SpatialOffset(r[0], r[1], r[2]);
    }

    /**
     * Forward-rotate this offset by pitch → yaw (no roll).
     */
    @Nonnull
    public SpatialOffset rotated(double yaw, double pitch) {
        return rotated(yaw, pitch, 0);
    }

    /**
     * Forward-rotate a point by roll → pitch → yaw.
     * This is the single source of truth for the rotation order used everywhere
     * (TransformedShape, ShapeCompositorV2 compose, computeEffectiveAnchor).
     */
    public static double[] forwardRotate(double x, double y, double z,
                                         double yaw, double pitch, double roll) {
        double rx = x, ry = y, rz = z;

        // Roll (rotation around Z axis)
        if (roll != 0.0) {
            double cosR = Math.cos(roll), sinR = Math.sin(roll);
            double xr = rx * cosR + ry * sinR;
            double yr = -rx * sinR + ry * cosR;
            rx = xr;
            ry = yr;
        }

        // Pitch (rotation around X axis)
        if (pitch != 0.0) {
            double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
            double yr = ry * cosP + rz * sinP;
            double zr = -ry * sinP + rz * cosP;
            ry = yr;
            rz = zr;
        }

        // Yaw (rotation around Y axis)
        if (yaw != 0.0) {
            double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
            double xr = rx * cosY - rz * sinY;
            double zr = rx * sinY + rz * cosY;
            rx = xr;
            rz = zr;
        }

        return new double[] {rx, ry, rz};
    }

    /**
     * Inverse-rotate a point by inverse yaw → inverse pitch → inverse roll.
     * The exact reverse of {@link #forwardRotate}.
     */
    public static double[] inverseRotate(double x, double y, double z,
                                         double yaw, double pitch, double roll) {
        double rx = x, ry = y, rz = z;

        // Inverse yaw
        if (yaw != 0.0) {
            double cosY = Math.cos(-yaw), sinY = Math.sin(-yaw);
            double xr = rx * cosY - rz * sinY;
            double zr = rx * sinY + rz * cosY;
            rx = xr;
            rz = zr;
        }

        // Inverse pitch
        if (pitch != 0.0) {
            double cosP = Math.cos(-pitch), sinP = Math.sin(-pitch);
            double yr = ry * cosP + rz * sinP;
            double zr = -ry * sinP + rz * cosP;
            ry = yr;
            rz = zr;
        }

        // Inverse roll
        if (roll != 0.0) {
            double cosR = Math.cos(-roll), sinR = Math.sin(-roll);
            double xr = rx * cosR + ry * sinR;
            double yr = -rx * sinR + ry * cosR;
            rx = xr;
            ry = yr;
        }

        return new double[] {rx, ry, rz};
    }
}
