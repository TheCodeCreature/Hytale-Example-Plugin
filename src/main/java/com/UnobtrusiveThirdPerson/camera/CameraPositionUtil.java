package com.UnobtrusiveThirdPerson.camera;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class CameraPositionUtil {
    private static final int RAYCAST_DISTANCE = 30;
    private static final float CAMERA_DISTANCE = 6.0F;

    private CameraPositionUtil() {
        // Utility class
    }

    /**
     * Gets the camera target block (where the player is looking).
     * Uses CameraManager if available, otherwise falls back to raycasting.
     */
    @Nullable
    static Vector3i getCameraTarget(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        CameraManager cameraManager = store.getComponent(ref, CameraManager.getComponentType());
        if (cameraManager != null) {
            Vector3i target = cameraManager.getLastTargetBlock();
            if (target != null) {
                return target;
            }
        }

        return TargetUtil.getTargetBlock(ref, RAYCAST_DISTANCE, store);
    }

    /**
     * Gets the camera origin block (behind the player in 3rd person view).
     * Calculates the position based on player position and look direction.
     */
    @Nullable
    static Vector3i getCameraOriginBlock(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Transform look = TargetUtil.getLook(ref, store);
        if (look == null) {
            return null;
        }

        // Get player position (eye level)
        com.hypixel.hytale.math.vector.Vector3d playerPos = look.getPosition();
        if (playerPos == null) {
            return null;
        }

        // Get look direction normalized
        com.hypixel.hytale.math.vector.Vector3d lookDir = look.getDirection();
        if (lookDir == null) {
            return null;
        }

        // Calculate camera position: player position - (look direction * camera distance)
        // Camera distance matches the peek camera settings (10f)
        double cameraX = playerPos.x - (lookDir.x * CAMERA_DISTANCE);
        double cameraY = playerPos.y - (lookDir.y * CAMERA_DISTANCE);
        double cameraZ = playerPos.z - (lookDir.z * CAMERA_DISTANCE);

        // Convert to block coordinates (floor to int)
        return new Vector3i((int) Math.floor(cameraX), (int) Math.floor(cameraY), (int) Math.floor(cameraZ));
    }
}
