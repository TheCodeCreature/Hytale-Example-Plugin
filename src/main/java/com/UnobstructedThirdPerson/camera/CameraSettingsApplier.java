package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CameraSettingsApplier {

    private static final Logger LOGGER = Logger.getLogger(CameraSettingsApplier.class.getName());

    private static ExtendedCameraSettings cachedSettings = null;

    @Nonnull
    public static ExtendedCameraSettings getSettings() {
        if (cachedSettings == null) {
            reloadSettings();
        }
        return cachedSettings != null ? cachedSettings : new ExtendedCameraSettings();
    }

    public static void reloadSettings() {
        ExtendedCameraSettings loaded = CameraSettingsLoader.loadFromPlayerModel();
        if (loaded != null) {
            cachedSettings = loaded;
            LOGGER.log(Level.INFO, "Reloaded camera settings from Player.json");
        } else {
            cachedSettings = new ExtendedCameraSettings();
            LOGGER.log(Level.WARNING, "Failed to load camera settings, using defaults");
        }
    }

    public static void applyToPlayer(@Nonnull PlayerRef playerRef) {
        applyToPlayer(playerRef, getSettings());
    }

    public static void applyToPlayer(@Nonnull PlayerRef playerRef, @Nonnull ExtendedCameraSettings settings) {
        try {
            ServerCameraSettings packet = settings.toPacket();
            
            // Use ThirdPerson view to avoid the Custom view bug that breaks block targeting
            // FirstPerson view ignores all settings, so we can't use that either
            ClientCameraView cameraView = ClientCameraView.ThirdPerson;
            
            // If the settings indicate first person, we still need to use ThirdPerson
            // because FirstPerson ignores ServerCameraSettings
            // The distance=0 and isFirstPerson=true will give first-person-like behavior
            if (settings.isFirstPerson()) {
                packet.distance = 0.0f;
                packet.isFirstPerson = true;
            }
            
            playerRef.getPacketHandler().writeNoCache(new SetServerCamera(cameraView, false, packet));
            
            LOGGER.log(Level.FINE, "Applied camera settings to player: " + settings);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply camera settings to player", e);
        }
    }

    public static void applyToPlayerWithCustomView(@Nonnull PlayerRef playerRef, @Nonnull ExtendedCameraSettings settings) {
        try {
            ServerCameraSettings packet = settings.toPacket();
            
            // WARNING: Custom view breaks block placement and entity interaction!
            // The target block/entity position gets stuck at the location where SetServerCamera was called.
            // Only use this if you don't need block placement or entity interaction.
            ClientCameraView cameraView = ClientCameraView.Custom;
            
            playerRef.getPacketHandler().writeNoCache(new SetServerCamera(cameraView, false, packet));
            
            LOGGER.log(Level.FINE, "Applied camera settings with Custom view to player: " + settings);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply camera settings to player", e);
        }
    }

    public static void resetCamera(@Nonnull PlayerRef playerRef) {
        try {
            playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
            LOGGER.log(Level.FINE, "Reset camera for player");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to reset camera for player", e);
        }
    }

    @Nullable
    public static ExtendedCameraSettings getCachedSettings() {
        return cachedSettings;
    }

    public static void clearCache() {
        cachedSettings = null;
    }
}
