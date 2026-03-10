package com.UnobstructedThirdPerson.command.debug;

import com.UnobstructedThirdPerson.command.debug.SubCommands.DebugCubeShapeSubCommand;
import com.UnobstructedThirdPerson.command.debug.SubCommands.DebugTexturedCubeSubCommand;
import com.UnobstructedThirdPerson.command.debug.SubCommands.ListHitboxTypesSubCommand;
import com.UnobstructedThirdPerson.command.debug.SubCommands.PreviewBlockSubCommand;
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
        this.addSubCommand(new DebugCubeShapeSubCommand());
        this.addSubCommand(new DebugTexturedCubeSubCommand());
    }
}
