package com.UnobstructedThirdPerson.camera;

import com.UnobstructedThirdPerson.shape.TransformedShape;
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

        assertFalse(actual.isEmpty(), "TransformedShape should produce blocks");

        // collectBlocks uses anchor (0,0,0), so forEachBlock tests containsPosition(x+0.5, y+0.5, z+0.5)
        // Verify every returned block's center falls inside the shape
        for (Pos pos : actual) {
            assertTrue(
                    transformed.containsPosition(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5),
                    "Block (" + pos.x() + "," + pos.y() + "," + pos.z() + ") center should be inside the shape"
            );
        }

        // Verify symmetry: no block whose center is inside the shape should be missing
        Box worldBox = transformed.getBox(0, 0, 0);
        int minX = (int) Math.floor(worldBox.min.x);
        int minY = (int) Math.floor(worldBox.min.y);
        int minZ = (int) Math.floor(worldBox.min.z);
        int maxX = (int) Math.floor(worldBox.max.x);
        int maxY = (int) Math.floor(worldBox.max.y);
        int maxZ = (int) Math.floor(worldBox.max.z);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean inside = transformed.containsPosition(x + 0.5, y + 0.5, z + 0.5);
                    assertEquals(inside, actual.contains(new Pos(x, y, z)),
                            "Block (" + x + "," + y + "," + z + ") inclusion should match containsPosition at center");
                }
            }
        }
    }

    // --- Phase 3: Rotation math tests ---

    @Test
    void containsPosition_90degYaw_rotatesXZPlane() {
        // A box spanning x=[-1,1], y=[-1,1], z=[2,4] (offset along +Z)
        Shape base = new Box(-1, -1, 2, 1, 1, 4);
        double yaw90 = Math.PI / 2;
        TransformedShape transformed = new TransformedShape(base, 0, 0, 0, yaw90, 0.0, 0.0);

        // After 90° yaw (Y-axis), forward rotation: x'=x·cosθ-z·sinθ, z'=x·sinθ+z·cosθ
        // So +Z rotates to -X: local (0,0,3) → world (-3, 0, 0)
        assertTrue(transformed.containsPosition(-3, 0, 0),
                "After 90° yaw, point along -X should be inside (was +Z in local)");
        // Original +Z and +X should be outside
        assertFalse(transformed.containsPosition(0, 0, 3),
                "After 90° yaw, original +Z axis point should be outside");
        assertFalse(transformed.containsPosition(3, 0, 0),
                "After 90° yaw, +X point should be outside");
    }

    @Test
    void containsPosition_90degPitch_rotatesYZPlane() {
        // A box along +Z: x=[-1,1], y=[-1,1], z=[2,4]
        Shape base = new Box(-1, -1, 2, 1, 1, 4);
        double pitch90 = Math.PI / 2;
        TransformedShape transformed = new TransformedShape(base, 0, 0, 0, 0.0, pitch90, 0.0);

        // After 90° pitch (X-axis rotation), forward: y'=y·cosθ+z·sinθ, z'=-y·sinθ+z·cosθ
        // So +Z rotates to +Y: local (0,0,3) → world (0, 3, 0)
        assertTrue(transformed.containsPosition(0, 3, 0),
                "After 90° pitch, point along +Y should be inside (was +Z in local)");
        assertFalse(transformed.containsPosition(0, 0, 3),
                "After 90° pitch, original +Z point should be outside");
        assertFalse(transformed.containsPosition(0, -3, 0),
                "After 90° pitch, -Y point should be outside");
    }

    @Test
    void containsPosition_combinedYawPitch_correctTransform() {
        // A thin box along +Z only: x=[-0.5,0.5], y=[-0.5,0.5], z=[3,5]
        Shape base = new Box(-0.5, -0.5, 3, 0.5, 0.5, 5);
        double yaw45 = Math.toRadians(45);
        double pitch45 = Math.toRadians(45);
        TransformedShape transformed = new TransformedShape(base, 0, 0, 0, yaw45, pitch45, 0.0);

        // The center of the box in local space is (0, 0, 4).
        // Forward-rotate: pitch first rotates Z→-Y partially, then yaw rotates X→Z partially.
        // After roll(0)->pitch(45°)->yaw(45°) on point (0,0,4):
        //   pitch: y' = 0*cos45 + 4*sin45 = 2√2, z' = -0*sin45 + 4*cos45 = 2√2
        //   yaw:   x' = 0*cos45 - 2√2*sin45 = -2, z' = 0*sin45 + 2√2*cos45 = 2
        double sqrt2 = Math.sqrt(2);
        double expectedX = -2.0;
        double expectedY = 2 * sqrt2;
        double expectedZ = 2.0;
        assertTrue(transformed.containsPosition(expectedX, expectedY, expectedZ),
                "Combined yaw+pitch: center of rotated box should be at expected location");
    }

    @Test
    void offsetX_isConsistentWithYAndZ() {
        // TransformedShape stores offsetX as-is (same as Y and Z).
        // containsPosition subtracts offsetX: localX = x - 3
        // The base box center is at (0,0,0), so containsPosition(x,0,0) → base.contains(x-3, 0, 0)
        // base.contains(0,0,0)=true → we need x-3=0 → x=3
        Shape base = new Box(-1, -1, -1, 1, 1, 1);
        TransformedShape transformed = new TransformedShape(base, 3, 0, 0, 0.0, 0.0, 0.0);

        assertTrue(transformed.containsPosition(3, 0, 0),
                "Positive offsetX should shift shape to positive X");
        assertFalse(transformed.containsPosition(-3, 0, 0),
                "Shape should NOT be at x=-3");
    }

    @Test
    void forEachBlock_withNonZeroAnchor_shiftsOutput() {
        Shape base = new Box(-1, -1, -1, 1, 1, 1);
        TransformedShape transformed = new TransformedShape(base, 0, 0, 0, 0.0, 0.0, 0.0);

        Set<Pos> atOrigin = collectBlocks(transformed);

        Set<Pos> atAnchor = new HashSet<>();
        transformed.forEachBlock(5, 0, 0, 0.0, (x, y, z) -> {
            atAnchor.add(new Pos(x, y, z));
            return true;
        });

        // With anchor=(5,0,0), forEachBlock tests containsPosition(x+0.5-5, y+0.5, z+0.5)
        // This shifts all output blocks by +5 in X compared to anchor=(0,0,0)
        assertFalse(atAnchor.isEmpty(), "Should produce blocks with non-zero anchor");
        assertNotEquals(atOrigin, atAnchor, "Different anchors should produce different block sets");

        // Every block in atAnchor should be shifted +5 in X from atOrigin
        Set<Pos> shifted = new HashSet<>();
        for (Pos p : atOrigin) {
            shifted.add(new Pos(p.x() + 5, p.y(), p.z()));
        }
        assertEquals(shifted, atAnchor, "Anchor shift of 5 in X should offset all blocks by +5");
    }

    @Test
    void getBox_rotatedShape_expandsToEncloseBounds() {
        Shape base = new Box(-5, -1, -1, 5, 1, 1);
        TransformedShape unrotated = new TransformedShape(base, 0, 0, 0, 0.0, 0.0, 0.0);
        TransformedShape rotated = new TransformedShape(base, 0, 0, 0, Math.toRadians(45), 0.0, 0.0);

        Box unrotatedBox = unrotated.getBox(0, 0, 0);
        Box rotatedBox = rotated.getBox(0, 0, 0);

        // The unrotated box is long in X (10 units) and thin in Z (2 units).
        // After 45° yaw rotation, the AABB must expand in Z to enclose the diagonal.
        double unrotatedZSpan = unrotatedBox.max.z - unrotatedBox.min.z;
        double rotatedZSpan = rotatedBox.max.z - rotatedBox.min.z;
        assertTrue(rotatedZSpan > unrotatedZSpan,
                "45° yaw rotation of elongated box should expand Z span of AABB");
    }

    // --- Phase 5: Builder tests ---

    @Test
    void builder_translateAndRotate_matchesConstructor() {
        Shape base = new Box(-2, -2, -2, 2, 2, 2);
        double yaw = Math.toRadians(30);

        TransformedShape fromConstructor = new TransformedShape(base, 2, 3, -1, yaw, 0.0, 0.0);
        TransformedShape fromBuilder = TransformedShape.builder(base)
                .translate(2, 3, -1)
                .rotateYaw(yaw)
                .build();

        Set<Pos> constructorBlocks = collectBlocks(fromConstructor);
        Set<Pos> builderBlocks = collectBlocks(fromBuilder);

        assertFalse(constructorBlocks.isEmpty(), "Constructor shape should produce blocks");
        assertEquals(constructorBlocks, builderBlocks,
                "Builder and constructor should produce identical block sets");
    }

    @Test
    void builder_degreesConversion_matchesRadians() {
        Shape base = new Box(-3, -1, -3, 3, 1, 3);

        TransformedShape fromRadians = TransformedShape.builder(base)
                .rotateYaw(Math.PI / 2)
                .build();
        TransformedShape fromDegrees = TransformedShape.builder(base)
                .rotateYawDegrees(90)
                .build();

        Set<Pos> radiansBlocks = collectBlocks(fromRadians);
        Set<Pos> degreesBlocks = collectBlocks(fromDegrees);

        assertFalse(radiansBlocks.isEmpty(), "Radians shape should produce blocks");
        assertEquals(radiansBlocks, degreesBlocks,
                "rotateYawDegrees(90) should produce same blocks as rotateYaw(PI/2)");
    }
}
