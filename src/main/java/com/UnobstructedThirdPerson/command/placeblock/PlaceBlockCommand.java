package com.UnobstructedThirdPerson.command.placeblock;

import com.UnobstructedThirdPerson.command.placeblock.subcommands.AssignSubCommand;
import com.UnobstructedThirdPerson.command.placeblock.subcommands.ClearSubCommand;
import com.UnobstructedThirdPerson.command.placeblock.subcommands.InfoSubCommand;
import com.UnobstructedThirdPerson.command.placeblock.subcommands.ListSubCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Parent command for PlaceBlock operations during Phase 2a testing.
 *
 * Usage: /placeblock assign|clear|list|info
 */
public class PlaceBlockCommand extends AbstractCommandCollection {

    public PlaceBlockCommand() {
        super("placeblock", "Manage PlaceBlock placeholder arming");
        this.addSubCommand(new AssignSubCommand());
        this.addSubCommand(new ClearSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ListSubCommand());
    }
}
