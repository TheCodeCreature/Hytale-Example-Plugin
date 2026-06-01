package com.CodeCreature.scaling;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.registry.BenchRegistry;
import com.CodeCreature.scaling.DropScaler;
import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.CodeCreature.scaling.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that expose the shared-instance bug in the modifier pipeline.
 *
 * <p>In the real game, Hytale's asset system uses JSON inheritance:
 * a child BlockType that doesn't override its parent's gathering config
 * shares the <b>same Java object instance</b> of {@link BlockBreakingDropType}
 * and {@link BlockGathering}. When {@link NaturalDropModifier} mutates a
 * shared {@code BlockBreakingDropType} for a natural block, every other
 * BlockType referencing that same instance silently inherits the 12x
 * quantity — including non-natural blocks like Rail.
 *
 * <p>Similarly, when {@link PlacedBlockDropModifier} sets
 * {@code useDefaultDropWhenPlaced=true} on a shared {@link BlockGathering},
 * it affects all blocks sharing that instance, not just the target block.
 *
 * <p>Our existing tests never catch this because each test block creates
 * its own separate instances. These tests simulate the real-game sharing
 * by assigning the <b>same object</b> to multiple BlockTypes.
 */
class SharedInstanceDropBugTest {

    @AfterEach
    void tearDown() {
        cleanup();
    }

    // -----------------------------------------------------------------
    //  Helper: install assets and run registries from scratch
    // -----------------------------------------------------------------

    private void installAndInit(Map<String, BlockType> blockTypes,
                                Map<String, Item> items,
                                Map<String, CraftingRecipe> recipes) {
        installBlockTypes(blockTypes);
        installItems(items);
        installRecipes(recipes);
        installDropLists(Map.of());
        BenchRegistry.init();
        NaturalResourceRegistry.init();
        RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
        BenchRecipeRegistries.init();
    }

    private void applyFullPipeline() {
        DropScaler.applyModifications();
    }

    // =================================================================
    //  Shared BlockBreakingDropType — the 12x contamination bug
    // =================================================================

    @Nested
    class SharedBreakingInstance {

        /**
         * Two blocks share one BlockBreakingDropType: one natural, one
         * recipe block. After the pipeline, the recipe block's quantity
         * should still be 1 because only natural blocks should be scaled.
         *
         * <p>EXPECTED: FAIL — NaturalDropModifier mutates the shared
         * instance, so the recipe block also becomes 12x.
         */
        @Test
        void recipeBlockSharingBreakingWithNaturalKeepsQuantity1() {
            // ONE shared instance — simulates asset inheritance
            BlockBreakingDropType sharedBreaking =
                    new BlockBreakingDropType("Rocks", 0, 1, "Cobble_Stone", null);

            BlockType naturalRock = blockType("Natural_Rock",
                    gathering(sharedBreaking, null, null, null));
            BlockType craftedWall = blockType("Crafted_Wall",
                    gathering(sharedBreaking, null, null, null));

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Natural_Rock", naturalRock);
            blockTypes.put("Crafted_Wall", craftedWall);

            Map<String, Item> items = new HashMap<>();
            items.put("Natural_Rock", item("Natural_Rock", "Natural_Rock", true, 100));
            items.put("Crafted_Wall", item("Crafted_Wall", "Crafted_Wall", true, 100));
            items.put("Cobble_Stone", item("Cobble_Stone", null, false, 100));

            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Crafted_Wall_Recipe", recipe("Crafted_Wall_Recipe",
                    new MaterialQuantity[]{materialQty("Cobble_Stone", 2)},
                    materialQty("Crafted_Wall", 1),
                    BenchType.StructuralCrafting, "Builders"));

            installAndInit(blockTypes, items, recipes);
            applyFullPipeline();

            // Natural_Rock is natural, Crafted_Wall has a recipe.
            // They share the same BlockBreakingDropType.
            // The recipe block's quantity should NOT have been modified.
            var breaking = readGatheringBreaking(craftedWall.getGathering());
            assertEquals(1, readBreakingQuantity(breaking),
                    "Recipe block sharing a breaking instance with a natural block "
                            + "should keep quantity=1, not inherit the 12x multiplier");
        }

        /**
         * Rail in the real game has breaking.itemId=null and gatherType=SoftBlocks.
         * If Rail shares its BlockBreakingDropType with ANY natural block that
         * also has a null-itemId SoftBlocks breaking config, Rail inherits 12x.
         *
         * The engine then calls getDrops(rail, 12, null, null) which falls back
         * to blockType.getItem() at quantity 12 — giving 12x Rail drops.
         *
         * <p>EXPECTED: FAIL — shared instance is mutated to 12.
         */
        @Test
        void railSharingBreakingWithNaturalBlockKeepsQuantity1() {
            // Shared null-itemId breaking — common with soft/decorative blocks
            BlockBreakingDropType sharedBreaking =
                    new BlockBreakingDropType("SoftBlocks", 0, 1, null, null);

            // A natural soft block (e.g., a plant or mushroom) shares
            // the same breaking instance with Rail via parent inheritance
            BlockType naturalPlant = blockType("Wild_Plant",
                    gathering(sharedBreaking, null, null, null));
            BlockType rail = blockType("Rail",
                    gathering(sharedBreaking, null, null, null));

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Wild_Plant", naturalPlant);
            blockTypes.put("Rail", rail);

            Map<String, Item> items = new HashMap<>();
            items.put("Wild_Plant", item("Wild_Plant", "Wild_Plant", true, 100));
            items.put("Rail", item("Rail", "Rail", true, 100));
            items.put("Ingredient_Bar_Iron", item("Ingredient_Bar_Iron", null, false, 100));

            // Rail has a Crafting-bench recipe (real game data)
            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Rail_Recipe_Generated_0", recipe("Rail_Recipe_Generated_0",
                    new MaterialQuantity[]{materialQty("Ingredient_Bar_Iron", 1)},
                    materialQty("Rail", 4),
                    BenchType.Crafting, "Workbench"));

            installAndInit(blockTypes, items, recipes);
            applyFullPipeline();

            // Rail is NOT natural (has a Crafting recipe).
            // But because it shares a breaking instance with Wild_Plant (natural),
            // the shared quantity was mutated to 12.
            // Engine: getDrops(rail, 12, null, null) → 12x Rail
            var breaking = readGatheringBreaking(rail.getGathering());
            assertEquals(1, readBreakingQuantity(breaking),
                    "Rail should drop 1x, but shared breaking instance "
                            + "was mutated to 12 by NaturalDropModifier via Wild_Plant");
        }

        /**
         * Even with separate items, the shared breaking instance means
         * the non-natural block drops 12x of its own item ID through the
         * engine's fallback path.
         *
         * <p>EXPECTED: FAIL
         */
        @Test
        void sharedBreakingWithExplicitItemIdContaminatesRecipeBlock() {
            // Shared breaking that drops "Rock_Shale_Cobble"
            BlockBreakingDropType sharedBreaking =
                    new BlockBreakingDropType("Rocks", 1, 1, "Rock_Shale_Cobble", null);

            // Rock_Shale_Cobble: natural (no recipe produces it)
            BlockType rockShaleCobble = blockType("Rock_Shale_Cobble",
                    gathering(sharedBreaking, null, null, null));
            // Rock_Shale_Cobble_Stairs: recipe block sharing the parent's breaking
            BlockType cobbleStairs = blockType("Rock_Shale_Cobble_Stairs",
                    gathering(sharedBreaking, null, null, null));

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Rock_Shale_Cobble", rockShaleCobble);
            blockTypes.put("Rock_Shale_Cobble_Stairs", cobbleStairs);

            Map<String, Item> items = new HashMap<>();
            items.put("Rock_Shale_Cobble",
                    item("Rock_Shale_Cobble", "Rock_Shale_Cobble", true, 100));
            items.put("Rock_Shale_Cobble_Stairs",
                    item("Rock_Shale_Cobble_Stairs", "Rock_Shale_Cobble_Stairs", true, 100));

            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Rock_Shale_Cobble_Stairs_Recipe_Generated_0",
                    recipe("Rock_Shale_Cobble_Stairs_Recipe_Generated_0",
                            new MaterialQuantity[]{materialQty("Rock_Shale_Cobble", 1)},
                            materialQty("Rock_Shale_Cobble_Stairs", 1),
                            BenchType.StructuralCrafting, "Builders"));

            installAndInit(blockTypes, items, recipes);
            applyFullPipeline();

            // Stairs is a recipe block — its quantity should be untouched
            var breaking = readGatheringBreaking(cobbleStairs.getGathering());
            assertEquals(1, readBreakingQuantity(breaking),
                    "Rock_Shale_Cobble_Stairs (recipe block) should keep quantity=1 "
                            + "but shared breaking was mutated by Rock_Shale_Cobble (natural)");
        }
    }

    // =================================================================
    //  Shared BlockGathering — useDefaultDropWhenPlaced contamination
    // =================================================================

    @Nested
    class SharedGatheringInstance {

        /**
         * If two blocks share the same BlockGathering instance and one
         * is natural, PlacedBlockDropModifier sets useDefaultDropWhenPlaced
         * on both — causing the non-natural block to also use deco drop
         * logic, which may strip its intended ingredient drops.
         *
         * <p>EXPECTED: FAIL — the shared gathering gets the flag set.
         */
        @Test
        void recipeBlockSharingGatheringDoesNotGetUseDefaultDropFlag() {
            BlockBreakingDropType breaking =
                    new BlockBreakingDropType("Rocks", 0, 1, "Some_Item", null);
            // ONE shared gathering instance
            BlockGathering sharedGathering = gathering(breaking, null, null, null);

            BlockType naturalBlock = blockType("Natural_Block", sharedGathering);
            BlockType recipeBlock = blockType("Recipe_Block", sharedGathering);

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Natural_Block", naturalBlock);
            blockTypes.put("Recipe_Block", recipeBlock);

            Map<String, Item> items = new HashMap<>();
            items.put("Natural_Block", item("Natural_Block", "Natural_Block", true, 100));
            items.put("Recipe_Block", item("Recipe_Block", "Recipe_Block", true, 100));
            items.put("Some_Item", item("Some_Item", null, false, 100));

            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Recipe_Block_Recipe", recipe("Recipe_Block_Recipe",
                    new MaterialQuantity[]{materialQty("Some_Item", 2)},
                    materialQty("Recipe_Block", 1),
                    BenchType.StructuralCrafting, "Builders"));

            installAndInit(blockTypes, items, recipes);
            applyFullPipeline();

            // Recipe_Block should NOT have useDefaultDropWhenPlaced=true
            assertFalse(readUseDefaultDropWhenPlaced(recipeBlock.getGathering()),
                    "Recipe block sharing a gathering instance with a natural block "
                            + "should NOT inherit useDefaultDropWhenPlaced=true");
        }

        /**
         * Rock_Shale_Cobble (natural) and Rock_Shale_Cobble_Stairs (recipe)
         * could share the same BlockGathering via parent inheritance. If
         * PlacedBlockDropModifier sets the flag on the shared gathering,
         * player-placed Stairs would also use deco drop logic, losing their
         * intended recipe-ingredient drops.
         *
         * <p>EXPECTED: FAIL
         */
        @Test
        void stairsSharingGatheringWithCobbleDoesNotGetDecoFlag() {
            BlockBreakingDropType breaking =
                    new BlockBreakingDropType("Rocks", 1, 1, "Rock_Shale_Cobble", null);
            BlockGathering sharedGathering = gathering(breaking, null, null, null);

            BlockType cobble = blockType("Rock_Shale_Cobble", sharedGathering);
            BlockType stairs = blockType("Rock_Shale_Cobble_Stairs", sharedGathering);

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Rock_Shale_Cobble", cobble);
            blockTypes.put("Rock_Shale_Cobble_Stairs", stairs);

            Map<String, Item> items = new HashMap<>();
            items.put("Rock_Shale_Cobble",
                    item("Rock_Shale_Cobble", "Rock_Shale_Cobble", true, 100));
            items.put("Rock_Shale_Cobble_Stairs",
                    item("Rock_Shale_Cobble_Stairs", "Rock_Shale_Cobble_Stairs", true, 100));

            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Rock_Shale_Cobble_Stairs_Recipe_Generated_0",
                    recipe("Rock_Shale_Cobble_Stairs_Recipe_Generated_0",
                            new MaterialQuantity[]{materialQty("Rock_Shale_Cobble", 1)},
                            materialQty("Rock_Shale_Cobble_Stairs", 1),
                            BenchType.StructuralCrafting, "Builders"));

            installAndInit(blockTypes, items, recipes);
            applyFullPipeline();

            assertFalse(readUseDefaultDropWhenPlaced(stairs.getGathering()),
                    "Stairs (recipe block) should NOT get useDefaultDropWhenPlaced "
                            + "just because it shares a gathering instance with cobble (natural)");
        }
    }

    // =================================================================
    //  Classification verification with real game data
    // =================================================================

    @Nested
    class RealGameClassification {

        /**
         * Rock_Shale has a Processing recipe (Rock_Shale_Recipe_Generated_0).
         * Processing recipes should NOT disqualify a block from being natural.
         * If this test fails, the isCraftingBench filter is missing or broken.
         */
        @Test
        void rockShaleWithProcessingRecipeIsStillNatural() {
            BlockType rockShale = blockType("Rock_Shale",
                    gathering(new BlockBreakingDropType("Rocks", 0, 1, "Rock_Shale_Cobble", null),
                            null, null, null));

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Rock_Shale", rockShale);

            Map<String, Item> items = new HashMap<>();
            items.put("Rock_Shale", item("Rock_Shale", "Rock_Shale", true, 100));
            items.put("Rock_Shale_Cobble", item("Rock_Shale_Cobble", null, false, 100));

            // Processing recipe — should NOT make Rock_Shale non-natural
            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Rock_Shale_Recipe_Generated_0",
                    recipe("Rock_Shale_Recipe_Generated_0",
                            new MaterialQuantity[]{materialQtyResource("Rock_Shale", 2)},
                            materialQty("Rock_Shale", 1),
                            BenchType.Processing));

            installAndInit(blockTypes, items, recipes);

            assertTrue(NaturalResourceRegistry.isNaturalBlock("Rock_Shale"),
                    "Rock_Shale should be natural — Processing recipes don't disqualify");
        }

        /**
         * Rail has a Crafting-bench recipe (not StructuralCrafting).
         * BenchRecipeRegistries only tracks configured benches (Builders,
         * Furniture_Bench), so Rail should NOT be in it. But
         * NaturalResourceRegistry should still exclude Rail
         * (isCraftingBench covers Workbench too).
         */
        @Test
        void railWithCraftingBenchNotInBenchRecipeRegistries() {
            BlockType rail = blockType("Rail",
                    gathering(new BlockBreakingDropType("SoftBlocks", 0, 1, null, null),
                            null, null, null));

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Rail", rail);

            Map<String, Item> items = new HashMap<>();
            items.put("Rail", item("Rail", "Rail", true, 100));
            items.put("Ingredient_Bar_Iron", item("Ingredient_Bar_Iron", null, false, 100));

            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Rail_Recipe_Generated_0",
                    recipe("Rail_Recipe_Generated_0",
                            new MaterialQuantity[]{materialQty("Ingredient_Bar_Iron", 1)},
                            materialQty("Rail", 4),
                            BenchType.Crafting, "Workbench"));

            installAndInit(blockTypes, items, recipes);

            assertFalse(NaturalResourceRegistry.isNaturalBlock("Rail"),
                    "Rail should NOT be natural (has Crafting-bench recipe)");
            assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Rail"),
                    "Rail should NOT be in BenchRecipeRegistries "
                            + "(Workbench is not a registered bench)");
        }

        /**
         * Rail's breaking quantity must stay 1 after the full pipeline
         * when it has its OWN breaking instance (no sharing).
         * This proves the classification is correct in isolation.
         */
        @Test
        void railWithOwnBreakingKeepsQuantity1() {
            BlockBreakingDropType railBreaking =
                    new BlockBreakingDropType("SoftBlocks", 0, 1, null, null);
            BlockType rail = blockType("Rail",
                    gathering(railBreaking, null, null, null));

            Map<String, BlockType> blockTypes = new HashMap<>();
            blockTypes.put("Rail", rail);

            Map<String, Item> items = new HashMap<>();
            items.put("Rail", item("Rail", "Rail", true, 100));
            items.put("Ingredient_Bar_Iron", item("Ingredient_Bar_Iron", null, false, 100));

            Map<String, CraftingRecipe> recipes = new HashMap<>();
            recipes.put("Rail_Recipe_Generated_0",
                    recipe("Rail_Recipe_Generated_0",
                            new MaterialQuantity[]{materialQty("Ingredient_Bar_Iron", 1)},
                            materialQty("Rail", 4),
                            BenchType.Crafting, "Workbench"));

            installAndInit(blockTypes, items, recipes);
            applyFullPipeline();

            assertEquals(1, readBreakingQuantity(railBreaking),
                    "Rail with its own breaking instance should keep quantity=1");
        }
    }
}
