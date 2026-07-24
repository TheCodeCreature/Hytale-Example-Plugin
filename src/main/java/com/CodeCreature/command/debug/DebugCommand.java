package com.CodeCreature.command.debug;

/**
 * @node    DebugCommand
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Aggregates debug and diagnostics command entry points, including runtime crafting parity probes.
 * @wave    1 (runtime parity diagnostics)
 * @status  Wave 1 - craftprobe subcommand registered
 * @do-not  Implement diagnostics behavior directly in this collection type.
 *          Remove existing debug subcommands while adding new probes.
 */

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
