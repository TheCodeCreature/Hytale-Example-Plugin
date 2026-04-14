package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

// Uses DropScaler.applyModifications() to test the consolidated single-pass pipeline

/**
 * Integration tests that verify the full modifier pipeline produces correct
 * end-to-end results: natural blocks drop 12x, recipe blocks drop scaled
 * ingredients, base blocks stay at 1x, recipe costs are scaled, and stack
 * sizes are adjusted.
 */
class ResourceScalingIntegrationTest {

    private TestDataSet data;

    @BeforeEach
    void setUp() {
        data = new TestDataSet();
        data.install();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    /**
     * Runs the consolidated single-pass pipeline.
     */
    private void applyFullPipeline() {
        DropScaler.applyModifications();
    }

    @Nested
    class NaturalBlockScaling {

        @Test
        void stoneDrops12x() {
            applyFullPipeline();
            assertEquals(12,
                    readBreakingQuantity(readGatheringBreaking(data.rockStone.getGathering())));
        }

        @Test
        void logDrops12x() {
            applyFullPipeline();
            assertEquals(12,
                    readBreakingQuantity(readGatheringBreaking(data.woodLogOak.getGathering())));
        }

        @Test
        void dirtSoftUnchanged() {
            applyFullPipeline();
            // Soft blocks have no quantity to multiply (always drop 1)
            assertEquals("Dirt", readSoftItemId(data.dirtSoft));
        }

        @Test
        void naturalStackSizesBoosted() {
            applyFullPipeline();
            assertEquals(1200, readItemMaxStack(data.itemRockStone));
            assertEquals(1200, readItemMaxStack(data.itemWoodLogOak));
        }
    }

    @Nested
    class RecipeBlockScaling {

        @Test
        void slabDropsIngredients() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.woodSlabOak.getGathering());
            assertEquals("Wood_Planks_Oak", breaking.getItemId());
            // 1 input * 12 / 2 output = 6
            assertEquals(6, breaking.getQuantity());
        }

        @Test
        void railDropsIngredients() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.railIron.getGathering());
            assertEquals("Metal_Ingot_Iron", breaking.getItemId());
            // 2 input * 12 / 1 output = 24
            assertEquals(24, breaking.getQuantity());
        }

        @Test
        void doorDropsIngredients() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.doorWoodOak.getGathering());
            assertEquals("Wood_Planks_Oak", breaking.getItemId());
            // 2 input * 12 / 1 output = 24
            assertEquals(24, breaking.getQuantity());
        }

        @Test
        void railDoesNotDrop12xOfItself() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.railIron.getGathering());
            // Should NOT be "Rail_Iron" × 12 — that was the bug we fixed
            assertNotEquals("Rail_Iron", breaking.getItemId(),
                    "Recipe block should drop ingredients, not 12x of itself");
        }

        @Test
        void doorDoesNotDrop12xOfItself() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.doorWoodOak.getGathering());
            assertNotEquals("Door_Wood_Oak", breaking.getItemId(),
                    "Recipe block should drop ingredients, not 12x of itself");
        }
    }

    @Nested
    class BaseBlockBehavior {

        @Test
        void planksKeep1xCost() {
            applyFullPipeline();
            MaterialQuantity[] inputs = readRecipeInputs(data.recipePlanksOak);
            assertEquals(1, inputs[0].getQuantity(),
                    "Base block (planks) should keep 1x recipe cost");
        }

        @Test
        void planksDropSelf() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.woodPlanksOak.getGathering());
            // RecipeDropModifier skips base blocks, so the original breaking stays
            assertEquals("Wood_Planks_Oak", breaking.getItemId());
            assertEquals(1, breaking.getQuantity());
        }
    }

    @Nested
    class IngredientDropScaling {

        @Test
        void bushPlantFiberScaled() {
            applyFullPipeline();
            assertEquals(12, readItemDropQuantityMin(data.bushPlantFiberDrop));
            assertEquals(24, readItemDropQuantityMax(data.bushPlantFiberDrop));
        }

        @Test
        void bushBerryNotScaled() {
            applyFullPipeline();
            assertEquals(1, readItemDropQuantityMin(data.bushBerryDrop));
            assertEquals(1, readItemDropQuantityMax(data.bushBerryDrop));
        }

        @Test
        void leavesPlantFiberConvertedToDropList() {
            applyFullPipeline();
            assertNull(readHarvestItemId(data.leavesHarvest),
                    "Harvest itemId should be null after conversion");
            assertNotNull(readHarvestDropListId(data.leavesHarvest),
                    "Harvest should now use a dropListId");
        }
    }

    @Nested
    class CraftingCostScaling {

        @Test
        void nonBaseRecipeCostsScaled() {
            applyFullPipeline();

            MaterialQuantity[] slabInputs = readRecipeInputs(data.recipeSlabOak);
            assertEquals(12, slabInputs[0].getQuantity(),
                    "Slab recipe cost (non-base) should be 1 * 12 = 12");

            MaterialQuantity[] railInputs = readRecipeInputs(data.recipeRailIron);
            assertEquals(24, railInputs[0].getQuantity(),
                    "Rail recipe cost (non-base) should be 2 * 12 = 24");
        }

        @Test
        void baseRecipeCostUnchanged() {
            applyFullPipeline();
            MaterialQuantity[] planksInputs = readRecipeInputs(data.recipePlanksOak);
            assertEquals(1, planksInputs[0].getQuantity());
        }
    }

    @Nested
    class RecipeBlockLeakGuard {

        @Test
        void recipeBlockInNaturalRegistryNotScaledByNaturalDrop() {
            // Simulate the bug: recipe blocks slipping into NaturalResourceRegistry
            data.naturalBlockIds.add("Rail_Iron");
            data.naturalBlockIds.add("Door_Wood_Oak");
            data.naturalBlockIds.add("Wood_Slab_Oak");
            data.install();

            applyFullPipeline();

            // These should still have ingredient drops, not 12x self-drops
            var railBreaking = readGatheringBreaking(data.railIron.getGathering());
            assertEquals("Metal_Ingot_Iron", railBreaking.getItemId(),
                    "Rail should drop ingredients even if in natural registry");

            var doorBreaking = readGatheringBreaking(data.doorWoodOak.getGathering());
            assertEquals("Wood_Planks_Oak", doorBreaking.getItemId(),
                    "Door should drop ingredients even if in natural registry");
        }
    }

    @Nested
    class PlacedBlockBehavior {

        @Test
        void naturalBlocksGetUseDefaultDropWhenPlaced() {
            applyFullPipeline();
            assertTrue(readUseDefaultDropWhenPlaced(data.rockStone.getGathering()),
                    "Rock_Stone should have useDefaultDropWhenPlaced=true");
            assertTrue(readUseDefaultDropWhenPlaced(data.woodLogOak.getGathering()),
                    "Wood_Log_Oak should have useDefaultDropWhenPlaced=true");
        }

        @Test
        void recipeBlocksDoNotGetUseDefaultDropWhenPlaced() {
            applyFullPipeline();
            assertFalse(readUseDefaultDropWhenPlaced(data.woodSlabOak.getGathering()),
                    "Recipe block Wood_Slab_Oak should NOT have useDefaultDropWhenPlaced");
            assertFalse(readUseDefaultDropWhenPlaced(data.railIron.getGathering()),
                    "Recipe block Rail_Iron should NOT have useDefaultDropWhenPlaced");
            assertFalse(readUseDefaultDropWhenPlaced(data.doorWoodOak.getGathering()),
                    "Recipe block Door_Wood_Oak should NOT have useDefaultDropWhenPlaced");
        }

        @Test
        void baseBlockDoesNotGetUseDefaultDropWhenPlaced() {
            applyFullPipeline();
            assertFalse(readUseDefaultDropWhenPlaced(data.woodPlanksOak.getGathering()),
                    "Base block Wood_Planks_Oak should NOT have useDefaultDropWhenPlaced");
        }

        @Test
        void sandWithSoftGatheringGetsFlagSet() {
            applyFullPipeline();
            assertTrue(readUseDefaultDropWhenPlaced(data.sand.getGathering()),
                    "Sand (soft-only natural) should have useDefaultDropWhenPlaced=true");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void blockWithNullGatheringSurvivesPipeline() {
            BlockType noGathering = blockType("NoGathering", null);
            data.blockTypes.put("NoGathering", noGathering);
            data.naturalBlockIds.add("NoGathering");
            data.install();

            assertDoesNotThrow(() -> applyFullPipeline());
        }

        @Test
        void blockMissingFromAssetMapSurvivesPipeline() {
            // Add a block ID to natural registry that doesn't exist in the store
            data.naturalBlockIds.add("Ghost_Block");
            setNaturalRegistry(data.naturalBlockIds, data.naturalItemIds);

            assertDoesNotThrow(() -> applyFullPipeline());
        }

        @Test
        void recipeWithOutputQtyOneProducesCorrectDrops() {
            // Rail: 2 * 12 / 1 = 24
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.railIron.getGathering());
            assertEquals(24, breaking.getQuantity());
        }

        @Test
        void recipeWithOutputQtyTwoProducesCorrectDrops() {
            // Slab: 1 * 12 / 2 = 6
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.woodSlabOak.getGathering());
            assertEquals(6, breaking.getQuantity());
        }

        @Test
        void gatherTypePreservedThroughPipeline() {
            applyFullPipeline();

            var railBreaking = readGatheringBreaking(data.railIron.getGathering());
            assertEquals("Metals", railBreaking.getGatherType(),
                    "GatherType should be preserved after RecipeDropModifier");

            var rockBreaking = data.rockStone.getGathering().getBreaking();
            assertEquals("Rocks", rockBreaking.getGatherType(),
                    "GatherType should be unchanged for natural blocks");
        }
    }

    @Nested
    class MultiIngredientRecipeScaling {

        @Test
        void furnitureBedUsesDropList() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.furnitureBed.getGathering());
            assertNull(readBreakingItemId(breaking),
                    "Multi-ingredient recipe should use dropListId, not direct itemId");
            assertEquals("Plugin_RecipeDrop_Furniture_Bed",
                    readBreakingDropListId(breaking));
        }

        @Test
        void furnitureBedDropsAllIngredients() {
            applyFullPipeline();
            ItemDropList dl = ItemDropList.getAssetMap().getAssetMap()
                    .get("Plugin_RecipeDrop_Furniture_Bed");
            assertNotNull(dl, "Synthetic drop list should be registered");

            List<ItemDrop> drops = dl.getContainer().getAllDrops(new ArrayList<>());
            assertEquals(3, drops.size(), "Should have 3 ingredient drops");

            // 3x Wood_Planks_Oak * 12 / 1 output = 36
            assertEquals("Wood_Planks_Oak", drops.get(0).getItemId());
            assertEquals(36, drops.get(0).getQuantityMin());
            assertEquals(36, drops.get(0).getQuantityMax());

            // 4x Ingredient_Fibre * 12 / 1 output = 48
            assertEquals("Ingredient_Fibre", drops.get(1).getItemId());
            assertEquals(48, drops.get(1).getQuantityMin());
            assertEquals(48, drops.get(1).getQuantityMax());

            // 2x Cloth_Wool_Red * 12 / 1 output = 24
            assertEquals("Cloth_Wool_Red", drops.get(2).getItemId());
            assertEquals(24, drops.get(2).getQuantityMin());
            assertEquals(24, drops.get(2).getQuantityMax());
        }

        @Test
        void furnitureBedDoesNotDropSelf() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.furnitureBed.getGathering());
            assertNotEquals("Furniture_Bed", readBreakingItemId(breaking),
                    "Multi-ingredient recipe block should not drop itself");
        }

        @Test
        void furnitureBedPreservesGatherType() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.furnitureBed.getGathering());
            assertEquals("Woods", breaking.getGatherType(),
                    "GatherType should be preserved for multi-ingredient recipe blocks");
        }

        @Test
        void singleIngredientRecipeStillUseDirectItemId() {
            applyFullPipeline();
            // Door: single input (2x Wood_Planks_Oak → 1x Door_Wood_Oak) — should stay direct
            var breaking = readGatheringBreaking(data.doorWoodOak.getGathering());
            assertEquals("Wood_Planks_Oak", readBreakingItemId(breaking),
                    "Single-ingredient recipe should use direct itemId");
            assertNull(readBreakingDropListId(breaking),
                    "Single-ingredient recipe should not use dropListId");
        }

        @Test
        void multiIngredientRecipeCostsScaled() {
            applyFullPipeline();
            MaterialQuantity[] inputs = readRecipeInputs(data.recipeFurnitureBed);
            assertEquals(36, inputs[0].getQuantity(), "3 * 12 = 36");
            assertEquals(48, inputs[1].getQuantity(), "4 * 12 = 48");
            assertEquals(24, inputs[2].getQuantity(), "2 * 12 = 24");
        }
    }
}
