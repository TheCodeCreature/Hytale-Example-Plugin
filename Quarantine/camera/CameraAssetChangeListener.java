package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class CameraAssetChangeListener {

    private static final Logger LOGGER = Logger.getLogger(CameraAssetChangeListener.class.getName());
    private static final String PLAYER_MODEL_ID = "Human/Player";

    public static void onModelAssetLoaded(@Nonnull LoadedAssetsEvent<String, ModelAsset, DefaultAssetMap<String, ModelAsset>> event) {
        Map<String, ModelAsset> loadedAssets = event.getLoadedAssets();
        
        if (loadedAssets.containsKey(PLAYER_MODEL_ID)) {
            LOGGER.log(Level.INFO, "Player.json model asset changed, reloading camera settings");
            
            // Clear cached settings and reload
            CameraSettingsApplier.reloadSettings();
            
            // Apply to all connected players
            applyToAllPlayers();
        }
    }

    public static void applyToAllPlayers() {
        try {
            ExtendedCameraSettings settings = CameraSettingsApplier.getSettings();
            
            // Get all connected players and apply settings
            for (PlayerRef playerRef : Universe.get().getPlayers()) {
                try {
                    CameraSettingsApplier.applyToPlayer(playerRef, settings);
                    LOGGER.log(Level.FINE, "Applied updated camera settings to player: " + playerRef.getUsername());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to apply camera settings to player: " + playerRef.getUsername(), e);
                }
            }
            
            LOGGER.log(Level.INFO, "Applied camera settings to all connected players");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply camera settings to all players", e);
        }
    }
}
