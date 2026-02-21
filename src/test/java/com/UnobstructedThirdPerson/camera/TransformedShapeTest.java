package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.hypixel.hytale.math.shape.Shape;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransformedShapeTest {

    private record Pos(int x, int y, int z) {
    }

    private static Set<Pos> collectBlocks(Shape shape) {
        Set<Pos> positions = new HashSet<>();
        boolean completed = shape.forEachBlock(0, 0, 0, 0.0, (x, y, z) -> {
            positions.add(new Pos(x, y, z));
            return true;
        });
        assertTrue(completed, "Shape.forEachBlock should complete without early exit");
        return positions;
    }

    private static Set<Pos> collectBlocksOldRounding(Shape baseShape, int offsetX, int offsetY, int offsetZ,
                                                     double yaw, double pitch, double roll) {
        final double cosYaw = Math.cos(yaw);
        final double sinYaw = Math.sin(yaw);
        final double cosPitch = Math.cos(pitch);
        final double sinPitch = Math.sin(pitch);
        final double cosRoll = Math.cos(roll);
        final double sinRoll = Math.sin(roll);

        Set<Pos> positions = new HashSet<>();

        boolean completed = baseShape.forEachBlock(0, 0, 0, 0.0, (localX, localY, localZ) -> {
            double rotX = localX;
            double rotY = localY;
            double rotZ = localZ;

            if (roll != 0.0) {
                double xTemp = rotX * cosRoll + rotY * sinRoll;
                double yTemp = -rotX * sinRoll + rotY * cosRoll;
                rotX = xTemp;
                rotY = yTemp;
            }

            if (pitch != 0.0) {
                double yTemp = rotY * cosPitch + rotZ * sinPitch;
                double zTemp = -rotY * sinPitch + rotZ * cosPitch;
                rotY = yTemp;
                rotZ = zTemp;
            }

            if (yaw != 0.0) {
                double xTemp = rotX * cosYaw - rotZ * sinYaw;
                double zTemp = rotX * sinYaw + rotZ * cosYaw;
                rotX = xTemp;
                rotZ = zTemp;
            }

            int worldX = (int) Math.round(rotX) + offsetX;
            int worldY = (int) Math.round(rotY) + offsetY;
            int worldZ = (int) Math.round(rotZ) + offsetZ;

            positions.add(new Pos(worldX, worldY, worldZ));
            return true;
        });

        assertTrue(completed, "Base shape forEachBlock should complete without early exit");
        return positions;
    }

    @Test
    void regression_nonOrthogonalYaw_shouldIncludeBlocksMissedByRounding() {
        int radius = 6;
        double yaw = Math.toRadians(30.0);

        Shape base = new Ellipsoid(radius);
        TransformedShape transformed = new TransformedShape(base, 0, 0, 0, yaw, 0.0, 0.0);

        Set<Pos> actual = collectBlocks(transformed);
        Set<Pos> old = collectBlocksOldRounding(base, 0, 0, 0, yaw, 0.0, 0.0);

        Set<Pos> diff = new HashSet<>(actual);
        diff.removeAll(old);

        assertFalse(diff.isEmpty(), "Non-90° yaw rotation should not be representable by simple rounded rotation without losing blocks");
    }

    @Test
    void baseline_zeroYaw_matchesBaseShapeWithTranslation() {
        int offsetX = 2;
        int offsetY = 3;
        int offsetZ = -1;

        Shape base = new Box(-2.5, -1.5, -3.5, 2.5, 3.5, 1.5);
        TransformedShape transformed = new TransformedShape(base, offsetX, offsetY, offsetZ, 0.0, 0.0, 0.0);

        Set<Pos> actual = collectBlocks(transformed);

        Set<Pos> expected = new HashSet<>();
        boolean completed = base.forEachBlock(offsetX, offsetY, offsetZ, 0.0, (x, y, z) -> {
            expected.add(new Pos(x, y, z));
            return true;
        });
        assertTrue(completed, "Base shape forEachBlock should complete without early exit");

        assertEquals(expected, actual, "With zero rotation, TransformedShape should match base shape with translation");
    }
}
