package com.UnobstructedThirdPerson.command.placeblock.subcommands;

import com.UnobstructedThirdPerson.stencil.StencilMetadata;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/**
 * Test command for creating blueprint stencil items during POC testing.
 *
 * <p>Usage: {@code /placeblock stencil <itemId>}</p>
 *
 * <p>Resolves the crafting recipe whose output matches the given item ID,
 * then creates a stencil ItemStack via {@link StencilMetadata#createStencil}
 * and adds it to the player's inventory.</p>
 *
 * <p><b>Example:</b> {@code /placeblock stencil Wood_Hardwood_Fence}</p>
 */
public class StencilSubCommand extends AbstractPlayerCommand {

    /** The item ID (e.g., "Wood_Hardwood_Fence"). */
    private final RequiredArg<String> itemIdArg;

    public StencilSubCommand() {
        super("stencil", "Create a blueprint stencil for testing");
        this.itemIdArg = withRequiredArg("itemId", "Item ID (e.g., Wood_Hardwood_Fence)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("§c[Stencil] Could not resolve player."));
            return;
        }

        String itemId = itemIdArg.get(context);

        // Resolve recipe by output item ID or block type ID
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(itemId);
        if (recipe == null) {
            recipe = findRecipeByOutput(itemId);
        }
        if (recipe == null) {
            playerRef.sendMessage(Message.raw("§c[Stencil] No recipe found for: " + itemId));
            return;
        }

        // Derive the output item ID from the recipe (the item we'll create the stencil from)
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null || primaryOutput.getItemId() == null) {
            playerRef.sendMessage(Message.raw("§c[Stencil] Recipe has no output item."));
            return;
        }
        String outputItemId = primaryOutput.getItemId();

        // Verify the output item has a block type (is placeable)
        Item outputItem = Item.getAssetMap().getAsset(outputItemId);
        if (outputItem == null || outputItem.getBlockId() == null) {
            playerRef.sendMessage(Message.raw("§c[Stencil] '" + outputItemId + "' is not a placeable block."));
            return;
        }

        // Create stencil using the output item ID and resolved recipe ID
        String recipeId = recipe.getId();
        ItemStack stencil = StencilMetadata.createStencil(outputItemId, recipeId);
        player.getInventory().getCombinedHotbarFirst().addItemStack(stencil);

        playerRef.sendMessage(Message.raw("§a[Stencil] Created: " + outputItemId + " (recipe: " + recipeId + ")"));
    }

    /**
     * Searches all non-shadow recipes for one whose output item ID or output block type ID
     * matches the given input string.
     */
    private static CraftingRecipe findRecipeByOutput(String input) {
        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;
            if (recipe.getId().startsWith("Blueprint_")) continue;

            MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
            if (primaryOutput == null) continue;
            String outputItemId = primaryOutput.getItemId();
            if (outputItemId == null) continue;

            if (input.equals(outputItemId)) return recipe;

            Item outputItem = Item.getAssetMap().getAsset(outputItemId);
            if (outputItem != null && input.equals(outputItem.getBlockId())) return recipe;
        }
        return null;
    }
}
