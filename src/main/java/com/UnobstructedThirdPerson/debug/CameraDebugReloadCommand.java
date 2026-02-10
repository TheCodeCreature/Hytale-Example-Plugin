package com.UnobstructedThirdPerson.debug;

import com.UnobstructedThirdPerson.camera.CameraSettingsApplier;
import com.UnobstructedThirdPerson.camera.ExtendedCameraSettings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class CameraDebugReloadCommand extends AbstractPlayerCommand {

    public CameraDebugReloadCommand() {
        super("reload", "Reload camera settings from Player.json");
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, 
                          @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        CameraDebugManager.log("Reloading camera settings from Player.json");
        CameraSettingsApplier.reloadSettings();
        ExtendedCameraSettings settings = CameraSettingsApplier.getCachedSettings();
        if (settings != null) {
            CameraDebugManager.log("Reloaded settings:\n" + settings.toFullString());
        }
        playerRef.sendMessage(Message.translation("Camera settings reloaded from Player.json"));
    }
}
