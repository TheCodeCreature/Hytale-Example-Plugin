package com.CodeCreature.command.debug;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class DebugCommand extends AbstractCommandCollection {

    public DebugCommand() {
        super("debug", "Debug and diagnostic commands");
        this.addSubCommand(new LoggingSubCommand());
        this.addSubCommand(new BreakLogSubCommand());
        this.addSubCommand(new CraftingParityProbeSubCommand());
        this.addSubCommand(new RegenerateProxyAssetsSubCommand());
        this.addSubCommand(new RegenerateProxyAssetsAllSubCommand());
        this.addSubCommand(new RegenerateProxyAssetsAliasSubCommand(
            "regenproxiesa",
            "Alias: recreate generic proxy assets and dump all proxy JSON files",
            true));
        this.addSubCommand(new RegenerateProxyAssetsAliasSubCommand(
            "regenproxy",
            "Alias: recreate generic proxy assets and dump JSON files",
            false));
    }
}
