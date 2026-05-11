package com.CodeCreature.command.placeblock;

import com.CodeCreature.command.placeblock.subcommands.StencilSubCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Parent command for PlaceBlock tool commands.
 *
 * Usage: /placeblock gridtest|stencil
 */
public class PlaceBlockCommand extends AbstractCommandCollection {

    public PlaceBlockCommand() {
        super("placeblock", "PlaceBlock tool commands");
        this.addSubCommand(new StencilSubCommand());
    }
}
