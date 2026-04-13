package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Multiplies drops of natural blocks that produce items used as ingredients
 * in StructuralCrafting recipes. Handles both:
 *   - Direct itemId drops (on soft/harvest configs with no breaking config)
 *   - Drop list (dropListId) drops — resolves the list, scales matching ItemDrop quantities
 *
 * Must be called after {@link NaturalResourceRegistry#init()} and
 * {@link BlockRecipeRegistry#init()}.
 */
public final class IngredientDropModifier {

    private static final String DROPLIST_PREFIX = "Plugin_NaturalIngredient_";

    private IngredientDropModifier() {}

    private static void log(String msg) {
        System.out.println("[IngredientDrop] " + msg);
    }

    public static void apply() {
        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;

        // 1. Collect all ingredient item IDs from StructuralCrafting recipes
        Set<String> ingredientItemIds = collectRecipeIngredientIds();
        log("Found " + ingredientItemIds.size() + " unique ingredient items in structural recipes");

        // 2. Prepare reflection fields
        Field softItemIdField, softDropListIdField;
        Field harvestItemIdField, harvestDropListIdField;
        Field physicsItemIdField, physicsDropListIdField;
        Field dropQuantityMin, dropQuantityMax;
        try {
            softItemIdField = SoftBlockDropType.class.getDeclaredField("itemId");
            softItemIdField.setAccessible(true);
            softDropListIdField = SoftBlockDropType.class.getDeclaredField("dropListId");
            softDropListIdField.setAccessible(true);
            harvestItemIdField = HarvestingDropType.class.getDeclaredField("itemId");
            harvestItemIdField.setAccessible(true);
            harvestDropListIdField = HarvestingDropType.class.getDeclaredField("dropListId");
            harvestDropListIdField.setAccessible(true);
            physicsItemIdField = PhysicsDropType.class.getDeclaredField("itemId");
            physicsItemIdField.setAccessible(true);
            physicsDropListIdField = PhysicsDropType.class.getDeclaredField("dropListId");
            physicsDropListIdField.setAccessible(true);
            dropQuantityMin = ItemDrop.class.getDeclaredField("quantityMin");
            dropQuantityMin.setAccessible(true);
            dropQuantityMax = ItemDrop.class.getDeclaredField("quantityMax");
            dropQuantityMax.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find required field: " + e.getMessage());
            return;
        }

        // Track configs and ItemDrop objects already processed (shared instances)
        Set<Object> processedConfigs = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ItemDrop> processedDrops = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> processedDropListIds = new HashSet<>();

        int directSwapped = 0;
        int dropListsModified = 0;
        List<ItemDropList> newDropLists = new ArrayList<>();    

        // 3. Scan natural blocks for ingredient drops
        for (String blockTypeId : NaturalResourceRegistry.getNaturalBlockTypes()) {
            BlockType bt = BlockType.getAssetMap().getAssetMap().get(blockTypeId);
            if (bt == null) continue;

            // Skip blocks that have a crafting recipe — handled by RecipeDropModifier
            if (BlockRecipeRegistry.hasRecipe(blockTypeId)) continue;

            BlockGathering gathering = bt.getGathering();
            if (gathering == null) continue;

            // Process breaking config drop lists (NaturalDropModifier skips these)
            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking != null && breaking.getDropListId() != null) {
                String dlId = breaking.getDropListId();
                if (!processedDropListIds.contains(dlId)) {
                    ItemDropList list = ItemDropList.getAssetMap().getAsset(dlId);
                    if (list != null && list.getContainer() != null) {
                        List<ItemDrop> allDrops = list.getContainer().getAllDrops(new ArrayList<>());
                        for (ItemDrop drop : allDrops) {
                            if (drop == null || processedDrops.contains(drop)) continue;
                            String dItemId = drop.getItemId();
                            if (dItemId != null && ingredientItemIds.contains(dItemId)) {
                                try {
                                    int curMin = dropQuantityMin.getInt(drop);
                                    int curMax = dropQuantityMax.getInt(drop);
                                    dropQuantityMin.setInt(drop, curMin * multiplier);
                                    dropQuantityMax.setInt(drop, curMax * multiplier);
                                    processedDrops.add(drop);
                                    dropListsModified++;
                                } catch (IllegalAccessException e) {
                                    log("ERROR scaling breaking drop list item " + dItemId + ": " + e.getMessage());
                                }
                            }
                        }
                        processedDropListIds.add(dlId);
                    }
                }
            }

            SoftBlockDropType soft = gathering.getSoft();
            if (soft != null && !processedConfigs.contains(soft)) {
                int result = processDropConfig(soft, soft.getItemId(), soft.getDropListId(),
                        ingredientItemIds, multiplier,
                        softItemIdField, softDropListIdField,
                        dropQuantityMin, dropQuantityMax,
                        processedDrops, newDropLists);
                if (result > 0) {
                    processedConfigs.add(soft);
                    if (result == 1) directSwapped++;
                    else dropListsModified++;
                }
            }

            HarvestingDropType harvest = gathering.getHarvest();
            if (harvest != null && !processedConfigs.contains(harvest)) {
                int result = processDropConfig(harvest, harvest.getItemId(), harvest.getDropListId(),
                        ingredientItemIds, multiplier,
                        harvestItemIdField, harvestDropListIdField,
                        dropQuantityMin, dropQuantityMax,
                        processedDrops, newDropLists);
                if (result > 0) {
                    processedConfigs.add(harvest);
                    if (result == 1) directSwapped++;
                    else dropListsModified++;
                }
            }

            // Process physics config (used when blocks break via physics cascade)
            PhysicsDropType physics = gathering.getPhysics();
            if (physics != null && !processedConfigs.contains(physics)) {
                int result = processDropConfig(physics, physics.getItemId(), physics.getDropListId(),
                        ingredientItemIds, multiplier,
                        physicsItemIdField, physicsDropListIdField,
                        dropQuantityMin, dropQuantityMax,
                        processedDrops, newDropLists);
                if (result > 0) {
                    processedConfigs.add(physics);
                    if (result == 1) directSwapped++;
                    else dropListsModified++;
                }
            }
        }

        // 4. Register any new drop lists we created for direct itemId swaps
        if (!newDropLists.isEmpty()) {
            try {
                ItemDropList.getAssetStore().loadAssets("Plugin:NaturalIngredients", newDropLists);
            } catch (Exception e) {
                log("ERROR registering drop lists: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        log("Modified " + directSwapped + " direct-item configs, "
                + dropListsModified + " drop-list configs ("
                + newDropLists.size() + " new drop lists registered)");
    }

    /**
     * Processes a single soft or harvest config.
     * Returns: 0 = no change, 1 = direct itemId swapped, 2 = drop list modified
     */
    private static int processDropConfig(
            Object config, String itemId, String dropListId,
            Set<String> ingredientItemIds, int multiplier,
            Field itemIdField, Field dropListIdField,
            Field qMinField, Field qMaxField,
            Set<ItemDrop> processedDrops,
            List<ItemDropList> newDropLists) {

        // Case A: direct itemId that is an ingredient
        if (itemId != null && !"Empty".equals(itemId) && ingredientItemIds.contains(itemId)) {
            String newDlId = DROPLIST_PREFIX + itemId;
            // Only create the drop list once per item
            boolean alreadyCreated = newDropLists.stream().anyMatch(dl -> newDlId.equals(dl.getId()));
            if (!alreadyCreated) {
                ItemDrop drop = new ItemDrop(itemId, null, multiplier, multiplier);
                SingleItemDropContainer container = new SingleItemDropContainer(drop, 100.0);
                newDropLists.add(new ItemDropList(newDlId, container));
            }
            try {
                itemIdField.set(config, null);
                dropListIdField.set(config, newDlId);
                return 1;
            } catch (IllegalAccessException e) {
                log("ERROR swapping direct item " + itemId + ": " + e.getMessage());
                return 0;
            }
        }

        // Case B: dropListId — resolve and scale matching ItemDrop quantities
        if (dropListId != null) {
            ItemDropList list = ItemDropList.getAssetMap().getAsset(dropListId);
            if (list == null || list.getContainer() == null) return 0;

            List<ItemDrop> allDrops = list.getContainer().getAllDrops(new ArrayList<>());
            boolean modified = false;
            for (ItemDrop drop : allDrops) {
                if (drop == null) continue;
                if (processedDrops.contains(drop)) continue;
                String dItemId = drop.getItemId();
                if (dItemId != null && ingredientItemIds.contains(dItemId)) {
                    try {
                        int curMin = qMinField.getInt(drop);
                        int curMax = qMaxField.getInt(drop);
                        qMinField.setInt(drop, curMin * multiplier);
                        qMaxField.setInt(drop, curMax * multiplier);
                        processedDrops.add(drop);
                        modified = true;
                    } catch (IllegalAccessException e) {
                        log("ERROR scaling drop list item " + dItemId + ": " + e.getMessage());
                    }
                }
            }
            return modified ? 2 : 0;
        }

        return 0;
    }

    /**
     * Collects all item IDs used as inputs across StructuralCrafting recipes,
     * resolving resourceTypeId references via BlockGroup lookup.
     */
    private static Set<String> collectRecipeIngredientIds() {
        Set<String> ids = new HashSet<>();
        for (CraftingRecipe recipe : BlockRecipeRegistry.getAllRecipesById().values()) {
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null) continue;
            for (MaterialQuantity mq : inputs) {
                if (mq == null) continue;
                String resolved = BlockRecipeRegistry.resolveInputItemId(mq);
                if (resolved != null) {
                    ids.add(resolved);
                }
            }
        }
        return ids;
    }
}
