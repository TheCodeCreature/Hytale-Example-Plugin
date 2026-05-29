package com.CodeCreature.scaling;

import com.CodeCreature.crafting.RecipeTreeResolver;
import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.registry.BenchRecipeRegistry;
import com.CodeCreature.registry.RecipeFilterRegistry;
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
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

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

    /**
     * Full pipeline: initialize registries from loaded assets, then apply
     * all asset modifications in a single pass.
     */
    public static void apply() {
        NaturalResourceRegistry.init();
        RecipeTierClassifier.init();
        RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
        BenchRecipeRegistries.init();
        applyModifications();
    }

    /**
     * Applies all asset modifications using pre-populated registries.
     * Package-private so tests can inject registry state before calling.
     */
    static void applyModifications() {
        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;
        AssetFieldAccessor f = AssetFieldAccessor.INSTANCE;

        // ── Phase 1: Scale all crafting costs ────────────────────────
        int recipesScaled = scaleCraftingCosts(f, multiplier);

        // ── Phase 2: Classify blocks by bench category ───────────────
        BenchBlockClassifier classifier = new BenchBlockClassifier();
        classifier.classify();

        // ── Phase 2b: Build raw-cost cache ─────────────────────────
        // Must run after Phase 1 (scaled costs) and before Phase 3
        // (processors use resolveRecipeToRaw for raw-material drops).
        RecipeTreeResolver.init();

        // ── Phase 3: Process blocks ──────────────────────────────────

        // Phase 3a: Recipe blocks — parallel per category
        List<BenchCategoryProcessor> processors = List.of(
                new BuildersProcessor(),
                new FurnitureProcessor(),
                new OverlapProcessor()
        );

        List<BenchCategoryProcessor.ProcessResult> categoryResults = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BenchCategoryProcessor.ProcessResult>> futures = new ArrayList<>();
            for (BenchCategoryProcessor proc : processors) {
                Set<String> blocks = classifier.getBlocksByCategory(proc.category());
                if (!blocks.isEmpty()) {
                    futures.add(executor.submit(() -> proc.process(blocks, f)));
                }
            }
            for (Future<BenchCategoryProcessor.ProcessResult> future : futures) {
                categoryResults.add(future.get());
            }
        } catch (Exception e) {
            DebugLogger.log(SCALING, Level.INFO, "[DropScaler] ERROR in parallel category processing: " + e.getMessage());
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

        // Phase 3b: Natural blocks — sequential (shared-instance tracking)
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
            if (btId.startsWith("*") || btId.startsWith("Block_Placeholder")) continue;

            boolean isNatural = NaturalResourceRegistry.isNaturalBlock(btId);
            boolean hasRecipe = classifier.getCategory(btId) != null;

            if (isNatural && !hasRecipe) {
                if (processNaturalBlock(bt, f, multiplier,
                        processedConfigs, processedDrops, processedDropListIds,
                        syntheticDropLists)) {
                    naturalModified++;
                } else {
                    naturalSkipped++;
                }
            }
        }

        // ── Phase 4: Register synthetic drop lists ───────────────────
        if (!syntheticDropLists.isEmpty()) {
            try {
                ItemDropList.getAssetStore().loadAssets(
                        "Plugin:NaturalIngredients", syntheticDropLists);
            } catch (Exception e) {
                DebugLogger.log(SCALING, Level.INFO, "[DropScaler] ERROR registering synthetic drop lists: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        // ── Phase 5: Scale natural item stack sizes ──────────────────
        int stacksBoosted = scaleStackSizes(f, multiplier);

        DebugLogger.log(SCALING, Level.INFO, "[DropScaler] Pipeline complete: " + recipesScaled + " recipes scaled, "
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
        int totalSkipped = 0;
        int duplicates = 0;
        Set<String> processedRecipeIds = new HashSet<>();
        for (BenchRecipeRegistry reg : BenchRecipeRegistries.getAllRegistries()) {
        for (var entry : reg.getAllRecipesById().entrySet()) {
            String recipeId = entry.getKey();
            if (!processedRecipeIds.add(recipeId)) { duplicates++; continue; }
            CraftingRecipe recipe = entry.getValue();

            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length == 0) continue;

            MaterialQuantity[] scaled = new MaterialQuantity[inputs.length];
            int skipped = 0;
            for (int i = 0; i < inputs.length; i++) {
                MaterialQuantity mq = inputs[i];
                if (mq == null) {
                    scaled[i] = null;
                    continue;
                }
                if (!RecipeTierClassifier.isRawInput(mq)) {
                    scaled[i] = mq; // keep vanilla quantity
                    skipped++;
                    continue;
                }
                scaled[i] = mq.clone(mq.getQuantity() * multiplier);
            }
            totalSkipped += skipped;

            try {
                f.recipeInput.set(recipe, scaled);
                modified++;
            } catch (IllegalAccessException e) {
                DebugLogger.log(SCALING, Level.INFO, "[DropScaler] ERROR scaling recipe " + recipeId + ": " + e.getMessage());
            }
        }
        }
        DebugLogger.log(SCALING, Level.INFO, "[DropScaler] Crafting costs: " + modified + " recipes scaled, "
                + totalSkipped + " inputs skipped (crafted intermediates), "
                + duplicates + " duplicates skipped");
        return modified;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Phase 3b: Natural Block Processing
    // ═════════════════════════════════════════════════════════════════

    private static boolean processNaturalBlock(
            BlockType bt, AssetFieldAccessor f, int multiplier,
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
                    scaleAllDropListItems(breaking.getDropListId(), multiplier,
                            processedDrops, processedDropListIds, f);
                } else if (breaking.getQuantity() > 0) {
                    BlockBreakingDropType newBreaking = new BlockBreakingDropType(
                            breaking.getGatherType(), breaking.getQuality(),
                            breaking.getQuantity() * multiplier,
                            breaking.getItemId(), null);
                    f.gatheringBreaking.set(gathering, newBreaking);
                }
            }

            // ── Scale soft/harvest/physics drops ──
            SoftBlockDropType soft = gathering.getSoft();
            if (soft != null && !processedConfigs.contains(soft)) {
                scaleDropConfig(soft, soft.getItemId(), soft.getDropListId(),
                        multiplier, f.softItemId, f.softDropListId,
                        processedDrops, processedDropListIds, f, syntheticDropLists);
                processedConfigs.add(soft);
            }

            HarvestingDropType harvest = gathering.getHarvest();
            if (harvest != null && !processedConfigs.contains(harvest)) {
                scaleDropConfig(harvest, harvest.getItemId(), harvest.getDropListId(),
                        multiplier, f.harvestItemId, f.harvestDropListId,
                        processedDrops, processedDropListIds, f, syntheticDropLists);
                processedConfigs.add(harvest);
            }

            PhysicsDropType physics = gathering.getPhysics();
            if (physics != null && !processedConfigs.contains(physics)) {
                scaleDropConfig(physics, physics.getItemId(), physics.getDropListId(),
                        multiplier, f.physicsItemId, f.physicsDropListId,
                        processedDrops, processedDropListIds, f, syntheticDropLists);
                processedConfigs.add(physics);
            }

            // Placement cost enforcement is handled at runtime by PlacementCostScaler,
            // so useDefaultDropWhenPlaced is intentionally NOT set here.

            return true;
        } catch (Exception e) {
            DebugLogger.log(SCALING, Level.INFO, "[DropScaler] ERROR processing natural block " + bt.getId() + ": " + e.getMessage());
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Drop scaling helpers
    // ═════════════════════════════════════════════════════════════════

    private static void scaleDropConfig(
            Object config, String itemId, String dropListId,
            int multiplier, Field itemIdField, Field dropListIdField,
            Set<ItemDrop> processedDrops, Set<String> processedDropListIds,
            AssetFieldAccessor f, List<ItemDropList> syntheticDropLists) {

        // Case A: direct itemId — swap to synthetic drop list with scaled quantity
        if (itemId != null && !"Empty".equals(itemId)) {
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
                DebugLogger.log(SCALING, Level.INFO, "[DropScaler] ERROR swapping item " + itemId + ": " + e.getMessage());
            }
            return;
        }

        // Case B: dropListId — scale ALL ItemDrop quantities in the list
        if (dropListId != null) {
            scaleAllDropListItems(dropListId, multiplier,
                    processedDrops, processedDropListIds, f);
        }
    }

    private static void scaleAllDropListItems(
            String dropListId, int multiplier,
            Set<ItemDrop> processedDrops, Set<String> processedDropListIds,
            AssetFieldAccessor f) {

        if (processedDropListIds.contains(dropListId)) return;

        ItemDropList list = ItemDropList.getAssetMap().getAsset(dropListId);
        if (list == null || list.getContainer() == null) return;

        List<ItemDrop> allDrops = list.getContainer().getAllDrops(new ArrayList<>());
        for (ItemDrop drop : allDrops) {
            if (drop == null || processedDrops.contains(drop)) continue;
            try {
                int curMin = f.dropQuantityMin.getInt(drop);
                int curMax = f.dropQuantityMax.getInt(drop);
                f.dropQuantityMin.setInt(drop, curMin * multiplier);
                f.dropQuantityMax.setInt(drop, curMax * multiplier);
                processedDrops.add(drop);
            } catch (IllegalAccessException e) {
                String dItemId = drop.getItemId();
                DebugLogger.log(SCALING, Level.INFO, "[DropScaler] ERROR scaling drop " + dItemId
                        + " in " + dropListId + ": " + e.getMessage());
            }
        }
        processedDropListIds.add(dropListId);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Phase 5: Stack Size Scaling
    // ═════════════════════════════════════════════════════════════════

    private static int scaleStackSizes(AssetFieldAccessor f, int multiplier) {
        int boosted = 0;
        for (var entry : Item.getAssetMap().getAssetMap().entrySet()) {
            Item item = entry.getValue();
            if (item == null) continue;
            try {
                int current = f.itemMaxStack.getInt(item);
                if (current > 1) {
                    f.itemMaxStack.setInt(item, current * multiplier);
                    boosted++;
                }
            } catch (IllegalAccessException e) {
                DebugLogger.log(SCALING, Level.INFO, "[DropScaler] ERROR scaling stack size for " + entry.getKey() + ": " + e.getMessage());
            }
        }
        return boosted;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Utility
    // ═════════════════════════════════════════════════════════════════

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

    static BlockGathering createEmptyGathering() throws Exception {
        Constructor<BlockGathering> ctor = BlockGathering.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    static SoftBlockDropType createEmptySoftDrop() throws Exception {
        Constructor<SoftBlockDropType> ctor = SoftBlockDropType.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }
}
