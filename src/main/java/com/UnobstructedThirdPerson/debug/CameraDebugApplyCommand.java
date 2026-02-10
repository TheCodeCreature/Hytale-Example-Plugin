package com.UnobstructedThirdPerson.debug;

import com.UnobstructedThirdPerson.camera.CameraSettingsApplier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class CameraDebugApplyCommand extends AbstractPlayerCommand {

    public CameraDebugApplyCommand() {
        super("apply", "Force re-apply camera settings");
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, 
                          @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        CameraDebugManager.log("Force applying camera settings to " + playerRef.getUsername());
        CameraSettingsApplier.reloadSettings();
        CameraSettingsApplier.applyToPlayer(playerRef);
        playerRef.sendMessage(Message.translation("Camera settings reloaded and applied"));
    }
}
