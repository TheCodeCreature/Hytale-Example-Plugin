package com.UnobstructedThirdPerson.command;

import com.UnobstructedThirdPerson.movement.PlayerCollisionValidationSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Locale;
import java.util.UUID;

public class WalkOnAirCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> modeArg;
    private final OptionalArg<Integer> heightArg;

    public WalkOnAirCommand() {
        super("WalkOnAir", "Prevent falling below a configured Y floor while still allowing jumping");
        this.addAliases("woa", "airwalk", "walkair");
        this.modeArg = withOptionalArg("Mode", "Mode: on, off, toggle, set, status", ArgTypes.STRING);
        this.heightArg = withOptionalArg("Height", "Floor Y level", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerId = playerRef.getUuid();
        boolean enabled = PlayerCollisionValidationSystem.isWalkOnAirEnabled(playerId);

        Vector3d position = getPlayerPosition(store, ref);
        double defaultFloor = position != null ? Math.floor(position.y) : 0.0;

        String mode = modeArg.provided(context)
            ? modeArg.get(context).toLowerCase(Locale.ROOT)
            : "toggle";
        boolean hasHeight = heightArg.provided(context);
        double targetFloor = hasHeight ? heightArg.get(context) : defaultFloor;

        switch (mode) {
            case "on":
            case "enable":
                PlayerCollisionValidationSystem.enableWalkOnAir(playerId, targetFloor);
                playerRef.sendMessage(Message.raw("Walk-on-air enabled at floor Y=" + formatFloor(targetFloor)));
                return;
            case "off":
            case "disable":
                if (!enabled) {
                    playerRef.sendMessage(Message.raw("Walk-on-air is already disabled."));
                    return;
                }
                PlayerCollisionValidationSystem.disableWalkOnAir(playerId);
                playerRef.sendMessage(Message.raw("Walk-on-air disabled."));
                return;
            case "set":
                if (!hasHeight) {
                    playerRef.sendMessage(Message.raw("Usage: /WalkOnAir set <height>"));
                    return;
                }
                PlayerCollisionValidationSystem.enableWalkOnAir(playerId, targetFloor);
                playerRef.sendMessage(Message.raw("Walk-on-air floor set to Y=" + formatFloor(targetFloor)));
                return;
            case "status":
                if (!enabled) {
                    playerRef.sendMessage(Message.raw("Walk-on-air is disabled."));
                    return;
                }
                Double activeFloor = PlayerCollisionValidationSystem.getWalkOnAirFloor(playerId);
                playerRef.sendMessage(Message.raw("Walk-on-air is enabled at Y=" + formatFloor(activeFloor != null ? activeFloor : defaultFloor)));
                return;
            case "toggle":
                if (enabled) {
                    PlayerCollisionValidationSystem.disableWalkOnAir(playerId);
                    playerRef.sendMessage(Message.raw("Walk-on-air disabled."));
                } else {
                    PlayerCollisionValidationSystem.enableWalkOnAir(playerId, targetFloor);
                    playerRef.sendMessage(Message.raw("Walk-on-air enabled at floor Y=" + formatFloor(targetFloor)));
                }
                return;
            default:
                playerRef.sendMessage(Message.raw("Unknown mode: " + mode + ". Use on, off, toggle, set, or status."));
        }
    }

    @Nullable
    private Vector3d getPlayerPosition(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        return transform != null ? transform.getPosition() : null;
    }

    private String formatFloor(double floor) {
        if (Math.rint(floor) == floor) {
            return Integer.toString((int) floor);
        }
        return Double.toString(floor);
    }
}
