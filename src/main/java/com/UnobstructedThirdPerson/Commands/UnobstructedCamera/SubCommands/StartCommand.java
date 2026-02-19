package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.camera.CameraTransparencyVolume;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class StartCommand extends AbstractPlayerCommand {
    private final OptionalArg<Float> radiusArg;

    public StartCommand(){
        super("Start","Starts the camera");
        this.radiusArg = withOptionalArg("Radius", "Set the size of the transparency radius", ArgTypes.FLOAT).addValidator(Validators.greaterThan(0f)).addValidator(Validators.lessThan(20f));
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        float DEFAULT_RADIUS = 6;
        float radius = this.radiusArg.provided(context)? this.radiusArg.get(context): DEFAULT_RADIUS;

        // Create camera transparency volume for this player
        CameraTransparencyVolume.getOrCreate(playerRef, world, new Shape[]{new Ellipsoid(radius)});
    }
}
