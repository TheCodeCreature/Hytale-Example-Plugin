package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Single-pass asset modifier for the 12x resource economy.
 *
 * <p>All block types are iterated exactly once. Reflection is centralized
 * in {@link AssetFieldAccessor}. Execution order is explicit in a single
 * method rather than spread across an implicit call sequence.
 *
 * <p>Usage from the plugin:
 * <pre>
 *   // In onAssetsLoaded:
 *   DropScaler.apply();
 * </pre>
 */
public final class DropScaler {

    private static final String DROPLIST_PREFIX = "Plugin_NaturalIngredient_";

    private DropScaler() {}

    private static void log(String msg) {
        System.out.println("[DropScaler] " + msg);
    }

    /**
     * Full pipeline: initialize registries from loaded assets, then apply
     * all asset modifications in a single pass.
     */
    public static void apply() {
        NaturalResourceRegistry.init();
        BenchRecipeRegistries.init("Builders", "Furniture_Bench");
        applyModifications();
    }

    /**
     * Applies all asset modifications using pre-populated registries.
     * Package-private so tests can inject registry state before calling.
     */
    static void applyModifications() {
        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;
        AssetFieldAccessor f = new AssetFieldAccessor();

        // ── Phase 1: Scale crafting costs for non-base recipes ───────
        int recipesScaled = scaleCraftingCosts(f, multiplier);

        // ── Phase 2: Collect ingredient item IDs (bench-aware) ───────
        Set<String> ingredientItemIds = collectIngredientItemIds();

        // ── Phase 3: Classify blocks by bench category ───────────────
        BenchBlockClassifier classifier = new BenchBlockClassifier();
        classifier.classify();

        // ── Phase 4: Process blocks ──────────────────────────────────

        // Phase 4a: Recipe blocks — parallel per category
        List<BenchCategoryProcessor> processors = List.of(
                new BuildersProcessor(),
                new FurnitureProcessor(),
                new OverlapProcessor()
        );

        List<BenchCategoryProcessor.ProcessResult> categoryResults = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BenchCategoryProcessor.ProcessResult>> futures = new ArrayList<>();
            for (BenchCategoryProcessor proc : processors) {
                Set<String> blocks = classifier.getNonBaseBlocksByCategory(proc.category());
                if (!blocks.isEmpty()) {
                    futures.add(executor.submit(() -> proc.process(blocks, f, ingredientItemIds)));
                }
            }
            for (Future<BenchCategoryProcessor.ProcessResult> future : futures) {
                categoryResults.add(future.get());
            }
        } catch (Exception e) {
            log("ERROR in parallel category processing: " + e.getMessage());
        }

        // Merge results from category processors
        List<ItemDropList> syntheticDropLists = new ArrayList<>();
        int recipeModified = 0;
        int recipeSkipped = 0;
        for (BenchCategoryProcessor.ProcessResult r : categoryResults) {
            syntheticDropLists.addAll(r.syntheticDropLists());
            recipeModified += r.modified();
            recipeSkipped += r.skipped();
        }

        // Phase 4b: Natural blocks — sequential (shared-instance tracking)
        Set<Object> processedConfigs = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ItemDrop> processedDrops = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> processedDropListIds = new HashSet<>();

        int naturalModified = 0;
        int naturalSkipped = 0;

        for (var entry : BlockType.getAssetMap().getAssetMap().entrySet()) {
            BlockType bt = entry.getValue();
            if (bt == null) continue;
            String btId = bt.getId();
            if ("Empty".equals(btId) || "Unknown".equals(btId)) continue;

            boolean isNatural = NaturalResourceRegistry.isNaturalBlock(btId);
            boolean hasRecipe = classifier.getCategory(btId) != null;

            if (isNatural && !hasRecipe) {
                if (processNaturalBlock(bt, f, multiplier, ingredientItemIds,
                        processedConfigs, processedDrops, processedDropListIds,
                        syntheticDropLists)) {
                    naturalModified++;
                } else {
                    naturalSkipped++;
                }
            }
        }

        // ── Phase 5: Register synthetic drop lists ───────────────────
        if (!syntheticDropLists.isEmpty()) {
            try {
                ItemDropList.getAssetStore().loadAssets(
                        "Plugin:NaturalIngredients", syntheticDropLists);
            } catch (Exception e) {
                log("ERROR registering synthetic drop lists: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        // ── Phase 6: Scale natural item stack sizes ──────────────────
        int stacksBoosted = scaleStackSizes(f, multiplier);

        log("Pipeline complete: " + recipesScaled + " recipes scaled, "
                + naturalModified + " natural blocks (" + naturalSkipped + " skipped), "
                + recipeModified + " recipe blocks (" + recipeSkipped + " skipped), "
                + syntheticDropLists.size() + " synthetic drop lists, "
                + stacksBoosted + " stack sizes boosted");
    }

    // ═════════════════════════════════════════════════════════════════
    //  Phase 1: Crafting Cost Scaling
    // ═════════════════════════════════════════════════════════════════

    private static int scaleCraftingCosts(AssetFieldAccessor f, int multiplier) {
        int modified = 0;
        for (BenchRecipeRegistry reg : BenchRecipeRegistries.getAllRegistries()) {
        for (var entry : reg.getAllRecipesById().entrySet()) {
            String recipeId = entry.getKey();
            CraftingRecipe recipe = entry.getValue();

            if (reg.isBaseBlockRecipe(recipeId)) continue;

            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length == 0) continue;

            MaterialQuantity[] scaled = new MaterialQuantity[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                MaterialQuantity mq = inputs[i];
                scaled[i] = mq == null ? null : mq.clone(mq.getQuantity() * multiplier);
            }

            try {
                f.recipeInput.set(recipe, scaled);
                modified++;
            } catch (IllegalAccessException e) {
                log("ERROR scaling recipe " + recipeId + ": " + e.getMessage());
            }
        }
        }
        return modified;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Phase 4a: Natural Block Processing
    // ═════════════════════════════════════════════════════════════════

    private static boolean processNaturalBlock(
            BlockType bt, AssetFieldAccessor f, int multiplier,
            Set<String> ingredientItemIds,
            Set<Object> processedConfigs, Set<ItemDrop> processedDrops,
            Set<String> processedDropListIds, List<ItemDropList> syntheticDropLists) {

        BlockGathering originalGathering = bt.getGathering();
        if (originalGathering == null) return false;

        try {
            // Clone gathering to avoid shared-instance contamination
            BlockGathering gathering = cloneGathering(originalGathering);
            f.blockTypeGathering.set(bt, gathering);

            // ── Scale breaking drops ──
            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking != null) {
                if (breaking.getDropListId() != null) {
                    // Drop list: scale ingredient items inside the list
                    scaleDropListIngredients(breaking.getDropListId(),
                            ingredientItemIds, multiplier,
                            processedDrops, processedDropListIds, f);
                } else if (breaking.getQuantity() > 0) {
                    // Direct itemId (or null fallback): multiply quantity
                    BlockBreakingDropType newBreaking = new BlockBreakingDropType(
                            breaking.getGatherType(), breaking.getQuality(),
                            breaking.getQuantity() * multiplier,
                            breaking.getItemId(), null);
                    f.gatheringBreaking.set(gathering, newBreaking);
                }
            }

            // ── Scale ingredient drops in soft/harvest/physics ──
            SoftBlockDropType soft = gathering.getSoft();
            if (soft != null && !processedConfigs.contains(soft)) {
                processIngredientConfig(soft, soft.getItemId(), soft.getDropListId(),
                        ingredientItemIds, multiplier,
                        f.softItemId, f.softDropListId,
                        processedDrops, processedDropListIds, f, syntheticDropLists);
                processedConfigs.add(soft);
            }

            HarvestingDropType harvest = gathering.getHarvest();
            if (harvest != null && !processedConfigs.contains(harvest)) {
                processIngredientConfig(harvest, harvest.getItemId(), harvest.getDropListId(),
                        ingredientItemIds, multiplier,
                        f.harvestItemId, f.harvestDropListId,
                        processedDrops, processedDropListIds, f, syntheticDropLists);
                processedConfigs.add(harvest);
            }

            PhysicsDropType physics = gathering.getPhysics();
            if (physics != null && !processedConfigs.contains(physics)) {
                processIngredientConfig(physics, physics.getItemId(), physics.getDropListId(),
                        ingredientItemIds, multiplier,
                        f.physicsItemId, f.physicsDropListId,
                        processedDrops, processedDropListIds, f, syntheticDropLists);
                processedConfigs.add(physics);
            }

            // Placement cost enforcement is handled at runtime by PlacementCostScaler,
            // so useDefaultDropWhenPlaced is intentionally NOT set here.

            return true;
        } catch (Exception e) {
            log("ERROR processing natural block " + bt.getId() + ": " + e.getMessage());
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Ingredient scaling helpers
    // ═════════════════════════════════════════════════════════════════

    private static void processIngredientConfig(
            Object config, String itemId, String dropListId,
            Set<String> ingredientItemIds, int multiplier,
            Field itemIdField, Field dropListIdField,
            Set<ItemDrop> processedDrops, Set<String> processedDropListIds,
            AssetFieldAccessor f, List<ItemDropList> syntheticDropLists) {

        // Case A: direct itemId that is a recipe ingredient — swap to synthetic drop list
        if (itemId != null && !"Empty".equals(itemId) && ingredientItemIds.contains(itemId)) {
            String newDlId = DROPLIST_PREFIX + itemId;
            boolean alreadyCreated = syntheticDropLists.stream()
                    .anyMatch(dl -> newDlId.equals(dl.getId()));
            if (!alreadyCreated) {
                ItemDrop drop = new ItemDrop(itemId, null, multiplier, multiplier);
                SingleItemDropContainer container = new SingleItemDropContainer(drop, 100.0);
                syntheticDropLists.add(new ItemDropList(newDlId, container));
            }
            try {
                itemIdField.set(config, null);
                dropListIdField.set(config, newDlId);
            } catch (IllegalAccessException e) {
                log("ERROR swapping ingredient item " + itemId + ": " + e.getMessage());
            }
            return;
        }

        // Case B: dropListId — scale matching ingredient ItemDrop quantities
        if (dropListId != null) {
            scaleDropListIngredients(dropListId, ingredientItemIds, multiplier,
                    processedDrops, processedDropListIds, f);
        }
    }

    private static void scaleDropListIngredients(
            String dropListId, Set<String> ingredientItemIds, int multiplier,
            Set<ItemDrop> processedDrops, Set<String> processedDropListIds,
            AssetFieldAccessor f) {

        if (processedDropListIds.contains(dropListId)) return;

        ItemDropList list = ItemDropList.getAssetMap().getAsset(dropListId);
        if (list == null || list.getContainer() == null) return;

        List<ItemDrop> allDrops = list.getContainer().getAllDrops(new ArrayList<>());
        for (ItemDrop drop : allDrops) {
            if (drop == null || processedDrops.contains(drop)) continue;
            String dItemId = drop.getItemId();
            if (dItemId != null && ingredientItemIds.contains(dItemId)) {
                try {
                    int curMin = f.dropQuantityMin.getInt(drop);
                    int curMax = f.dropQuantityMax.getInt(drop);
                    f.dropQuantityMin.setInt(drop, curMin * multiplier);
                    f.dropQuantityMax.setInt(drop, curMax * multiplier);
                    processedDrops.add(drop);
                } catch (IllegalAccessException e) {
                    log("ERROR scaling drop " + dItemId
                            + " in " + dropListId + ": " + e.getMessage());
                }
            }
        }
        processedDropListIds.add(dropListId);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Phase 6: Stack Size Scaling
    // ═════════════════════════════════════════════════════════════════

    private static int scaleStackSizes(AssetFieldAccessor f, int multiplier) {
        int boosted = 0;
        for (String itemId : NaturalResourceRegistry.getNaturalItemIds()) {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) continue;
            try {
                int current = f.itemMaxStack.getInt(item);
                if (current > 1) {
                    f.itemMaxStack.setInt(item, current * multiplier);
                    boosted++;
                }
            } catch (IllegalAccessException e) {
                log("ERROR scaling stack size for " + itemId + ": " + e.getMessage());
            }
        }
        return boosted;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Utility
    // ═════════════════════════════════════════════════════════════════

    private static Set<String> collectIngredientItemIds() {
        Set<String> ids = new HashSet<>();
        for (BenchRecipeRegistry reg : BenchRecipeRegistries.getAllRegistries()) {
            BenchCategory category = "Builders".equals(reg.getBenchId())
                    ? BenchCategory.BUILDERS_ONLY : BenchCategory.FURNITURE_ONLY;
            for (CraftingRecipe recipe : reg.getAllRecipesById().values()) {
                MaterialQuantity[] inputs = recipe.getInput();
                if (inputs == null) continue;
                for (MaterialQuantity mq : inputs) {
                    if (mq == null) continue;
                    String resolved = ResourceTypeResolver.resolveInputItemId(mq, category);
                    if (resolved != null) ids.add(resolved);
                }
            }
        }
        return ids;
    }

    static BlockGathering cloneGathering(BlockGathering original) throws Exception {
        Constructor<BlockGathering> ctor = BlockGathering.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        BlockGathering clone = ctor.newInstance();
        for (Field field : BlockGathering.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            field.set(clone, field.get(original));
        }
        return clone;
    }
}
