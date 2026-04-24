package com.UnobstructedThirdPerson.command.placeblock.subcommands;

import com.UnobstructedThirdPerson.placeblock.BlueprintBenchRecipeMutator;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists available placeable recipes that can be assigned to a placeholder.
 *
 * Usage: /placeblock list [filter]
 */
public class ListSubCommand extends AbstractPlayerCommand {

    private static final int PAGE_SIZE = 20;

    private final OptionalArg<String> filterArg;

    public ListSubCommand() {
        super("list", "List available placeable recipes");
        this.filterArg = withOptionalArg("filter", "Filter recipes by name (substring match)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {

        String filter = this.filterArg.provided(context)
                ? this.filterArg.get(context).toLowerCase()
                : null;

        List<String> matches = new ArrayList<>();

        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;

            String id = recipe.getId();
            // Skip shadow recipes — show only originals
            if (id.startsWith("Blueprint_")) continue;

            // Must have a placeable output
            MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
            if (primaryOutput == null) continue;
            String outputItemId = primaryOutput.getItemId();
            if (outputItemId == null) continue;
            Item outputItem = Item.getAssetMap().getAsset(outputItemId);
            if (outputItem == null || outputItem.getBlockId() == null) continue;

            // Apply filter
            if (filter != null && !id.toLowerCase().contains(filter)) continue;

            matches.add(id);
        }

        matches.sort(String::compareToIgnoreCase);

        if (matches.isEmpty()) {
            playerRef.sendMessage(Message.raw("§e[PlaceBlock] No matching recipes found."));
            return;
        }

        int total = matches.size();
        int showing = Math.min(total, PAGE_SIZE);

        StringBuilder sb = new StringBuilder();
        sb.append("§a[PlaceBlock] ").append(total).append(" placeable recipes");
        if (filter != null) sb.append(" matching '").append(filter).append("'");
        sb.append(" (showing first ").append(showing).append("):\n");

        for (int i = 0; i < showing; i++) {
            sb.append("§7  ").append(matches.get(i));
            if (i < showing - 1) sb.append("\n");
        }

        if (total > PAGE_SIZE) {
            sb.append("\n§e  ... and ").append(total - PAGE_SIZE).append(" more. Use a filter to narrow results.");
        }

        playerRef.sendMessage(Message.raw(sb.toString()));
    }
}
