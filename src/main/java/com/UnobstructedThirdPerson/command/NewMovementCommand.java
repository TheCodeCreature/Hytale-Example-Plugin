package com.UnobstructedThirdPerson.command;

import com.UnobstructedThirdPerson.movement.NewMovementSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public class NewMovementCommand extends AbstractPlayerCommand {

    private static final Logger LOGGER = Logger.getLogger("WalkOnAirCommand");

    private final OptionalArg<String> modeArg;
    private final OptionalArg<Float> gravityArg;

    public NewMovementCommand() {
        super("WalkOnAir", "Prevent falling below a configured Y floor while still allowing jumping");
        this.addAliases("woa", "airwalk", "walkair");
        this.modeArg = withOptionalArg("Mode", "Mode: on, off, toggle, set, status", ArgTypes.STRING);
        this.gravityArg = withOptionalArg("Gravity", "Gravity Level", ArgTypes.FLOAT);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerId = playerRef.getUuid();
        boolean enabled = NewMovementSystem.isMoonGravityEnabled(playerId);

        double defaultFactor = NewMovementSystem.DEFAULT_MOON_GRAVITY_FACTOR;

        String mode = modeArg.provided(context)
            ? modeArg.get(context).toLowerCase(Locale.ROOT)
            : "toggle";
        boolean hasGravityArg = gravityArg.provided(context);
        double gravityFactor = hasGravityArg ? gravityArg.get(context) : defaultFactor;

        switch (mode) {
            case "on":
            case "enable":
                NewMovementSystem.enableMoonGravity(playerId, gravityFactor);
                return;
            case "off":
            case "disable":
                if (!enabled) {
                    playerRef.sendMessage(Message.raw("Moon Gravity is already disabled."));
                    return;
                }
                NewMovementSystem.disableMoonGravity(playerId);
                playerRef.sendMessage(Message.raw("Moon Gravity disabled."));
                return;
            case "set":
                if (!hasGravityArg) {
                    return;
                }
                NewMovementSystem.enableMoonGravity(playerId, gravityFactor);
                return;
            case "status":
                if (!enabled) {
                    playerRef.sendMessage(Message.raw("Moon Gravity is disabled."));
                    return;
                }
                Double activeFactor = NewMovementSystem.getMoonGravityFactor(playerId);
                return;
            case "toggle":
                if (enabled) {
                    NewMovementSystem.disableMoonGravity(playerId);
                    playerRef.sendMessage(Message.raw("Moon Gravity disabled."));
                } else {
                    NewMovementSystem.enableMoonGravity(playerId, gravityFactor);
                    playerRef.sendMessage(Message.raw("Moon Gravity enabled."));
                }
                return;
            default:
                playerRef.sendMessage(Message.raw("Unknown mode: " + mode + ". Use on, off, toggle, set, or status."));
        }
    }
}
