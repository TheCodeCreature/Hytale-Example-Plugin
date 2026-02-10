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

public class CameraDebugDumpCommand extends AbstractPlayerCommand {

    public CameraDebugDumpCommand() {
        super("dump", "Dump cached camera settings to console");
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, 
                          @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        ExtendedCameraSettings settings = CameraSettingsApplier.getCachedSettings();
        if (settings != null) {
            CameraDebugManager.log("Cached ExtendedCameraSettings:\n" + settings.toFullString());
            playerRef.sendMessage(Message.translation("Camera settings dumped to console"));
        } else {
            CameraDebugManager.log("No cached camera settings");
            playerRef.sendMessage(Message.translation("No cached camera settings"));
        }
    }
}
