package com.UnobstructedThirdPerson.command.debug.SubCommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Spawns a debug cube shape at the player's current position.
 *
 * Usage: /Debug DebugCubeShape
 */
public class DebugCubeShapeSubCommand extends AbstractPlayerCommand {

    private static final double CUBE_SCALE = 1.0;
    private static final float DISPLAY_SECONDS = 30.0F;

    public DebugCubeShapeSubCommand() {
        super("DebugCubeShape", "Spawns a debug cube at your position");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3d position = playerRef.getTransform().getPosition();
        Vector3f color = new Vector3f(0.137F, 0.867F, 0.882F);

        DebugUtils.addCube(world, position, color, CUBE_SCALE, DISPLAY_SECONDS);

        playerRef.sendMessage(Message.raw(
                "Debug cube created at "
                        + (int) Math.floor(position.x)
                        + ", "
                        + (int) Math.floor(position.y)
                        + ", "
                        + (int) Math.floor(position.z)
                        + " (scale "
                        + CUBE_SCALE
                        + ", "
                        + (int) DISPLAY_SECONDS
                        + "s)."
        ));
    }
}
