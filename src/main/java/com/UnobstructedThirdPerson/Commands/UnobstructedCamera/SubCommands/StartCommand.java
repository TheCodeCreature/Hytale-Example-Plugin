package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.camera.*;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.EventListener;

public class StartCommand extends AbstractPlayerCommand {
    private final OptionalArg<Integer> radiusArg;

    public StartCommand(){
        super("Start","Starts the camera transparency system");
        this.radiusArg = withOptionalArg("Radius", "Set the size of the transparency radius", ArgTypes.INTEGER).addValidator(Validators.min(0)).addValidator(Validators.max(20));
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        int DEFAULT_RADIUS = 6;
        int radius = this.radiusArg.provided(context)? this.radiusArg.get(context): DEFAULT_RADIUS;

        // Parametric method: Front/back split with placeholders and empty blocks
        ShapeCompositor compositor = new ShapeCompositor(new Vector3i(0, 0, 0));

        // apply shape similar to the first test of this
        compositor = Original(compositor, radius);

        // Operation 5: Exclude floor
        compositor.addOperation("floor_exclusion",
                new Box(-radius, -radius, -radius, radius, 0, radius),
                OperationType.EXCLUDE,
                null).build();
        // Create camera transparency volume with parametric compositor
        CameraTransparencyVolume.getOrCreate(playerRef, world, compositor);
    }

    private ShapeCompositor Original(ShapeCompositor compositor, int radius){
        var shape = new TransformedShape(new Ellipsoid(radius), 0,0,-radius/2);

        // Operation 1: Define outer ellipsoid boundary (geometry only)
        compositor.addOperation("camera_volume",
                shape,
                OperationType.DEFINE,
                null)
                .build();

        // Operation 1: Define outer ellipsoid boundary (geometry only)
        compositor.addOperation("empty_fill",
                shape,
                OperationType.FILL_REMAINING,
                new EmptyBlockFill())
                .withReference("camera_volume")
                .build();
        return compositor;
    }

    private ShapeCompositor HalfHalf(ShapeCompositor compositor, int radius){

        // Operation 1: Define outer ellipsoid boundary (geometry only)
        compositor.addOperation("camera_volume",
                new Ellipsoid(radius),
                OperationType.DEFINE,
                null).build();  // No fill - just defines the region

        // Operation 2: Front half (closer to player) = placeholders with hitbox-based shapes
        compositor.addOperation("front_zone",
                        new Box(-radius, -radius, 2, radius, radius, radius),  // Front hemisphere (positive Z)
                        OperationType.INTERSECT,
                        new AutoPlaceholderFill())  // PARAMETER: auto-select placeholders based on hitbox
                .withReference("camera_volume")
                .build();

        // Operation 3: Back half = empty blocks (fill remaining in ellipsoid)
        compositor.addOperation("back_zone",
                        null,  // No new geometry
                        OperationType.FILL_REMAINING,
                        new EmptyBlockFill())  // PARAMETER: use air blocks
                .withReference("camera_volume")
                .build();

//      // Operation 4: Exclude immediate player area (1x2x1 box around player)
//      compositor.addOperation("player_exclusion",
//          new Box(-1, -1, -1, 1, 2, 1),
//          OperationType.EXCLUDE,
//          null).build();

        // Operation 5: Exclude floor
        compositor.addOperation("floor_exclusion",
                new Box(-radius, -radius, -radius, radius, 0, radius),
                OperationType.EXCLUDE,
                null).build();
        return compositor;
    }
}
