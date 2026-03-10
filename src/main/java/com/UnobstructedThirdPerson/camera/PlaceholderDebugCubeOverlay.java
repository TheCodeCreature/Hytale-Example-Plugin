package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * Renders short-lived debug cubes for placeholder blocks.
 *
 * Removal is implicit: when a position is no longer refreshed, its cube expires quickly.
 */
public class PlaceholderDebugCubeOverlay {

    private static final double CUBE_SCALE = 1.0;
    private static final float CUBE_DURATION_SECONDS = 0.45F;
    private static final long REFRESH_INTERVAL_MILLIS = 150L;

    @Nonnull
    private final World world;
    private final Set<Long> activePlaceholderPositions = new HashSet<>();
    private long lastRefreshMillis = 0L;

    public PlaceholderDebugCubeOverlay(@Nonnull World world) {
        this.world = world;
    }

    public void update(@Nonnull Set<Long> placeholderPositions) {
        long now = System.currentTimeMillis();
        boolean changed = !activePlaceholderPositions.equals(placeholderPositions);
        if (!changed && (now - lastRefreshMillis) < REFRESH_INTERVAL_MILLIS) {
            return;
        }

        activePlaceholderPositions.clear();
        activePlaceholderPositions.addAll(placeholderPositions);
        lastRefreshMillis = now;

        for (Long packedPos : activePlaceholderPositions) {
            int x = BlockUtil.unpackX(packedPos);
            int y = BlockUtil.unpackY(packedPos);
            int z = BlockUtil.unpackZ(packedPos);

            Vector3d center = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
            Vector3f color = new Vector3f(0.137F, 0.867F, 0.882F);
            DebugUtils.addCube(world, center, color, CUBE_SCALE, CUBE_DURATION_SECONDS);
        }
    }

    public void shutdown() {
        activePlaceholderPositions.clear();
        lastRefreshMillis = 0L;
    }
}
