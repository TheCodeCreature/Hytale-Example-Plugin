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
 * Clears the armed recipe from the held Block_Placeholder.
 *
 * Usage: /placeblock clear
 */
public class ClearSubCommand extends AbstractPlayerCommand {

    public ClearSubCommand() {
        super("clear", "Clear armed recipe from held placeholder");
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
            playerRef.sendMessage(Message.raw("§e[PlaceBlock] Placeholder is already unarmed."));
            return;
        }

        ItemStack cleared = PlaceBlockMetadata.disarm(heldItem);

        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot >= 0) {
            inventory.getHotbar().setItemStackForSlot(activeSlot, cleared);
        }

        playerRef.sendMessage(Message.raw("§a[PlaceBlock] Placeholder disarmed."));
    }
}
