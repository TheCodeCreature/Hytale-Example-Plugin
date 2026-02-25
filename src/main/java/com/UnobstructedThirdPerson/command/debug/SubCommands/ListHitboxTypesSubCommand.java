package com.UnobstructedThirdPerson.command.debug.SubCommands;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Subcommand to list all available BlockBoundingBoxes (hitbox types) from the asset map.
 * This helps identify which placeholder blocks need to be created.
 * 
 * Usage: /Debug ListHitboxTypes
 */
public class ListHitboxTypesSubCommand extends AbstractPlayerCommand {
    private static final Logger LOGGER = Logger.getLogger(ListHitboxTypesSubCommand.class.getName());

    public ListHitboxTypesSubCommand() {
        super("ListHitboxTypes", "Lists all available hitbox types");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext, @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        IndexedLookupTableAssetMap<String, BlockBoundingBoxes> assetMap = BlockBoundingBoxes.getAssetMap();
        
        if (assetMap == null) {
            playerRef.sendMessage(Message.raw("§cBlockBoundingBoxes asset map is null!"));
            LOGGER.severe("[ListHitboxTypes] BlockBoundingBoxes asset map is null");
            return;
        }

        // Collect all hitbox type IDs
        List<String> hitboxTypes = new ArrayList<>();
        int maxIndex = assetMap.getNextIndex();
        for (int i = 0; i < maxIndex; i++) {
            BlockBoundingBoxes hitbox = assetMap.getAsset(i);
            if (hitbox != null && hitbox.getId() != null) {
                hitboxTypes.add(hitbox.getId());
            }
        }

        // Sort alphabetically for easier reading
        Collections.sort(hitboxTypes);

        // Log to console
        LOGGER.info("=== BlockBoundingBoxes (Hitbox Types) ===");
        LOGGER.info("Total count: " + hitboxTypes.size());
        for (String type : hitboxTypes) {
            LOGGER.info("  - " + type);
        }
        LOGGER.info("=========================================");

        // Send summary to player
        playerRef.sendMessage(Message.raw("§aFound §f" + hitboxTypes.size() + " §ahitbox types. Check server console for full list."));
        
        // Send first 10 as preview
        int previewCount = Math.min(10, hitboxTypes.size());
        playerRef.sendMessage(Message.raw("§7First " + previewCount + " types:"));
        for (int i = 0; i < previewCount; i++) {
            playerRef.sendMessage(Message.raw("§7  - §f" + hitboxTypes.get(i)));
        }
        if (hitboxTypes.size() > 10) {
            playerRef.sendMessage(Message.raw("§7  ... and " + (hitboxTypes.size() - 10) + " more (see console)"));
        }
    }
}
