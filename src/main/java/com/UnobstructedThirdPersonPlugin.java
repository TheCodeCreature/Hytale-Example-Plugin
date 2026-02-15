package com;

import com.UnobstructedThirdPerson.AssetEditor.CameraSchemaExtension;
import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.UnobstructedCameraCommand;
import com.UnobstructedThirdPerson.Commands.debug.DebugTargetCommand;
import com.UnobstructedThirdPerson.camera.PeekTestCommand;
import com.UnobstructedThirdPerson.camera.TransparentAreaCommand;
import com.UnobstructedThirdPerson.AssetEditor.Commands.CameraDebugCommand;
import com.UnobstructedThirdPerson.fix.BlockInteractionEventSystems;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.GenerateSchemaEvent;
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
        this.getCommandRegistry().registerCommand(new TransparentAreaCommand());
        this.getCommandRegistry().registerCommand(new DebugTargetCommand());

        // Register schema extension to add all 30 camera settings fields to AssetEditor
        this.getEventRegistry().register(GenerateSchemaEvent.class, CameraSchemaExtension::extendCameraSchema);
        
        // Register camera debug command
        this.getCommandRegistry().registerCommand(new CameraDebugCommand());
    }

    @Override
    protected void start() {
        // Apply camera settings when player spawns
        // this.getEntityStoreRegistry().registerSystem(new ApplyCameraOnSpawnSystem());
        
        // // Re-apply camera settings when assets are reloaded (e.g., Player.json changes)
        // this.getEventRegistry().register(LoadedAssetsEvent.class, ModelAsset.class, CameraAssetChangeListener::onModelAssetLoaded);
        
        // Register block event systems to cancel place/break for enabled players
        this.getEntityStoreRegistry().registerSystem(new BlockInteractionEventSystems.PlaceBlockEventSystem());
        this.getEntityStoreRegistry().registerSystem(new BlockInteractionEventSystems.BreakBlockEventSystem());
        LOGGER.atInfo().log("Registered BlockInteractionEventSystems for place/break cancellation");
    }
}
