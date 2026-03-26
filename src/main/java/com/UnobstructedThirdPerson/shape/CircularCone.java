package com.UnobstructedThirdPerson.shape;

import com.hypixel.hytale.function.predicate.TriIntObjPredicate;
import com.hypixel.hytale.function.predicate.TriIntPredicate;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Shape;

public class CircularCone implements Shape {

    private final double baseRadius;
    private final double height;

    public CircularCone(double baseRadius, double height) {
        if (baseRadius <= 0.0) {
            throw new IllegalArgumentException("baseRadius must be > 0. Given: " + baseRadius);
        }
        if (height <= 0.0) {
            throw new IllegalArgumentException("height must be > 0. Given: " + height);
        }

        this.baseRadius = baseRadius;
        this.height = height;
    }

    @Override
    public Box getBox(double x, double y, double z) {
        return new Box(
                x - baseRadius,
                y - baseRadius,
                z,
                x + baseRadius,
                y + baseRadius,
                z + height
        );
    }

    @Override
    public boolean containsPosition(double x, double y, double z) {
        if (z < 0.0 || z > height) {
            return false;
        }

        double radiusScale = 1.0 - (z / height);
        double currentRadius = baseRadius * radiusScale;
        double distanceFromCenter = Math.sqrt(x * x + y * y);
        return distanceFromCenter <= currentRadius;
    }

    @Override
    public void expand(double amount) {
        throw new UnsupportedOperationException("CircularCone does not support expand operation");
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
