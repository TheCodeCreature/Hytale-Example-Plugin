package com.UnobstructedThirdPerson.shape;

import com.hypixel.hytale.function.predicate.TriIntObjPredicate;
import com.hypixel.hytale.function.predicate.TriIntPredicate;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Shape;

/**
 * A pyramid with a rectangular base in the X/Y plane at Z=0,
 * tapering toward an apex along +Z.
 */
public class SquarePyramid implements Shape {

    private double halfBaseX;
    private double halfBaseY;
    private final double centerOffsetX;
    private final double centerOffsetY;
    private double height;

    public SquarePyramid(double baseWidthX, double baseWidthY, double height) {
        if (baseWidthX <= 0.0) {
            throw new IllegalArgumentException("baseWidthX must be > 0. Given: " + baseWidthX);
        }
        if (baseWidthY <= 0.0) {
            throw new IllegalArgumentException("baseWidthY must be > 0. Given: " + baseWidthY);
        }
        if (height <= 0.0) {
            throw new IllegalArgumentException("height must be > 0. Given: " + height);
        }

        this.halfBaseX = baseWidthX / 2.0;
        this.halfBaseY = baseWidthY / 2.0;
        this.centerOffsetX = isOddIntegerWidth(baseWidthX) ? 0.0 : 0.5;
        this.centerOffsetY = isOddIntegerWidth(baseWidthY) ? 0.0 : 0.5;
        this.height = height;
    }

    private static boolean isOddIntegerWidth(double width) {
        double rounded = Math.rint(width);
        return Math.abs(width - rounded) < 1.0E-9 && (((long) rounded) & 1L) == 1L;
    }

    @Override
    public Box getBox(double x, double y, double z) {
        return new Box(
                x + centerOffsetX - halfBaseX,
                y + centerOffsetY - halfBaseY,
                z,
                x + centerOffsetX + halfBaseX,
                y + centerOffsetY + halfBaseY,
                z + height
        );
    }

    @Override
    public boolean containsPosition(double x, double y, double z) {
        if (z < 0.0 || z > height) {
            return false;
        }

        double widthScale = 1.0 - (z / height);
        double currentHalfWidthX = halfBaseX * widthScale;
        double currentHalfWidthY = halfBaseY * widthScale;
        return Math.abs(x - centerOffsetX) <= currentHalfWidthX
                && Math.abs(y - centerOffsetY) <= currentHalfWidthY;
    }

    @Override
    public void expand(double amount) {
        halfBaseX = Math.max(0.001, halfBaseX + amount);
        halfBaseY = Math.max(0.001, halfBaseY + amount);
        height = Math.max(0.001, height + amount);
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
}
