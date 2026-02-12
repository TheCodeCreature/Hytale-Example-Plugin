package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.camera.CameraTransparencyVolume;
import com.UnobstructedThirdPerson.fix.InteractionPositionFixer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class ResetCameraCommand extends AbstractPlayerCommand {
    public ResetCameraCommand(){
        super("Stop","Stops the camera");
    }
    @Override
    protected void execute(@NonNull CommandContext commandContext, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        // Disable the interaction position fixer when resetting camera
        InteractionPositionFixer.disableForPlayer(playerRef.getUuid());

        // Shutdown camera transparency volume - restores all transparent blocks
        CameraTransparencyVolume.remove(playerRef.getUuid());

        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
    }
}
