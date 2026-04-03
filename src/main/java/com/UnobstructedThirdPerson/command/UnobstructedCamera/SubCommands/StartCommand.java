package com.UnobstructedThirdPerson.command.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.camera.*;
import com.UnobstructedThirdPerson.shape.v1.ShapeCompositorPresets;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class StartCommand extends AbstractPlayerCommand {
    private final OptionalArg<Integer> radiusArg;

    public StartCommand(){
        super("Start","Starts the camera transparency system");
        this.radiusArg = withOptionalArg("Radius", "Set the size of the transparency radius", ArgTypes.INTEGER).addValidator(Validators.min(0)).addValidator(Validators.max(20));
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        var compositor = this.radiusArg.provided(context)?
                new ShapeCompositorPresets(this.radiusArg.get(context)).Test():
                new ShapeCompositorPresets().Test();

        CameraTransparencyVolume.StartTransparencyVolumeLoop(playerRef, world, compositor);
    }
}
