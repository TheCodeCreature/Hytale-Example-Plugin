package com.UnobstructedThirdPerson.command.placeblock.subcommands;

import com.UnobstructedThirdPerson.placeblock.BlueprintBenchRecipeMutator;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockMetadata;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/**
 * Arms the held Block_Placeholder with a recipe.
 *
 * Usage: /placeblock assign <recipeId>
 */
public class AssignSubCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> recipeArg;

    public AssignSubCommand() {
        super("assign", "Arm held placeholder with a recipe");
        this.recipeArg = withRequiredArg("recipeId", "The recipe ID to assign", ArgTypes.STRING);
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

        String input = this.recipeArg.get(context);

        // Resolve recipe: try exact recipe ID first, then by output block type, then by output item ID
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(input);
        if (recipe == null) {
            recipe = findRecipeByOutput(input);
        }
        if (recipe == null) {
            playerRef.sendMessage(Message.raw("§c[PlaceBlock] No recipe found for: " + input
                    + "\n§7Tip: Use /placeblock list to see available recipe IDs."));
            return;
        }

        String recipeId = recipe.getId();

        // Verify the recipe has a placeable output
        String blockTypeId = getOutputBlockTypeId(recipe);
        if (blockTypeId == null) {
            playerRef.sendMessage(Message.raw("§c[PlaceBlock] Recipe '" + recipeId + "' does not produce a placeable block."));
            return;
        }

        // Arm the placeholder
        ItemStack armed = PlaceBlockMetadata.setArmedRecipeId(heldItem, recipeId, blockTypeId);

        // Replace held item in inventory
        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot >= 0) {
            inventory.getHotbar().setItemStackForSlot(activeSlot, armed);
        }

        playerRef.sendMessage(Message.raw("§a[PlaceBlock] Armed with: " + recipeId + " → " + blockTypeId));
    }

    private static String getOutputBlockTypeId(CraftingRecipe recipe) {
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null) return null;
        String outputItemId = primaryOutput.getItemId();
        if (outputItemId == null) return null;
        Item outputItem = Item.getAssetMap().getAsset(outputItemId);
        if (outputItem == null) return null;
        return outputItem.getBlockId();
    }

    /**
     * Searches all non-shadow recipes for one whose output block type ID or output item ID
     * matches the given input string. This lets players type block names or item names
     * instead of needing to know exact recipe IDs.
     */
    private static CraftingRecipe findRecipeByOutput(String input) {
        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;
            String id = recipe.getId();
            if (id.startsWith("Blueprint_")) continue;

            MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
            if (primaryOutput == null) continue;
            String outputItemId = primaryOutput.getItemId();
            if (outputItemId == null) continue;

            // Match by output item ID
            if (input.equals(outputItemId)) return recipe;

            // Match by output block type ID
            Item outputItem = Item.getAssetMap().getAsset(outputItemId);
            if (outputItem != null && input.equals(outputItem.getBlockId())) return recipe;
        }
        return null;
    }
}
