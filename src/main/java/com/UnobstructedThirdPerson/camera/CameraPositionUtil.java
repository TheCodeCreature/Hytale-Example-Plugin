package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Logger;

final class CameraPositionUtil {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int RAYCAST_DISTANCE = 30;
    private static final float CAMERA_DISTANCE = 5.0F;

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
        HeadRotation headRotationComponent = ref.getStore().getComponent(ref, HeadRotation.getComponentType());

        // Get player position (eye level)
        com.hypixel.hytale.math.vector.Vector3d playerPos = look.getPosition();

        // Get look direction normalized
        com.hypixel.hytale.math.vector.Vector3d lookDir = look.getDirection();
//        LOGGER.atInfo().log("LookDir:X{" + lookDir.x + "} Y{" + lookDir.y + "} Z{" + lookDir.z + "}");
//        LOGGER.atInfo().log("PlayerPos:X{" + playerPos.x + "} Y{" + playerPos.y + "} Z{" + playerPos.z + "}");

        // Calculate camera position: player position - (look direction * camera distance)
        //-North South+
        double cameraZ = playerPos.z - (lookDir.z * CAMERA_DISTANCE);
        //-West East+
        double cameraX = playerPos.x - (lookDir.x * CAMERA_DISTANCE);
        //-Up Down+
        double cameraY = playerPos.y + 3;

        //North
//[2026/02/18 03:37:25   INFO]       [CameraPositionUtil] LookDir:X{0.05130165661279329} Y{-0.04983345419168472} Z{-0.9974391172370609}
//[2026/02/18 03:37:25   INFO]       [CameraPositionUtil] PlayerPos:X{1699.838623046875} Y{115.43623507022858} Z{228.0076141357422}

        //West
//[2026/02/18 03:38:15   INFO]       [CameraPositionUtil] LookDir:X{-0.9997500302816036} Y{-0.011504221707582474} Z{-0.019172327507041675}
//[2026/02/18 03:38:15   INFO]       [CameraPositionUtil] PlayerPos:X{1699.838623046875} Y{115.43623507022858} Z{228.0076141357422}

        //South
//[2026/02/18 03:38:46   INFO]       [CameraPositionUtil] LookDir:X{0.04935427546906368} Y{-0.1383797526359558} Z{0.9891487088965292}
//[2026/02/18 03:38:46   INFO]       [CameraPositionUtil] PlayerPos:X{1699.838623046875} Y{115.43623507022858} Z{228.0076141357422}

        // Convert to block coordinates (floor to int)
        return new Vector3i((int) Math.floor(cameraX), (int) Math.floor(cameraY), (int) Math.floor(cameraZ));
    }

    /**
     * Gets the camera origin block and triggers the CameraTransparencyVolume update
     * if there is an active volume for this player.
     */
    @Nullable
    static Vector3i getCameraOriginBlockAndUpdate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UUID playerId) {
        Vector3i origin = getCameraOriginBlock(ref, store);
        if (origin != null) {
            CameraTransparencyVolume volume = CameraTransparencyVolume.get(playerId);
            if (volume != null) {
                Transform look = TargetUtil.getLook(ref, store);
                int minY = (int) Math.floor(look.getPosition().y) - 1;
                volume.update(origin);
            }
        }
        return origin;
    }
}
