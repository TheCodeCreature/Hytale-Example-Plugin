package com.CodeCreature.util;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.iterator.BlockIterator;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Hitbox-accurate block raycast that checks actual block shapes (interaction hitboxes)
 * instead of treating every non-air block as a full 1x1x1 cube.
 *
 * Uses the same eye-position and look-direction as the engine's TargetUtil.getLook(),
 * then walks the grid with BlockIterator and validates each candidate against
 * BlockBoundingBoxes detail boxes using the interaction hitbox type (the same hitbox
 * the client uses for block outline rendering and crosshair targeting).
 */
public final class BoundingBoxRayCast {

    private static final String AIR_BLOCK_ID = "Empty";

    private BoundingBoxRayCast() {}

    @Nullable
    public static Vector3i getTargetBlock(
            @Nonnull Ref<EntityStore> ref,
            double maxDistance,
            @Nonnull ComponentAccessor<EntityStore> store) {

        Transform look = TargetUtil.getLook(ref, store);
        Vector3d pos = look.getPosition();
        Vector3d dir = look.getDirection();
        World world = store.getExternalData().getWorld();

        return getTargetBlock(world, pos.x, pos.y, pos.z, dir.x, dir.y, dir.z, maxDistance);
    }

    @Nullable
    public static Vector3i getTargetBlock(
            @Nonnull World world,
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            double maxDist) {

        // Pre-compute ray endpoints once
        Vector3d rayStart = new Vector3d(ox, oy, oz);
        Vector3d rayEnd = new Vector3d(ox + dx * maxDist, oy + dy * maxDist, oz + dz * maxDist);
        int[] result = {Integer.MIN_VALUE, 0, 0};

        BlockIterator.iterate(ox, oy, oz, dx, dy, dz, maxDist,
                (x, y, z, px, py, pz, qx, qy, qz) -> {
                    if (y < 0 || y >= 320) return true;

                    BlockType blockType = world.getBlockType(x, y, z);
                    if (blockType == null || AIR_BLOCK_ID.equals(blockType.getId())) return true;

                    // Prefer interaction hitbox; fall back to collision hitbox
                    int hitboxIndex = blockType.getInteractionHitboxTypeIndex();
                    if (hitboxIndex < 0) {
                        hitboxIndex = blockType.getHitboxTypeIndex();
                    }
                    if (hitboxIndex < 0) return true;

                    BlockBoundingBoxes hitboxes = BlockBoundingBoxes.getAssetMap().getAsset(hitboxIndex);
                    if (hitboxes == null) return true;

                    int rotIndex = world.getBlockRotationIndex(x, y, z);
                    BlockBoundingBoxes.RotatedVariantBoxes rotated = hitboxes.get(rotIndex);

                    for (Box box : rotated.getDetailBoxes()) {
                        Box offsetBox = box.clone().offset(x, y, z);
                        if (offsetBox.intersectsLine(rayStart, rayEnd)) {
                            result[0] = x;
                            result[1] = y;
                            result[2] = z;
                            return false;
                        }
                    }
                    return true;
                });

        return result[0] == Integer.MIN_VALUE ? null : new Vector3i(result[0], result[1], result[2]);
    }
}
