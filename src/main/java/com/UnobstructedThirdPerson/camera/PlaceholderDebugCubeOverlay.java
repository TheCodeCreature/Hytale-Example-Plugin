package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Utility methods for rendering and clearing debug cubes at packed block positions.
 */
public final class PlaceholderDebugCubeOverlay {

    private static final double CUBE_SCALE = 1.0;
    private static final float CUBE_DURATION_SECONDS = 1000F;

    private PlaceholderDebugCubeOverlay() {
        // Utility class
    }

    public static void addDebugCube(@Nonnull World world, long packedPos, @Nonnull Vector3f color) {
        int x = BlockUtil.unpackX(packedPos);
        int y = BlockUtil.unpackY(packedPos);
        int z = BlockUtil.unpackZ(packedPos);

        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(x + 0.5, y + 0.5, z + 0.5);
        matrix.scale(CUBE_SCALE, CUBE_SCALE, CUBE_SCALE);
        DebugUtils.add(world, DebugShape.Cube, matrix, color, CUBE_DURATION_SECONDS, false);
    }

    public static void addDebugCubes(@Nonnull World world, @Nonnull Set<Long> packedPositions, @Nonnull Vector3f color) {
        for (Long packedPos : packedPositions) {
            addDebugCube(world, packedPos, color);
        }
    }

    public static void clearDebugCubes(@Nonnull World world) {
        DebugUtils.clear(world);
    }
}
