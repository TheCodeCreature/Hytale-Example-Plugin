package com.CodeCreature.scaling;

import com.hypixel.hytale.protocol.BenchType;
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

import static com.CodeCreature.scaling.AssetTestHelper.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds a complete set of test data modeled after real Hytale asset patterns.
 * Contains natural blocks (stone, logs, dirt, bushes/leaves), recipe blocks
 * (planks, slabs, rails, doors), and all supporting items, recipes, and drop lists.
 *
 * Call {@link #install()} to populate all asset stores and registries.
 * Call {@link AssetTestHelper#cleanup()} when done.
 */
public final class TestDataSet {

    // ── Natural block types ──
    public final BlockBreakingDropType rockBreaking;
    public final BlockType rockStone;

    public final BlockBreakingDropType logBreaking;
    public final BlockType woodLogOak;

    public final SoftBlockDropType dirtSoft;
    public final BlockType dirt;

    public final SoftBlockDropType sandSoft;
    public final PhysicsDropType sandPhysics;
    public final BlockType sand;

    public final SoftBlockDropType bushSoft;        // dropList-based, contains Plant_Fiber
    public final BlockType bushBerry;

    public final HarvestingDropType leavesHarvest;  // direct itemId = Plant_Fiber
    public final BlockType leavesOak;

    // ── Recipe block types ──
    public final BlockBreakingDropType planksBreaking;
    public final BlockType woodPlanksOak;

    public final BlockBreakingDropType slabBreaking;
    public final BlockType woodSlabOak;

    public final BlockBreakingDropType railBreaking;
    public final BlockType railIron;

    public final BlockBreakingDropType doorBreaking;
    public final BlockType doorWoodOak;

    // ── Items ──
    public final Item itemRockStone;
    public final Item itemWoodLogOak;
    public final Item itemDirt;
    public final Item itemSand;
    public final Item itemPlantFiber;
    public final Item itemBerry;
    public final Item itemWoodPlanksOak;
    public final Item itemWoodSlabOak;
    public final Item itemRailIron;
    public final Item itemDoorWoodOak;
    public final Item itemMetalIngotIron;

    // ── Recipes ──
    public final CraftingRecipe recipePlanksOak;   // base: 1x Wood_Log_Oak → 2x Wood_Planks_Oak
    public final CraftingRecipe recipeSlabOak;     // non-base: 1x Wood_Planks_Oak → 2x Wood_Slab_Oak
    public final CraftingRecipe recipeRailIron;    // non-base: 2x Metal_Ingot_Iron → 1x Rail_Iron
    public final CraftingRecipe recipeDoorWood;    // non-base: 2x Wood_Planks_Oak → 1x Door_Wood_Oak
    public final CraftingRecipe recipeThatch;      // non-base: 4x Plant_Fiber → 1x Thatch_Block
    public final CraftingRecipe salvageSlab;       // Salvage prefix, should be filtered
    public final CraftingRecipe recipeIngotIron;   // Processing bench, should be filtered

    // ── Extra recipe blocks (for ingredient tests) ──
    public final BlockBreakingDropType thatchBreaking;
    public final BlockType thatchBlock;
    public final Item itemThatchBlock;

    // ── Multi-ingredient recipe block (furniture bench style) ──
    public final BlockBreakingDropType furnitureBedBreaking;
    public final BlockType furnitureBed;
    public final Item itemFurnitureBed;
    public final Item itemIngredientFibre;
    public final Item itemClothWoolRed;
    public final CraftingRecipe recipeFurnitureBed; // non-base: 3x Wood_Planks_Oak + 4x Ingredient_Fibre + 2x Cloth_Wool_Red → 1x Furniture_Bed

    // ── Kweebec Bed: uses ResourceTypeId "Wood_All" instead of direct ItemId ──
    public final BlockBreakingDropType kweebecBedBreaking;
    public final BlockType kweebecBed;
    public final Item itemKweebecBed;
    public final CraftingRecipe recipeKweebecBed; // non-base: 3x Wood_All (resourceTypeId) + 4x Ingredient_Fibre → 1x Furniture_Kweebec_Bed

    // ── Hardwood Fence: single ResourceTypeId input "Wood_Hardwood" (Builders bench) ──
    public final BlockBreakingDropType fenceHardwoodBreaking;
    public final BlockType fenceHardwood;
    public final Item itemFenceHardwood;
    public final CraftingRecipe recipeFenceHardwood; // non-base: 1x Wood_Hardwood (resourceTypeId) → 2x Wood_Hardwood_Fence

    // ── Drop lists ──
    public final ItemDrop bushPlantFiberDrop;
    public final ItemDrop bushBerryDrop;
    public final ItemDropList dropListBush;

    // ── Maps for installation ──
    public final Map<String, BlockType> blockTypes = new HashMap<>();
    public final Map<String, Item> items = new HashMap<>();
    public final Map<String, CraftingRecipe> recipes = new HashMap<>();
    public final Map<String, ItemDropList> dropLists = new HashMap<>();

    // ── Registry sets ──
    public final Set<String> naturalBlockIds = new HashSet<>();
    public final Set<String> naturalItemIds = new HashSet<>();
    public final Map<String, CraftingRecipe> buildersRecipesByBlockType = new HashMap<>();
    public final Map<String, CraftingRecipe> buildersRecipesById = new HashMap<>();
    public final Map<String, CraftingRecipe> furnitureBenchRecipesByBlockType = new HashMap<>();
    public final Map<String, CraftingRecipe> furnitureBenchRecipesById = new HashMap<>();
    public final Set<String> baseBlockRecipeIds = new HashSet<>();

    public TestDataSet() {
        // ────────────────────────────────────────────────
        //  Natural blocks
        // ────────────────────────────────────────────────

        // Rock_Stone: breaking with gatherType, quantity=1, direct itemId
        rockBreaking = new BlockBreakingDropType("Rocks", 0, 1, "Rock_Stone", null);
        rockStone = blockType("Rock_Stone",
                gathering(rockBreaking, null, null, null));

        // Wood_Log_Oak: breaking with gatherType, quantity=1, direct itemId
        logBreaking = new BlockBreakingDropType("Woods", 0, 1, "Wood_Log_Oak", null);
        woodLogOak = blockType("Wood_Log_Oak",
                gathering(logBreaking, null, null, null));

        // Dirt: soft only (no breaking config)
        dirtSoft = new SoftBlockDropType("Dirt", null, true);
        dirt = blockType("Dirt", gathering(null, dirtSoft, null, null));

        // Sand: soft + physics (both direct itemId)
        sandSoft = new SoftBlockDropType("Sand", null, true);
        sandPhysics = new PhysicsDropType("Sand", null);
        sand = blockType("Sand", gathering(null, sandSoft, null, sandPhysics));

        // Bush_Berry: soft with dropListId containing Plant_Fiber and Berry
        bushSoft = new SoftBlockDropType(null, "DropList_Bush", true);
        bushBerry = blockType("Bush_Berry", gathering(null, bushSoft, null, null));

        // Leaves_Oak: harvest with direct itemId=Plant_Fiber (an ingredient)
        leavesHarvest = new HarvestingDropType("Plant_Fiber", null);
        leavesOak = blockType("Leaves_Oak",
                gathering(null, null, leavesHarvest, null));

        // ────────────────────────────────────────────────
        //  Recipe blocks (StructuralCrafting)
        // ────────────────────────────────────────────────

        // Wood_Planks_Oak: base block, breaking with gatherType
        planksBreaking = new BlockBreakingDropType("Woods", 0, 1, "Wood_Planks_Oak", null);
        woodPlanksOak = blockType("Wood_Planks_Oak",
                gathering(planksBreaking, null, null, null));

        // Wood_Slab_Oak: non-base, breaking with gatherType
        slabBreaking = new BlockBreakingDropType("Woods", 0, 1, "Wood_Slab_Oak", null);
        woodSlabOak = blockType("Wood_Slab_Oak",
                gathering(slabBreaking, null, null, null));

        // Rail_Iron: non-base, breaking with gatherType
        railBreaking = new BlockBreakingDropType("Metals", 1, 1, "Rail_Iron", null);
        railIron = blockType("Rail_Iron",
                gathering(railBreaking, null, null, null));

        // Door_Wood_Oak: non-base, breaking with gatherType
        doorBreaking = new BlockBreakingDropType("Woods", 0, 1, "Door_Wood_Oak", null);
        doorWoodOak = blockType("Door_Wood_Oak",
                gathering(doorBreaking, null, null, null));

        // ────────────────────────────────────────────────
        //  Items
        // ────────────────────────────────────────────────

        itemRockStone = item("Rock_Stone", "Rock_Stone", true, 100,
                resourceType("Rock"));
        itemWoodLogOak = item("Wood_Log_Oak", "Wood_Log_Oak", true, 100,
                resourceType("Wood_All"), resourceType("Wood_Hardwood"),
                resourceType("Wood_Trunk"), resourceType("Fuel"));
        itemDirt = item("Dirt", "Dirt", true, 100);
        itemSand = item("Sand", "Sand", true, 100);
        itemPlantFiber = item("Plant_Fiber", null, false, 100);
        itemBerry = item("Berry", null, false, 64);
        itemWoodPlanksOak = item("Wood_Planks_Oak", "Wood_Planks_Oak", true, 100,
                resourceType("Wood_Planks"), resourceType("Fuel"));
        itemWoodSlabOak = item("Wood_Slab_Oak", "Wood_Slab_Oak", true, 100);
        itemRailIron = item("Rail_Iron", "Rail_Iron", true, 100);
        itemDoorWoodOak = item("Door_Wood_Oak", "Door_Wood_Oak", true, 100);
        itemMetalIngotIron = item("Metal_Ingot_Iron", null, false, 100);

        items.put("Wood_Blackwood_Planks", item("Wood_Blackwood_Planks", "Wood_Blackwood_Planks", true, 100,
                resourceType("Wood_Planks")));
        items.put("Wood_Hardwood_Planks", item("Wood_Hardwood_Planks", "Wood_Hardwood_Planks", true, 100,
                "Wood_Hardwood_Planks",
                resourceType("Wood_Planks"), resourceType("Wood_Hardwood")));
        items.put("Wood_Hardwood_Decorative", item("Wood_Hardwood_Decorative", "Wood_Hardwood_Decorative", true, 100,
                "Wood_Hardwood_Planks",
                resourceType("Wood_Hardwood")));
        items.put("Wood_Hardwood_Ornate", item("Wood_Hardwood_Ornate", "Wood_Hardwood_Ornate", true, 100,
                "Wood_Hardwood_Planks",
                resourceType("Wood_Hardwood")));

        // ────────────────────────────────────────────────
        //  Recipes
        // ────────────────────────────────────────────────

        // Base block: 1x Wood_Log_Oak → 2x Wood_Planks_Oak
        recipePlanksOak = recipe("Planks_Oak",
                new MaterialQuantity[]{materialQty("Wood_Log_Oak", 1)},
                materialQty("Wood_Planks_Oak", 2),
                BenchType.StructuralCrafting, "Builders");

        // Non-base: 1x Wood_Planks_Oak → 2x Wood_Slab_Oak
        recipeSlabOak = recipe("Slab_Oak",
                new MaterialQuantity[]{materialQty("Wood_Planks_Oak", 1)},
                materialQty("Wood_Slab_Oak", 2),
                BenchType.StructuralCrafting, "Builders");

        // Non-base: 2x Metal_Ingot_Iron → 1x Rail_Iron
        recipeRailIron = recipe("Rail_Iron",
                new MaterialQuantity[]{materialQty("Metal_Ingot_Iron", 2)},
                materialQty("Rail_Iron", 1),
                BenchType.StructuralCrafting, "Builders");

        // Non-base: 2x Wood_Planks_Oak → 1x Door_Wood_Oak
        recipeDoorWood = recipe("Door_Wood",
                new MaterialQuantity[]{materialQty("Wood_Planks_Oak", 2)},
                materialQty("Door_Wood_Oak", 1),
                BenchType.StructuralCrafting, "Builders");

        // Non-base: 4x Plant_Fiber → 1x Thatch_Block (makes Plant_Fiber an ingredient)
        recipeThatch = recipe("Thatch_Block",
                new MaterialQuantity[]{materialQty("Plant_Fiber", 4)},
                materialQty("Thatch_Block", 1),
                BenchType.StructuralCrafting, "Builders");

        // Thatch block setup
        thatchBreaking = new BlockBreakingDropType("Woods", 0, 1, "Thatch_Block", null);
        thatchBlock = blockType("Thatch_Block",
                gathering(thatchBreaking, null, null, null));
        itemThatchBlock = item("Thatch_Block", "Thatch_Block", true, 100);

        // Multi-ingredient: 3x Wood_Planks_Oak + 4x Ingredient_Fibre + 2x Cloth_Wool_Red → 1x Furniture_Bed
        furnitureBedBreaking = new BlockBreakingDropType("Woods", 0, 1, "Furniture_Bed", null);
        furnitureBed = blockType("Furniture_Bed",
                gathering(furnitureBedBreaking, null, null, null));
        itemFurnitureBed = item("Furniture_Bed", "Furniture_Bed", true, 1);
        itemIngredientFibre = item("Ingredient_Fibre", null, false, 100);
        itemClothWoolRed = item("Cloth_Wool_Red", null, false, 100);
        recipeFurnitureBed = recipe("Furniture_Bed",
                new MaterialQuantity[]{
                        materialQty("Wood_Planks_Oak", 3),
                        materialQty("Ingredient_Fibre", 4),
                        materialQty("Cloth_Wool_Red", 2)
                },
                materialQty("Furniture_Bed", 1),
                BenchType.StructuralCrafting, "Builders");

        // Kweebec Bed: uses ResourceTypeId "Wood_All" — matches real game data
        kweebecBedBreaking = new BlockBreakingDropType("Woods", 0, 1, "Furniture_Kweebec_Bed", null);
        kweebecBed = blockType("Furniture_Kweebec_Bed",
                gathering(kweebecBedBreaking, null, null, null));
        itemKweebecBed = item("Furniture_Kweebec_Bed", "Furniture_Kweebec_Bed", true, 1);
        recipeKweebecBed = recipe("Furniture_Kweebec_Bed",
                new MaterialQuantity[]{
                        materialQtyResource("Wood_All", 3),
                        materialQty("Ingredient_Fibre", 4)
                },
                materialQty("Furniture_Kweebec_Bed", 1),
                BenchType.Crafting, "Furniture_Bench");

        // Hardwood Fence: single ResourceTypeId input — Builders bench
        fenceHardwoodBreaking = new BlockBreakingDropType("Woods", 0, 1, "Wood_Hardwood_Fence", null);
        fenceHardwood = blockType("Wood_Hardwood_Fence",
                gathering(fenceHardwoodBreaking, null, null, null));
        itemFenceHardwood = item("Wood_Hardwood_Fence", "Wood_Hardwood_Fence", true, 100);
        recipeFenceHardwood = recipe("Wood_Hardwood_Fence",
                new MaterialQuantity[]{materialQtyResource("Wood_Hardwood", 1)},
                materialQty("Wood_Hardwood_Fence", 2),
                BenchType.StructuralCrafting, "Builders");

        // Salvage recipe (should be filtered out)
        salvageSlab = recipe("Salvage_Slab_Oak",
                new MaterialQuantity[]{materialQty("Wood_Slab_Oak", 1)},
                materialQty("Wood_Planks_Oak", 1),
                BenchType.StructuralCrafting, "Builders");

        // Processing recipe (wrong bench type, should be filtered out)
        recipeIngotIron = recipe("Ingot_Iron",
                new MaterialQuantity[]{materialQty("Ore_Iron", 1)},
                materialQty("Metal_Ingot_Iron", 1),
                BenchType.Processing);

        // ────────────────────────────────────────────────
        //  Drop lists
        // ────────────────────────────────────────────────

        bushPlantFiberDrop = new ItemDrop("Plant_Fiber", null, 1, 2);
        bushBerryDrop = new ItemDrop("Berry", null, 1, 1);
        // DropList_Bush uses the first drop; a real list would have both
        // but SingleItemDropContainer only holds one. We'll test the single case.
        dropListBush = new ItemDropList("DropList_Bush",
                new SingleItemDropContainer(bushPlantFiberDrop, 100.0));

        // ────────────────────────────────────────────────
        //  Populate maps
        // ────────────────────────────────────────────────

        // Block types
        blockTypes.put("Rock_Stone", rockStone);
        blockTypes.put("Wood_Log_Oak", woodLogOak);
        blockTypes.put("Dirt", dirt);
        blockTypes.put("Sand", sand);
        blockTypes.put("Bush_Berry", bushBerry);
        blockTypes.put("Leaves_Oak", leavesOak);
        blockTypes.put("Wood_Planks_Oak", woodPlanksOak);
        blockTypes.put("Wood_Slab_Oak", woodSlabOak);
        blockTypes.put("Rail_Iron", railIron);
        blockTypes.put("Door_Wood_Oak", doorWoodOak);
        blockTypes.put("Thatch_Block", thatchBlock);
        blockTypes.put("Furniture_Bed", furnitureBed);
        blockTypes.put("Furniture_Kweebec_Bed", kweebecBed);
        blockTypes.put("Wood_Hardwood_Fence", fenceHardwood);

        // Items
        items.put("Rock_Stone", itemRockStone);
        items.put("Wood_Log_Oak", itemWoodLogOak);
        items.put("Dirt", itemDirt);
        items.put("Sand", itemSand);
        items.put("Plant_Fiber", itemPlantFiber);
        items.put("Berry", itemBerry);
        items.put("Wood_Planks_Oak", itemWoodPlanksOak);
        items.put("Wood_Slab_Oak", itemWoodSlabOak);
        items.put("Rail_Iron", itemRailIron);
        items.put("Door_Wood_Oak", itemDoorWoodOak);
        items.put("Metal_Ingot_Iron", itemMetalIngotIron);
        items.put("Thatch_Block", itemThatchBlock);
        items.put("Furniture_Bed", itemFurnitureBed);
        items.put("Furniture_Kweebec_Bed", itemKweebecBed);
        items.put("Ingredient_Fibre", itemIngredientFibre);
        items.put("Cloth_Wool_Red", itemClothWoolRed);
        items.put("Wood_Hardwood_Fence", itemFenceHardwood);

        // Recipes (all of them, including salvage/processing for registry tests)
        recipes.put("Planks_Oak", recipePlanksOak);
        recipes.put("Slab_Oak", recipeSlabOak);
        recipes.put("Rail_Iron", recipeRailIron);
        recipes.put("Door_Wood", recipeDoorWood);
        recipes.put("Thatch_Block", recipeThatch);
        recipes.put("Salvage_Slab_Oak", salvageSlab);
        recipes.put("Ingot_Iron", recipeIngotIron);
        recipes.put("Furniture_Bed", recipeFurnitureBed);
        recipes.put("Furniture_Kweebec_Bed", recipeKweebecBed);
        recipes.put("Wood_Hardwood_Fence", recipeFenceHardwood);

        // Drop lists
        dropLists.put("DropList_Bush", dropListBush);

        // ────────────────────────────────────────────────
        //  Registry data (what init() would compute)
        // ────────────────────────────────────────────────

        // Natural blocks: those with no non-Salvage crafting recipe output
        naturalBlockIds.add("Rock_Stone");
        naturalBlockIds.add("Wood_Log_Oak");
        naturalBlockIds.add("Dirt");
        naturalBlockIds.add("Sand");
        naturalBlockIds.add("Bush_Berry");
        naturalBlockIds.add("Leaves_Oak");

        // Natural items: all items dropped by natural blocks
        naturalItemIds.add("Rock_Stone");
        naturalItemIds.add("Wood_Log_Oak");
        naturalItemIds.add("Dirt");
        naturalItemIds.add("Sand");
        naturalItemIds.add("Plant_Fiber");
        naturalItemIds.add("Berry");

        // Builders recipes
        buildersRecipesByBlockType.put("Wood_Planks_Oak", recipePlanksOak);
        buildersRecipesByBlockType.put("Wood_Slab_Oak", recipeSlabOak);
        buildersRecipesByBlockType.put("Rail_Iron", recipeRailIron);
        buildersRecipesByBlockType.put("Door_Wood_Oak", recipeDoorWood);
        buildersRecipesByBlockType.put("Thatch_Block", recipeThatch);
        buildersRecipesByBlockType.put("Furniture_Bed", recipeFurnitureBed);
        buildersRecipesByBlockType.put("Wood_Hardwood_Fence", recipeFenceHardwood);

        // Furniture_Bench recipes
        furnitureBenchRecipesByBlockType.put("Furniture_Kweebec_Bed", recipeKweebecBed);

        buildersRecipesById.put("Planks_Oak", recipePlanksOak);
        buildersRecipesById.put("Slab_Oak", recipeSlabOak);
        buildersRecipesById.put("Rail_Iron", recipeRailIron);
        buildersRecipesById.put("Door_Wood", recipeDoorWood);
        buildersRecipesById.put("Thatch_Block", recipeThatch);
        buildersRecipesById.put("Furniture_Bed", recipeFurnitureBed);
        buildersRecipesById.put("Wood_Hardwood_Fence", recipeFenceHardwood);
        furnitureBenchRecipesById.put("Furniture_Kweebec_Bed", recipeKweebecBed);

        // Base block: Planks_Oak (all inputs natural: Wood_Log_Oak ∈ naturalItemIds)
        baseBlockRecipeIds.add("Planks_Oak");
    }

    /** Installs all asset stores and pre-populates registries. */
    public void install() {
        installBlockTypes(blockTypes);
        installItems(items);
        installRecipes(recipes);
        installDropLists(dropLists);
        setNaturalRegistry(naturalBlockIds, naturalItemIds);
        setBenchRecipeRegistries(
                Map.of("Builders", buildersRecipesByBlockType,
                       "Furniture_Bench", furnitureBenchRecipesByBlockType),
                Map.of("Builders", buildersRecipesById,
                       "Furniture_Bench", furnitureBenchRecipesById),
                Map.of("Builders", baseBlockRecipeIds,
                       "Furniture_Bench", Set.of()));
    }
}
