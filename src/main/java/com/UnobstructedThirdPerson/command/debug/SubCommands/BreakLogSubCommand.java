package com.UnobstructedThirdPerson.command.debug.SubCommands;

import com.UnobstructedThirdPerson.resourcecollection.BreakBlockDiagnostic;
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
 * Toggles the {@link BreakBlockDiagnostic} system on/off.
 * When enabled, every broken block dumps its full gathering config
 * and drop details to the server console.
 *
 * <p>Usage: {@code /Debug BreakLog}
 */
public class BreakLogSubCommand extends AbstractPlayerCommand {

    public BreakLogSubCommand() {
        super("BreakLog", "Toggle block-break diagnostic logging");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        boolean nowEnabled = BreakBlockDiagnostic.toggle();
        String status = nowEnabled ? "§aENABLED" : "§cDISABLED";
        playerRef.sendMessage(Message.raw("§e[BreakLog] Diagnostic logging " + status));
        System.out.println("[BreakLog] Diagnostic logging " + (nowEnabled ? "ENABLED" : "DISABLED"));
    }
}
