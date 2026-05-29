package com.CodeCreature.command.debug;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Parent command group for debug and diagnostic commands.
 *
 * <p>Usage: {@code /debug logging|breaklog}</p>
 *
 * <p>Follows the same pattern as {@code PlaceBlockCommand} — an
 * {@link AbstractCommandCollection} that groups subcommands.</p>
 *
 * <p>Registered in {@code Plugin.setup()} via:
 * {@code this.getCommandRegistry().registerCommand(new DebugCommand())}</p>
 */
public class DebugCommand extends AbstractCommandCollection {

    public DebugCommand() {
        super("debug", "Debug and diagnostic commands");
        // TODO: this.addSubCommand(new LoggingSubCommand());
        // TODO: this.addSubCommand(new BreakLogSubCommand());
    }
}
