package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.Settings.CustomCameraSettings;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class StartUnobstructedCameraCommand extends AbstractPlayerCommand {
    private final OptionalArg<Float> distanceArg;

    public StartUnobstructedCameraCommand(){
        super("Start","Starts the camera");
        this.distanceArg = withOptionalArg("Distance", "Set the Distance from the camera.", ArgTypes.FLOAT).addValidator(Validators.greaterThan(0f));
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        ServerCameraSettings settings = new CustomCameraSettings().Settings;

        float distance = distanceArg.get(commandContext);
        if(distance != 0) settings.distance = distance;

        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, settings));
    }
}
