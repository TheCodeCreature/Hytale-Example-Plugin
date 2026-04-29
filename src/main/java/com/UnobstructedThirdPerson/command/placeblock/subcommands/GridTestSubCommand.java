package com.UnobstructedThirdPerson.command.placeblock.subcommands;

import com.UnobstructedThirdPerson.placeblock.ui.gridtest.ItemGridTestPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/**
 * Opens the BlockSelector test page.
 * Usage: /placeblock gridtest
 */
public class GridTestSubCommand extends AbstractPlayerCommand {

    public GridTestSubCommand() {
        super("gridtest", "Open UI element test page");
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("§c[GridTest] Could not resolve player."));
            return;
        }

        ItemGridTestPage page = new ItemGridTestPage(playerRef);
        player.getPageManager().openCustomPage(ref, store, page);
        playerRef.sendMessage(Message.raw("§a[GridTest] Test page opened."));
    }
}
