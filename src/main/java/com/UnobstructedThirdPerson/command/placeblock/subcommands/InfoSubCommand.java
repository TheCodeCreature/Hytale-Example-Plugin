package com.UnobstructedThirdPerson.command.placeblock.subcommands;

import com.UnobstructedThirdPerson.placeblock.PlaceBlockMetadata;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/**
 * Shows the current armed state of the held Block_Placeholder.
 *
 * Usage: /placeblock info
 */
public class InfoSubCommand extends AbstractPlayerCommand {

    public InfoSubCommand() {
        super("info", "Show armed state of held placeholder");
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("§c[PlaceBlock] Could not resolve player."));
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack heldItem = inventory.getItemInHand();

        if (!PlaceBlockMetadata.isPlaceBlock(heldItem)) {
            playerRef.sendMessage(Message.raw("§c[PlaceBlock] You must hold a Block_Placeholder."));
            return;
        }

        if (!PlaceBlockMetadata.isArmed(heldItem)) {
            playerRef.sendMessage(Message.raw("§e[PlaceBlock] Placeholder is unarmed (Blue)."));
            return;
        }

        String recipeId = PlaceBlockMetadata.getArmedRecipeId(heldItem);
        String blockTypeId = PlaceBlockMetadata.getOutputBlockTypeId(heldItem);
        playerRef.sendMessage(Message.raw(
                "§a[PlaceBlock] Armed — Recipe: " + recipeId + " | Block: " + blockTypeId + " | Item: " + heldItem.getItemId()));
    }
}
