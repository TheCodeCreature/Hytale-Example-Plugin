package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.Settings.CustomCameraSettings;
import com.UnobstructedThirdPerson.camera.CameraTransparencyVolume;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.UnobstructedThirdPerson.fix.InteractionPositionFixer;
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
import org.jspecify.annotations.Nullable;

public class StartUnobstructedCameraCommand extends AbstractPlayerCommand {
    private final OptionalArg<Float> distanceArg;
    private final OptionalArg<Boolean> redirectModeArg;

    public StartUnobstructedCameraCommand(){
        super("Start","Starts the camera");
        this.distanceArg = withOptionalArg("Distance", "Set the Distance from the camera.", ArgTypes.FLOAT).addValidator(Validators.greaterThan(0f));
        this.redirectModeArg = withOptionalArg("RedirectBlocks", "Redirect block interactions to camera target (true) or block them (false). Default: true", ArgTypes.BOOLEAN);
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        ServerCameraSettings settings = new CustomCameraSettings().Settings;

        var distance = distanceArg.get(commandContext);
        if(distance != null) settings.distance = distance;

        // Set redirect mode if specified
        var redirectMode = redirectModeArg.get(commandContext);
        if(redirectMode != null) {
            InteractionPositionFixer.setRedirectMode(redirectMode);
        }

        // Enable the interaction position fixer to correct stale block positions
        InteractionPositionFixer.enableForPlayer(playerRef);

        // Create camera transparency volume for this player
        CameraTransparencyVolume.getOrCreate(playerRef, world, new Ellipsoid(10));

        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, settings));
    }
}
