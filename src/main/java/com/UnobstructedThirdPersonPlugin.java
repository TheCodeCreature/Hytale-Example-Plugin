package com;

import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.UnobstructedCameraCommand;
import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.Settings.CustomCameraSettings;
import com.UnobstructedThirdPerson.camera.PeekTestCommand;
import com.UnobstructedThirdPerson.camera.TransparentAreaCommand;
import com.hypixel.hytale.builtin.asseteditor.AssetEditorPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jspecify.annotations.NonNull;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.builtin.asseteditor.AssetEditorPlugin;
import com.hypixel.hytale.builtin.asseteditor.AssetPath;
import com.hypixel.hytale.builtin.asseteditor.EditorClient;
import com.hypixel.hytale.builtin.asseteditor.assettypehandler.JsonTypeHandler;
import com.hypixel.hytale.codec.lookup.Priority;
import com.hypixel.hytale.protocol.packets.asseteditor.AssetEditorAssetType;
import com.hypixel.hytale.protocol.packets.asseteditor.AssetEditorEditorType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider;
import net.kaupenjoe.hytale.oregenv1.providers.CustomWorldGenProvider;
import org.bson.BsonDocument;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

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

        AssetEditorPlugin.get().getAssetTypeRegistry().registerAssetType("CustomCameraSettings", Priority.DEFAULT, CustomCameraSettings.class, JsonTypeHandler.get());
    }
}
