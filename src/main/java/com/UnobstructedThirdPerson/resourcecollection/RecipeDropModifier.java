package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Replaces the breaking drop of each recipe block with its recipe's first
 * ingredient at {@code inputQty / outputQty} quantity.
 * Recipe inputs are already scaled by {@link CraftingCostModifier}, so the
 * resulting quantity reflects the 12x economy directly.
 *
 * Must be called after {@link BlockRecipeRegistry#init()} and
 * {@link CraftingCostModifier#apply()}.
 */
public final class RecipeDropModifier {

    private RecipeDropModifier() {}

    private static void log(String msg) {
        System.out.println("[RecipeDropMod] " + msg);
    }

    public static void apply() {
        Field breakingField;
        try {
            breakingField = BlockGathering.class.getDeclaredField("breaking");
            breakingField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find 'breaking' field on BlockGathering: " + e.getMessage());
            return;
        }

        int modified = 0;
        int skipped = 0;

        for (var entry : BlockRecipeRegistry.getAllRecipesByBlockType().entrySet()) {
            String blockTypeId = entry.getKey();
            CraftingRecipe recipe = entry.getValue();

            // Skip base block recipes — they drop themselves at 1x
            if (BlockRecipeRegistry.isBaseBlockRecipe(recipe.getId())) {
                continue;
            }

            BlockType bt = BlockType.getAssetMap().getAssetMap().get(blockTypeId);
            if (bt == null) continue;

            BlockGathering gathering = bt.getGathering();
            if (gathering == null) {
                skipped++;
                continue;
            }

            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length == 0) {
                skipped++;
                continue;
            }

            // Resolve the first input to a droppable item ID
            String resolvedItemId = null;
            int inputQty = 0;
            for (MaterialQuantity mq : inputs) {
                if (mq == null) continue;
                resolvedItemId = resolveInputItemId(mq);
                if (resolvedItemId != null) {
                    inputQty = mq.getQuantity(); // already 12x scaled
                    break;
                }
            }

            if (resolvedItemId == null) {
                log("SKIP " + blockTypeId + " (" + recipe.getId() + "): could not resolve any input");
                skipped++;
                continue;
            }

            MaterialQuantity primaryOut = recipe.getPrimaryOutput();
            int outputQty = (primaryOut != null && primaryOut.getQuantity() > 0)
                    ? primaryOut.getQuantity() : 1;
            int dropQty = Math.max(1, inputQty / outputQty);

            // Preserve the original gatherType and quality so the block
            // remains breakable with the same tool requirements
            BlockBreakingDropType existing = gathering.getBreaking();
            String gatherType = existing != null ? existing.getGatherType() : null;
            int quality = existing != null ? existing.getQuality() : 0;

            BlockBreakingDropType newBreaking = new BlockBreakingDropType(
                    gatherType, quality, dropQty, resolvedItemId, null);

            try {
                breakingField.set(gathering, newBreaking);
                modified++;
            } catch (IllegalAccessException e) {
                log("ERROR setting breaking for " + blockTypeId + ": " + e.getMessage());
            }
        }

        log("Set ingredient drops for " + modified + " recipe blocks ("
                + skipped + " skipped)");
    }

    @Nullable
    private static String resolveInputItemId(@Nonnull MaterialQuantity input) {
        String itemId = input.getItemId();
        if (itemId != null && !"Empty".equals(itemId)) {
            Item item = Item.getAssetMap().getAsset(itemId);
            return item != null ? itemId : null;
        }

        String resId = input.getResourceTypeId();
        if (resId != null) {
            return resolveByBlockGroup(resId);
        }

        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static String resolveByBlockGroup(@Nonnull String resId) {
        int underscoreIdx = resId.indexOf('_');
        String groupName = underscoreIdx >= 0
                ? "FullBlocks" + resId.substring(underscoreIdx)
                : "FullBlocks_" + resId;

        DefaultAssetMap<String, BlockGroup> blockGroupMap =
                (DefaultAssetMap<String, BlockGroup>) AssetRegistry.getAssetStore(BlockGroup.class).getAssetMap();
        BlockGroup group = blockGroupMap.getAsset(groupName);
        if (group == null) return null;

        // Try blockId directly as an item ID
        for (int i = 0; i < group.size(); i++) {
            String blockId = group.get(i);
            Item item = Item.getAssetMap().getAsset(blockId);
            if (item != null) return blockId;
        }

        // Search all items for one whose blockId matches a group entry
        for (int i = 0; i < group.size(); i++) {
            String blockId = group.get(i);
            for (Map.Entry<String, Item> e : Item.getAssetMap().getAssetMap().entrySet()) {
                if (e.getValue() != null && blockId.equals(e.getValue().getBlockId())) {
                    return e.getKey();
                }
            }
        }

        return null;
    }
}
