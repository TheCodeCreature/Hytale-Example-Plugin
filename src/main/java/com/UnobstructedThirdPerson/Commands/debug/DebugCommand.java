package com.UnobstructedThirdPerson.Commands.debug;

import com.UnobstructedThirdPerson.Commands.debug.SubCommands.ListHitboxTypesSubCommand;
import com.UnobstructedThirdPerson.Commands.debug.SubCommands.PreviewBlockSubCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Parent debug command for various debugging utilities.
 * 
 * Usage: /Debug <subcommand>
 */
public class DebugCommand extends AbstractCommandCollection {

    public DebugCommand() {
        super("Debug", "Debug utilities for development");
        this.addSubCommand(new ListHitboxTypesSubCommand());
        this.addSubCommand(new PreviewBlockSubCommand());
    }
}
