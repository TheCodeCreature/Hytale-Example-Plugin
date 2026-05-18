package com.CodeCreature.scaling;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.scaling.DropScaler;
import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.CodeCreature.scaling.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario-based tests modeled from exact in-game Hytale asset data
 * (dumped from a live server 2026-04-13). Each block, item, and recipe
 * uses the real field values so that registry classification and modifier
 * behavior match production.
 *
 * <h3>Real asset summary</h3>
 * <table>
 *   <tr><th>BlockType</th><th>breaking.itemId</th><th>gatherType</th><th>quality</th></tr>
 *   <tr><td>Rock_Shale</td><td>Rock_Shale_Cobble</td><td>Rocks</td><td>0</td></tr>
 *   <tr><td>Rock_Shale_Cobble</td><td>Rock_Shale_Cobble</td><td>Rocks</td><td>1</td></tr>
 *   <tr><td>Rail</td><td>null</td><td>SoftBlocks</td><td>0</td></tr>
 * </table>
 *
 * <h3>Key recipes</h3>
 * <ul>
 *   <li>Rock_Shale_Recipe_Generated_0: 2x Rock_Shale (resourceType) -> 1x Rock_Shale
 *       -- bench=Processing (NOT StructuralCrafting)</li>
 *   <li>Salvage_Rock_Shale_Cobble: 1x Rock_Shale_Cobble -> 1x Rubble_Shale
 *       -- Salvage prefix, always filtered</li>
 *   <li>Rail_Recipe_Generated_0: 2x Wood_Planks (resourceType) + 1x Ingredient_Bar_Iron
 *       -> 4x Rail -- bench=Crafting (NOT StructuralCrafting)</li>
 * </ul>
 */
class DropBehaviorScenarioTest {

    // Block types (exact game configs)
    private BlockType rockShale;
    private BlockType rockShaleCobble;
    private BlockType rail;

    @BeforeEach
    void setUp() {
        // -- Block types (real data from server dump 2026-04-13) ---

        // Rock_Shale: world-generated stone, drops cobble when broken
        rockShale = blockType("Rock_Shale",
                gathering(new BlockBreakingDropType("Rocks", 0, 1, "Rock_Shale_Cobble", null),
                        null, null, null));

        // Rock_Shale_Cobble: cobble form, drops itself when broken (quality=1)
        rockShaleCobble = blockType("Rock_Shale_Cobble",
                gathering(new BlockBreakingDropType("Rocks", 1, 1, "Rock_Shale_Cobble", null),
                        null, null, null));

        // Rail: placeable rail block -- breaking.itemId is null in the real game
        rail = blockType("Rail",
                gathering(new BlockBreakingDropType("SoftBlocks", 0, 1, null, null),
                        null, null, null));

        Map<String, BlockType> blockTypes = new HashMap<>();
        blockTypes.put("Rock_Shale", rockShale);
        blockTypes.put("Rock_Shale_Cobble", rockShaleCobble);
        blockTypes.put("Rail", rail);

        // -- Items (exact game configs) ---------------------------

        Map<String, Item> items = new HashMap<>();
        items.put("Rock_Shale",
                item("Rock_Shale", "Rock_Shale", true, 100));
        items.put("Rock_Shale_Cobble",
                item("Rock_Shale_Cobble", "Rock_Shale_Cobble", true, 100));
        items.put("Rail",
                item("Rail", "Rail", true, 100));
        items.put("Ingredient_Bar_Iron",
                item("Ingredient_Bar_Iron", null, false, 100));
        items.put("Rubble_Shale",
                item("Rubble_Shale", null, false, 100));

        // -- Recipes (exact game configs) -------------------------

        Map<String, CraftingRecipe> recipes = new HashMap<>();

        // Rock_Shale_Recipe_Generated_0:
        //   input: resourceTypeId=Rock_Shale qty=2
        //   output: Rock_Shale qty=1
        //   bench: Processing (NOT StructuralCrafting)
        recipes.put("Rock_Shale_Recipe_Generated_0",
                recipe("Rock_Shale_Recipe_Generated_0",
                        new MaterialQuantity[]{materialQtyResource("Rock_Shale", 2)},
                        materialQty("Rock_Shale", 1),
                        BenchType.Processing));

        // Salvage_Rock_Shale_Cobble:
        //   input: Rock_Shale_Cobble qty=1
        //   output: Rubble_Shale qty=1
        //   bench: Processing  (also has Salvage prefix -> always filtered)
        recipes.put("Salvage_Rock_Shale_Cobble",
                recipe("Salvage_Rock_Shale_Cobble",
                        new MaterialQuantity[]{materialQty("Rock_Shale_Cobble", 1)},
                        materialQty("Rubble_Shale", 1),
                        BenchType.Processing));

        // Rail_Recipe_Generated_0:
        //   input: resourceTypeId=Wood_Planks qty=2, itemId=Ingredient_Bar_Iron qty=1
        //   output: Rail qty=4
        //   bench: Crafting (Workbench — not a registered bench)
        recipes.put("Rail_Recipe_Generated_0",
                recipe("Rail_Recipe_Generated_0",
                        new MaterialQuantity[]{
                                materialQtyResource("Wood_Planks", 2),
                                materialQty("Ingredient_Bar_Iron", 1)
                        },
                        materialQty("Rail", 4),
                        BenchType.Crafting, "Workbench"));

        // -- Install everything and run real init() logic ---------

        installBlockTypes(blockTypes);
        installItems(items);
        installRecipes(recipes);
        installDropLists(Map.of());

        NaturalResourceRegistry.init();
        RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
        BenchRecipeRegistries.init();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void applyFullPipeline() {
        DropScaler.applyModifications();
    }

    // -- Test 1: World Rock_Shale -> 12 Rock_Shale_Cobble ---------

    @Test
    void worldRockShaleDrops12RockShaleCobble() {
        applyFullPipeline();

        var breaking = readGatheringBreaking(rockShale.getGathering());
        assertEquals("Rock_Shale_Cobble", readBreakingItemId(breaking),
                "World Rock_Shale should drop Rock_Shale_Cobble");
        assertEquals(12, readBreakingQuantity(breaking),
                "World Rock_Shale should drop quantity 12");
    }

    // -- Test 2: World Rock_Shale_Cobble -> 12 Rock_Shale_Cobble --

    @Test
    void worldRockShaleCobbleDrops12RockShaleCobble() {
        applyFullPipeline();

        var breaking = readGatheringBreaking(rockShaleCobble.getGathering());
        assertEquals("Rock_Shale_Cobble", readBreakingItemId(breaking),
                "World Rock_Shale_Cobble should drop Rock_Shale_Cobble");
        assertEquals(12, readBreakingQuantity(breaking),
                "World Rock_Shale_Cobble should drop quantity 12");
    }

    // -- Test 3: Player-placed Rock_Shale_Cobble -> PlacementCostScaler enforces 12x cost

    @Test
    void playerPlacedRockShaleCobbleNoUseDefaultDropFlag() {
        applyFullPipeline();

        assertFalse(readUseDefaultDropWhenPlaced(rockShaleCobble.getGathering()),
                "Rock_Shale_Cobble should NOT have useDefaultDropWhenPlaced "
                        + "(placement cost enforced by PlacementCostScaler at runtime)");
    }

    // -- Test 4: World Rail -> 1 Rail -----------------------------

    @Test
    void worldRailDrops1Rail() {
        applyFullPipeline();

        // Rail's breaking.itemId is null in the real game -- the engine
        // falls back to blockType.getItem() which returns Rail at qty 1.
        // Verify that no modifier has replaced Rail's breaking config
        // with recipe ingredients.
        var breaking = readGatheringBreaking(rail.getGathering());
        String itemId = readBreakingItemId(breaking);
        int qty = readBreakingQuantity(breaking);

        // Either itemId is still null (engine fallback -> 1x Rail)
        // or it has been set to "Rail" at qty 1
        boolean correctDrop = itemId == null
                || ("Rail".equals(itemId) && qty == 1);

        assertFalse("Ingredient_Bar_Iron".equals(itemId),
                "World Rail must NOT drop recipe ingredients -- "
                        + "actual itemId=" + itemId + " qty=" + qty);
        assertTrue(correctDrop,
                "World Rail should drop 1x Rail (via engine fallback or explicit config) -- "
                        + "actual itemId=" + itemId + " qty=" + qty);
    }

    // -- Test 5: Player-placed Rail -> 1 Rail ---------------------

    @Test
    void playerPlacedRailDrops1Rail() {
        applyFullPipeline();

        // Rail has a Crafting-bench recipe (Workbench, not a registered bench), so it
        // should NOT be in BenchRecipeRegistries and should keep its vanilla
        // config.  Verify no modifier has changed the drop to ingredients.
        var breaking = readGatheringBreaking(rail.getGathering());
        String itemId = readBreakingItemId(breaking);
        int qty = readBreakingQuantity(breaking);

        boolean correctDrop = itemId == null
                || ("Rail".equals(itemId) && qty == 1);

        assertFalse("Ingredient_Bar_Iron".equals(itemId),
                "Player-placed Rail must NOT drop recipe ingredients -- "
                        + "actual itemId=" + itemId + " qty=" + qty);
        assertTrue(correctDrop,
                "Player-placed Rail should drop 1x Rail -- "
                        + "actual itemId=" + itemId + " qty=" + qty);
    }
}
