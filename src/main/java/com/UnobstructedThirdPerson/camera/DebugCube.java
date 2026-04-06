package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;

import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility methods for rendering and clearing debug cubes at packed block positions.
 */
public final class DebugCube {

    private static final double CUBE_SCALE = 0.95;
    private static final float CUBE_DURATION_SECONDS = 0.2F;
    private static final float DEFAULT_OPACITY = 0.05F;
    private static final Map<World, Map<Long, CachedCube>> CACHED_DEBUG_CUBES_BY_WORLD =
            Collections.synchronizedMap(new WeakHashMap<>());

    private DebugCube() {
        // Utility class
    }

    private record CachedCube(@Nonnull Vector3f color, float opacity) {}

    private static void renderDebugCube(@Nonnull World world, long packedPos, @Nonnull Vector3f color, float opacity) {
        int x = BlockUtil.unpackX(packedPos);
        int y = BlockUtil.unpackY(packedPos);
        int z = BlockUtil.unpackZ(packedPos);

        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(x + 0.5, y + 0.5, z + 0.5);
        matrix.scale(CUBE_SCALE, CUBE_SCALE, CUBE_SCALE);
        DebugUtils.add(world, DebugShape.Cube, matrix, color, opacity, CUBE_DURATION_SECONDS, 0);
    }

    @Nonnull
    private static Map<Long, CachedCube> getOrCreateWorldCache(@Nonnull World world) {
        synchronized (CACHED_DEBUG_CUBES_BY_WORLD) {
            return CACHED_DEBUG_CUBES_BY_WORLD.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
        }
    }

    public static void addDebugCube(@Nonnull World world, long packedPos, @Nonnull Vector3f color, float opacity) {
        getOrCreateWorldCache(world).put(packedPos, new CachedCube(new Vector3f(color.x, color.y, color.z), opacity));
        renderDebugCube(world, packedPos, color, opacity);
    }

    public static void addDebugCubes(@Nonnull World world, @Nonnull Set<Long> packedPositions, @Nonnull Vector3f color) {
        addDebugCubes(world, packedPositions, color, DEFAULT_OPACITY);
    }

    public static void addDebugCubes(@Nonnull World world, @Nonnull Set<Long> packedPositions, @Nonnull Vector3f color, float opacity) {
        Map<Long, CachedCube> worldCache = getOrCreateWorldCache(world);
        for (Long packedPos : packedPositions) {
            worldCache.put(packedPos, new CachedCube(new Vector3f(color.x, color.y, color.z), opacity));
            renderDebugCube(world, packedPos, color, opacity);
        }
    }

    /**
     * Re-renders all cached debug cubes, refreshing their lifetime on the client.
     */
    public static void renderCachedDebugCubes(@Nonnull World world) {
        Map<Long, CachedCube> worldCache;
        synchronized (CACHED_DEBUG_CUBES_BY_WORLD) {
            worldCache = CACHED_DEBUG_CUBES_BY_WORLD.get(world);
        }
        if (worldCache == null || worldCache.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, CachedCube> entry : worldCache.entrySet()) {
            CachedCube cached = entry.getValue();
            renderDebugCube(world, entry.getKey(), cached.color(), cached.opacity());
        }
    }

    /**
     * Differentially updates the debug cube cache for the given world.
     * <p>
     * New positions are added to the cache and rendered immediately.
     * Stale positions are removed from the cache and expire naturally on the client
     * (within {@link #CUBE_DURATION_SECONDS}), avoiding the flicker caused by
     * clearing all debug shapes and re-adding them.
     * Retained positions are re-rendered to refresh their client-side lifetime.
     *
     * @param world       the world to update
     * @param debugStyles map of packed positions to their debug styles (only enabled styles with non-null color are used)
     */
    public static void updateDebugCubes(@Nonnull World world, @Nonnull Map<Long, DebugStyle> debugStyles) {
        Map<Long, CachedCube> worldCache = getOrCreateWorldCache(world);

        // Remove stale entries (no longer in the new set) — they expire naturally on the client
        worldCache.keySet().retainAll(debugStyles.keySet());

        // Add/update entries and render all active cubes
        for (Map.Entry<Long, DebugStyle> entry : debugStyles.entrySet()) {
            DebugStyle style = entry.getValue();
            Vector3f color = style.getColor();
            if (color == null) {
                continue;
            }
            CachedCube cached = new CachedCube(new Vector3f(color.x, color.y, color.z), style.getOpacity());
            worldCache.put(entry.getKey(), cached);
            renderDebugCube(world, entry.getKey(), cached.color(), cached.opacity());
        }
    }

    /**
     * Removes specific positions from the cache. They will expire naturally on the client.
     */
    public static void removeCachedPositions(@Nonnull World world, @Nonnull Set<Long> positions) {
        Map<Long, CachedCube> worldCache;
        synchronized (CACHED_DEBUG_CUBES_BY_WORLD) {
            worldCache = CACHED_DEBUG_CUBES_BY_WORLD.get(world);
        }
        if (worldCache == null) {
            return;
        }
        for (Long pos : positions) {
            worldCache.remove(pos);
        }
    }

    public static void clearCachedDebugCubes(@Nonnull World world) {
        synchronized (CACHED_DEBUG_CUBES_BY_WORLD) {
            Map<Long, CachedCube> worldCache = CACHED_DEBUG_CUBES_BY_WORLD.get(world);
            if (worldCache != null) {
                worldCache.clear();
            }
        }
    }

    public static void clearDebugCubes(@Nonnull World world) {
        clearCachedDebugCubes(world);
        DebugUtils.clear(world);
    }
}
