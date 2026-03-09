package com.UnobstructedThirdPerson.shape;

import com.hypixel.hytale.function.predicate.TriIntObjPredicate;
import com.hypixel.hytale.function.predicate.TriIntPredicate;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Shape;

/**
 * A square-based pyramid with its base centered at (0, baseY, 0)
 * and apex at (0, baseY + height, 0).
 */
public class SquarePyramid implements Shape {

    private double baseX;
    private final double oddX;
    private double baseY;
    private final double oddY;
    private double height;

    public SquarePyramid(double baseX, double baseY, double height) {
        if (baseX <= 0.0) {
            throw new IllegalArgumentException("baseX must be > 0. Given: " + baseX);
        }
        if (height <= 0.0) {
            throw new IllegalArgumentException("height must be > 0. Given: " + height);
        }

        this.baseX = baseX/2;
        this.oddX = baseX % 2 == 0? 0 : 1;
        this.baseY = baseY/2;
        this.oddY = baseY % 2 == 0? 0 : 1;
        this.height = height;
    }

    @Override
    public Box getBox(double x, double y, double z) {
        return new Box(
                x - (baseX + oddX),
                y - (baseY + oddY),
                z - 0,
                x + baseX,
                y + baseY,
                z + height
        );
    }

    @Override
    public boolean containsPosition(double x, double y, double z) {
        double localY = y - baseY;
//        if (localY < 0.0 || localY > height) {
//            return false;
//        }

        double widthScale = 1.0 - (localY / height);
        double currentHalfWidth = baseX * widthScale;
        return Math.abs(x) <= currentHalfWidth && Math.abs(z) <= currentHalfWidth;
    }

    @Override
    public void expand(double amount) {
        baseX = (int)Math.max(0.001, baseX + amount);
        height = Math.max(0.001, height + amount);
        baseY -= (int)Math.max(0.001, baseY + amount);
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
                    if (!containsPosition(x + 0.5 - anchorX, y + 0.5 - anchorY, z + 0.5 - anchorZ)) {
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
                    if (!containsPosition(x + 0.5 - anchorX, y + 0.5 - anchorY, z + 0.5 - anchorZ)) {
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
