package com;

import com.UnobstructedThirdPerson.AssetEditor.CameraSchemaExtension;
import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.UnobstructedCameraCommand;
import com.UnobstructedThirdPerson.camera.ApplyCameraOnSpawnSystem;
import com.UnobstructedThirdPerson.camera.CameraAssetChangeListener;
import com.UnobstructedThirdPerson.camera.PeekTestCommand;
import com.UnobstructedThirdPerson.camera.TransparentAreaCommand;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.GenerateSchemaEvent;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jspecify.annotations.NonNull;

public class UnobstructedThirdPersonPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public UnobstructedThirdPersonPlugin(@NonNull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup(){
        LOGGER.atInfo().log("Hello from %s version %s SETUP", this.getName(), this.getManifest().getVersion().toString());
        this.getCommandRegistry().registerCommand(new UnobstructedCameraCommand());
        this.getCommandRegistry().registerCommand(new PeekTestCommand());
        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName(), this.getManifest().getVersion().toString()));
        this.getCommandRegistry().registerCommand(new TransparentAreaCommand());

        // Register schema extension to add all 30 camera settings fields to AssetEditor
        this.getEventRegistry().register(GenerateSchemaEvent.class, CameraSchemaExtension::extendCameraSchema);

        // Register system to apply camera settings when player spawns
        this.getEntityStoreRegistry().registerSystem(new ApplyCameraOnSpawnSystem());

        // Register listener to reload and reapply camera settings when Player.json is modified
        this.getEventRegistry().register(LoadedAssetsEvent.class, ModelAsset.class, CameraAssetChangeListener::onModelAssetLoaded);
    }
}
