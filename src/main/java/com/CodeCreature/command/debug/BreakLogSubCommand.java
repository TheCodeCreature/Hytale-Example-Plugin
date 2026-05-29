package com.CodeCreature.command.debug;

import com.CodeCreature.util.FeatureFlags;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/**
 * Toggles the break-block diagnostic logging via {@link FeatureFlags}.
 *
 * <p>Usage: {@code /debug breaklog}</p>
 *
 * <p>Toggles the {@code diagnostics.breakLog} feature flag, which controls
 * whether {@code BreakBlockDiagnostic} logs detailed drop configuration
 * when a block is broken.</p>
 *
 * <p>This command replaces the previous toggle mechanism that used a local
 * {@code AtomicBoolean} inside {@code BreakBlockDiagnostic}. After migration,
 * the toggle state is centralized in {@link FeatureFlags} and persisted to
 * {@code feature_flags.json}.</p>
 */
public class BreakLogSubCommand extends AbstractPlayerCommand {

    public BreakLogSubCommand() {
        super("breaklog", "Toggle break-block diagnostic logging");
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        // TODO: boolean newVal = FeatureFlags.toggle("diagnostics.breakLog");
        // TODO: String status = newVal
        //           ? "§a[Debug] Break-block diagnostic logging ENABLED"
        //           : "§c[Debug] Break-block diagnostic logging DISABLED";
        // TODO: playerRef.sendMessage(Message.raw(status));
    }
}
