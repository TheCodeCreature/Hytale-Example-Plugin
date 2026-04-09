package com.UnobstructedThirdPerson.command.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.camera.v2.CameraTransparencyVolumeV2;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class ToggleBoundingShapesCommand extends AbstractPlayerCommand {

    public ToggleBoundingShapesCommand() {
        super("ToggleBoundingShapes", "Toggle debug bounding shape rendering on/off");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        CameraTransparencyVolumeV2 volume = CameraTransparencyVolumeV2.getInstance(playerRef.getUuid());
        if (volume == null) {
            playerRef.sendMessage(Message.raw("No active camera transparency volume."));
            return;
        }

        boolean newState = !volume.isDebugBoundingShapesEnabled();
        volume.setDebugBoundingShapesEnabled(newState);
        playerRef.sendMessage(Message.raw("Debug bounding shapes " + (newState ? "enabled" : "disabled") + "."));
    }
}
