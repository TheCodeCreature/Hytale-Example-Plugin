package com.CodeCreature.scaling;

import com.hypixel.hytale.protocol.BenchRequirement;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    /** Maps block-item IDs to their gatherable drop form (e.g. Rock_Shale → Rock_Shale_Cobble). */
    private static Map<String, String> gatherableFormMap = Collections.emptyMap();

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
        // from a Crafting or StructuralCrafting bench.  Processing recipes
        // (e.g. 2× Rock_Shale → 1× Rock_Shale at the stonecutter) are
        // refinement recipes and should NOT disqualify a block from being natural.
        Set<String> craftableBlockIds = new HashSet<>();
        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null || recipe.getId().startsWith("Salvage")) continue;
            if (!isCraftingBench(recipe)) continue;
            if (recipe.getPrimaryOutput() == null || recipe.getPrimaryOutput().getItemId() == null) continue;
            Item item = Item.getAssetMap().getAsset(recipe.getPrimaryOutput().getItemId());
            if (item != null) {
                // Use getBlockId() instead of hasBlockType() — some items
                // (e.g. rails, doors) reference an external block definition
                // and won't have hasBlockType=true, but still have a blockId.
                String blockId = item.getBlockId();
                if (blockId != null && !blockId.isEmpty()) {
                    craftableBlockIds.add(blockId);
                }
            }
        }

        // Iterate all block types; those not in craftableBlockIds are natural
        for (var entry : BlockType.getAssetMap().getAssetMap().entrySet()) {
            BlockType bt = entry.getValue();
            if (bt == null) continue;
            String btId = bt.getId();
            if ("Empty".equals(btId) || "Unknown".equals(btId)) continue;
            if (btId.startsWith("*") || btId.startsWith("Block_Placeholder")) continue;
            if (craftableBlockIds.contains(btId)) continue;

            blockTypes.add(btId);
            collectDropItems(bt, itemIds);
        }

        // Build gatherable-form map: block-item → breaking drop for natural blocks
        Map<String, String> gfMap = new HashMap<>();
        for (String btId : blockTypes) {
            BlockType bt = BlockType.getAssetMap().getAsset(btId);
            if (bt == null) continue;
            Item blockItem = bt.getItem();
            if (blockItem == null) continue;
            BlockGathering gathering = bt.getGathering();
            if (gathering == null) continue;
            var breaking = gathering.getBreaking();
            if (breaking == null) continue;
            String breakingDrop = breaking.getItemId();
            if (breakingDrop != null && !breakingDrop.isEmpty()
                    && !breakingDrop.equals(blockItem.getId())) {
                gfMap.put(blockItem.getId(), breakingDrop);
            }
        }

        naturalBlockTypes = Collections.unmodifiableSet(blockTypes);
        naturalItemIds = Collections.unmodifiableSet(itemIds);
        gatherableFormMap = Collections.unmodifiableMap(gfMap);
        log("Initialized: " + blockTypes.size() + " natural block types, "
                + itemIds.size() + " natural items, "
                + gfMap.size() + " gatherable-form mappings");
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

    /**
     * If the given item is a block-item for a natural block whose gathering
     * drop is different (e.g. Rock_Shale → Rock_Shale_Cobble), returns the
     * gatherable drop form. Otherwise returns the input unchanged.
     * Single-level resolution only — no recursive chains.
     */
    @Nonnull
    public static String resolveToGatherableForm(@Nonnull String itemId) {
        return gatherableFormMap.getOrDefault(itemId, itemId);
    }

    /**
     * Bench IDs whose recipes disqualify a block from being natural.
     * Must match the bench IDs passed to {@link BenchRecipeRegistries#init}.
     */
    private static final Set<String> CRAFTING_BENCH_IDS = Set.of(
            "Builders", "Furniture_Bench", "Workbench", "Fieldcraft");

    /**
     * Returns true if the recipe requires one of the known crafting benches.
     * Processing recipes (stonecutter/refinery) are excluded because
     * they are resource-refinement recipes that don't make the output block
     * a "crafted" block.
     */
    private static boolean isCraftingBench(@Nonnull CraftingRecipe recipe) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return false;
        for (BenchRequirement req : reqs) {
            if (req != null && req.id != null && CRAFTING_BENCH_IDS.contains(req.id)) {
                return true;
            }
        }
        return false;
    }
}
