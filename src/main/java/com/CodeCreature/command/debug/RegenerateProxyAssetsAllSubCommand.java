package com.CodeCreature.command.debug;

import org.jspecify.annotations.NonNull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Alias command for forcing full proxy dump mode when command parsers do not
 * accept trailing optional args after subcommand resolution.
 *
 * <p>Usage: {@code /debug regenproxiesall}</p>
 */
public class RegenerateProxyAssetsAllSubCommand extends AbstractPlayerCommand {

    public RegenerateProxyAssetsAllSubCommand() {
        super("regenproxiesall", "Recreate generic proxy assets and dump all proxy JSON files");
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        RegenerateProxyAssetsSubCommand.runRegeneration(playerRef, true);
    }
}
