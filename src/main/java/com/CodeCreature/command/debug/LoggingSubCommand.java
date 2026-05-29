package com.CodeCreature.command.debug;

import com.CodeCreature.util.DebugLogger;
import com.CodeCreature.util.FeatureFlags;
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
import org.jspecify.annotations.NonNull;

/**
 * Toggles debug logging flags via {@link FeatureFlags}.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /debug logging} — toggles {@code logging.global}</li>
 *   <li>{@code /debug logging <subsystem>} — toggles {@code logging.<subsystem>}</li>
 * </ul>
 *
 * <p>Valid subsystem names correspond to {@link DebugLogger.Subsystem} values
 * (case-insensitive): plugin, stencil, blueprint_bench, blueprint_book,
 * scaling, registry, crafting, ingredient_tree.</p>
 *
 * <p>Sends a color-coded status message to the player:
 * {@code §a} for enabled, {@code §c} for disabled.</p>
 */
public class LoggingSubCommand extends AbstractPlayerCommand {

    /** Optional subsystem name. If not provided, toggles the global flag. */
    private final OptionalArg<String> subsystemArg;

    public LoggingSubCommand() {
        super("logging", "Toggle debug logging (global or per-subsystem)");
        this.subsystemArg = withOptionalArg("subsystem",
                "Subsystem name (e.g. stencil, scaling)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        // TODO: Check if subsystemArg.provided(context)
        //
        // If NOT provided:
        //   boolean newVal = FeatureFlags.toggle("logging.global");
        //   String status = newVal ? "§a[Debug] Global logging ENABLED" : "§c[Debug] Global logging DISABLED";
        //   playerRef.sendMessage(Message.raw(status));
        //
        // If provided:
        //   String input = subsystemArg.get(context);
        //   Validate input against DebugLogger.Subsystem.values() (case-insensitive)
        //   If invalid: playerRef.sendMessage(Message.raw("§c[Debug] Unknown subsystem: " + input))
        //     and list valid names
        //   If valid:
        //     DebugLogger.Subsystem sub = matched subsystem
        //     boolean newVal = FeatureFlags.toggle(sub.flagKey());
        //     String status = newVal ? "§a[Debug] " + sub.name() + " logging ENABLED"
        //                            : "§c[Debug] " + sub.name() + " logging DISABLED";
        //     playerRef.sendMessage(Message.raw(status));
    }
}
