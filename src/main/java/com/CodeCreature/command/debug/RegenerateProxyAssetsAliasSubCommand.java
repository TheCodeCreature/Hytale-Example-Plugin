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
 * Alias wrapper for proxy regeneration commands so common variants still work.
 */
public class RegenerateProxyAssetsAliasSubCommand extends AbstractPlayerCommand {

    private final boolean dumpAll;

    public RegenerateProxyAssetsAliasSubCommand(@NonNull String name,
                                                @NonNull String description,
                                                boolean dumpAll) {
        super(name, description);
        this.dumpAll = dumpAll;
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        RegenerateProxyAssetsSubCommand.runRegeneration(playerRef, dumpAll);
    }
}
