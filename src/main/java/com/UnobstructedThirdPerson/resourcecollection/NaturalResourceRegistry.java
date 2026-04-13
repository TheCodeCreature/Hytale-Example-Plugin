package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Central registry of natural resource block types and item IDs.
 * Built once after assets load; queried by drop listeners, stack size
 * modifiers, and any future system that needs to know if something is
 * a natural resource.
 *
 * A block type is "natural" if it has NO non-Salvage crafting recipe.
 * A natural resource item is any item that a natural block can drop
 * (via its gathering config or fallback block-item).
 */
public final class NaturalResourceRegistry {

    private static Set<String> naturalBlockTypes = Collections.emptySet();
    private static Set<String> naturalItemIds = Collections.emptySet();

    private NaturalResourceRegistry() {}

    private static void log(String msg) {
        System.out.println("[NaturalRegistry] " + msg);
    }

    /**
     * Builds the registry. Must be called after assets are loaded
     * (e.g., from a LoadAssetEvent handler).
     */
    public static void init() {
        Set<String> blockTypes = new HashSet<>();
        Set<String> itemIds = new HashSet<>();

        // Build set of block type IDs that have a non-Salvage crafting recipe
        Set<String> craftableBlockIds = new HashSet<>();
        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null || recipe.getId().startsWith("Salvage")) continue;
            if (recipe.getPrimaryOutput() == null || recipe.getPrimaryOutput().getItemId() == null) continue;
            Item item = Item.getAssetMap().getAsset(recipe.getPrimaryOutput().getItemId());
            if (item != null && item.hasBlockType()) {
                craftableBlockIds.add(item.getBlockId());
            }
        }

        // Iterate all block types; those not in craftableBlockIds are natural
        for (var entry : BlockType.getAssetMap().getAssetMap().entrySet()) {
            BlockType bt = entry.getValue();
            if (bt == null) continue;
            String btId = bt.getId();
            if ("Empty".equals(btId) || "Unknown".equals(btId)) continue;
            if (craftableBlockIds.contains(btId)) continue;

            blockTypes.add(btId);

            // Collect all items this block can drop
            collectDropItems(bt, itemIds);
        }

        naturalBlockTypes = Collections.unmodifiableSet(blockTypes);
        naturalItemIds = Collections.unmodifiableSet(itemIds);
        log("Initialized: " + blockTypes.size() + " natural block types, "
                + itemIds.size() + " natural resource items");
    }

    /**
     * Collects all item IDs that the given block type can drop via any
     * gathering path, plus its fallback block-item.
     */
    private static void collectDropItems(@Nonnull BlockType blockType, @Nonnull Set<String> out) {
        // Fallback: block's own item
        Item blockItem = blockType.getItem();
        if (blockItem != null) {
            out.add(blockItem.getId());
        }

        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) return;

        collectFromDropType(blockType, gathering.getBreaking(), out);
        collectFromDropType(blockType, gathering.getSoft(), out);
        collectFromDropType(blockType, gathering.getHarvest(), out);
        collectFromDropType(blockType, gathering.getPhysics(), out);

        // Tool-specific drops
        var toolData = gathering.getToolData();
        if (toolData != null) {
            for (BlockGathering.BlockToolData td : toolData.values()) {
                if (td == null) continue;
                addIfPresent(td.getItemId(), out);
                resolveDropList(blockType, td.getDropListId(), out);
            }
        }
    }

    private static void collectFromDropType(@Nonnull BlockType bt,
                                            Object dropType, @Nonnull Set<String> out) {
        if (dropType == null) return;

        String itemId = null;
        String dropListId = null;
        int quantity = 1;

        if (dropType instanceof BlockBreakingDropType d) {
            itemId = d.getItemId();
            dropListId = d.getDropListId();
            quantity = d.getQuantity();
        } else if (dropType instanceof SoftBlockDropType d) {
            itemId = d.getItemId();
            dropListId = d.getDropListId();
        } else if (dropType instanceof HarvestingDropType d) {
            itemId = d.getItemId();
            dropListId = d.getDropListId();
        } else if (dropType instanceof PhysicsDropType d) {
            itemId = d.getItemId();
            dropListId = d.getDropListId();
        }

        addIfPresent(itemId, out);
        resolveDropList(bt, dropListId, out);

        // Also resolve via getDrops to catch fallback logic
        if (itemId != null || dropListId != null) {
            try {
                List<ItemStack> drops = BlockHarvestUtils.getDrops(bt, quantity, itemId, dropListId);
                for (ItemStack stack : drops) {
                    addIfPresent(stack.getItemId(), out);
                }
            } catch (Exception ignored) {
                // Drop list may reference missing assets during init
            }
        }
    }

    private static void resolveDropList(@Nonnull BlockType bt, String dropListId, @Nonnull Set<String> out) {
        if (dropListId == null) return;
        try {
            List<ItemStack> drops = BlockHarvestUtils.getDrops(bt, 1, null, dropListId);
            for (ItemStack stack : drops) {
                addIfPresent(stack.getItemId(), out);
            }
        } catch (Exception ignored) {}
    }

    private static void addIfPresent(String itemId, Set<String> out) {
        if (itemId != null && !itemId.isEmpty() && !"Empty".equals(itemId)) {
            out.add(itemId);
        }
    }

    /** Returns true if the block type ID is a natural (non-craftable) block. */
    public static boolean isNaturalBlock(@Nonnull String blockTypeId) {
        return naturalBlockTypes.contains(blockTypeId);
    }

    /** Returns true if the item ID is dropped by any natural block. */
    public static boolean isNaturalItem(@Nonnull String itemId) {
        return naturalItemIds.contains(itemId);
    }

    /** Returns the full set of natural block type IDs (read-only). */
    public static Set<String> getNaturalBlockTypes() {
        return naturalBlockTypes;
    }

    /** Returns the full set of natural resource item IDs (read-only). */
    public static Set<String> getNaturalItemIds() {
        return naturalItemIds;
    }
}
