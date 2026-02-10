package com.UnobstructedThirdPerson.debug;

import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class CameraDebugManager {

    private static final Logger LOGGER = Logger.getLogger("CameraDebug");
    private static final Set<UUID> debugEnabledPlayers = new HashSet<>();

    public static void enableDebug(@Nonnull PlayerRef playerRef) {
        debugEnabledPlayers.add(playerRef.getUuid());
        LOGGER.info("[CameraDebug] Debug enabled for player: " + playerRef.getUsername());
    }

    public static void disableDebug(@Nonnull PlayerRef playerRef) {
        debugEnabledPlayers.remove(playerRef.getUuid());
        LOGGER.info("[CameraDebug] Debug disabled for player: " + playerRef.getUsername());
    }

    public static boolean isDebugEnabled(@Nonnull PlayerRef playerRef) {
        return debugEnabledPlayers.contains(playerRef.getUuid());
    }

    public static boolean isDebugEnabled(@Nonnull UUID uuid) {
        return debugEnabledPlayers.contains(uuid);
    }

    public static void logPacket(@Nonnull PlayerRef playerRef, @Nonnull SetServerCamera packet) {
        if (!isDebugEnabled(playerRef)) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[CameraDebug] Sending SetServerCamera to ").append(playerRef.getUsername()).append(":\n");
        sb.append("  ClientCameraView: ").append(packet.clientCameraView).append("\n");
        sb.append("  isLocked: ").append(packet.isLocked).append("\n");

        if (packet.cameraSettings != null) {
            ServerCameraSettings s = packet.cameraSettings;
            sb.append("  ServerCameraSettings:\n");
            sb.append("    positionLerpSpeed: ").append(s.positionLerpSpeed).append("\n");
            sb.append("    rotationLerpSpeed: ").append(s.rotationLerpSpeed).append("\n");
            sb.append("    distance: ").append(s.distance).append("\n");
            sb.append("    speedModifier: ").append(s.speedModifier).append("\n");
            sb.append("    allowPitchControls: ").append(s.allowPitchControls).append("\n");
            sb.append("    displayCursor: ").append(s.displayCursor).append("\n");
            sb.append("    displayReticle: ").append(s.displayReticle).append("\n");
            sb.append("    mouseInputTargetType: ").append(s.mouseInputTargetType).append("\n");
            sb.append("    sendMouseMotion: ").append(s.sendMouseMotion).append("\n");
            sb.append("    skipCharacterPhysics: ").append(s.skipCharacterPhysics).append("\n");
            sb.append("    isFirstPerson: ").append(s.isFirstPerson).append("\n");
            sb.append("    movementForceRotationType: ").append(s.movementForceRotationType).append("\n");
            sb.append("    movementForceRotation: ").append(s.movementForceRotation).append("\n");
            sb.append("    attachedToType: ").append(s.attachedToType).append("\n");
            sb.append("    attachedToEntityId: ").append(s.attachedToEntityId).append("\n");
            sb.append("    eyeOffset: ").append(s.eyeOffset).append("\n");
            sb.append("    positionDistanceOffsetType: ").append(s.positionDistanceOffsetType).append("\n");
            sb.append("    positionOffset: ").append(s.positionOffset).append("\n");
            sb.append("    rotationOffset: ").append(s.rotationOffset).append("\n");
            sb.append("    positionType: ").append(s.positionType).append("\n");
            sb.append("    position: ").append(s.position).append("\n");
            sb.append("    rotationType: ").append(s.rotationType).append("\n");
            sb.append("    rotation: ").append(s.rotation).append("\n");
            sb.append("    canMoveType: ").append(s.canMoveType).append("\n");
            sb.append("    applyMovementType: ").append(s.applyMovementType).append("\n");
            sb.append("    movementMultiplier: ").append(s.movementMultiplier).append("\n");
            sb.append("    applyLookType: ").append(s.applyLookType).append("\n");
            sb.append("    lookMultiplier: ").append(s.lookMultiplier).append("\n");
            sb.append("    mouseInputType: ").append(s.mouseInputType).append("\n");
            sb.append("    planeNormal: ").append(s.planeNormal).append("\n");
        } else {
            sb.append("  ServerCameraSettings: null\n");
        }

        LOGGER.info(sb.toString());
    }

    public static void log(@Nonnull String message) {
        LOGGER.info("[CameraDebug] " + message);
    }

    public static void logForPlayer(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        if (isDebugEnabled(playerRef)) {
            LOGGER.info("[CameraDebug] [" + playerRef.getUsername() + "] " + message);
        }
    }
}
