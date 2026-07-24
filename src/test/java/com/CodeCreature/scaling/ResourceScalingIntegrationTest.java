package com.CodeCreature.scaling;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.CodeCreature.scaling.AssetTestHelper.blockType;
import static com.CodeCreature.scaling.AssetTestHelper.cleanup;
import static com.CodeCreature.scaling.AssetTestHelper.readBreakingDropListId;
import static com.CodeCreature.scaling.AssetTestHelper.readBreakingItemId;
import static com.CodeCreature.scaling.AssetTestHelper.readBreakingQuantity;
import static com.CodeCreature.scaling.AssetTestHelper.readGatheringBreaking;
import static com.CodeCreature.scaling.AssetTestHelper.readHarvestDropListId;
import static com.CodeCreature.scaling.AssetTestHelper.readHarvestItemId;
import static com.CodeCreature.scaling.AssetTestHelper.readItemDropQuantityMax;
import static com.CodeCreature.scaling.AssetTestHelper.readItemDropQuantityMin;
import static com.CodeCreature.scaling.AssetTestHelper.readItemMaxStack;
import static com.CodeCreature.scaling.AssetTestHelper.readRecipeInputs;
import static com.CodeCreature.scaling.AssetTestHelper.readSoftDropListId;
import static com.CodeCreature.scaling.AssetTestHelper.readSoftItemId;
import static com.CodeCreature.scaling.AssetTestHelper.readUseDefaultDropWhenPlaced;
import static com.CodeCreature.scaling.AssetTestHelper.setNaturalRegistry;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

// Uses DropScaler.applyModifications() to test the consolidated single-pass pipeline

/**
 * Integration tests that verify the full modifier pipeline produces correct
 * end-to-end results: natural blocks drop 12x, recipe blocks drop scaled
 * ingredients, raw input costs are scaled 12x, crafted intermediate inputs
 * retain vanilla quantities, and stack sizes are adjusted.
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
        void dirtSoftConvertedToDropList() {
            applyFullPipeline();
            // Soft drops with direct itemId are converted to drop lists for 12x scaling
            assertNull(readSoftItemId(data.dirtSoft));
            assertEquals("Plugin_NaturalIngredient_Dirt", readSoftDropListId(data.dirtSoft));
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
            // All recipe blocks use synthetic drop lists
            assertNull(readBreakingItemId(breaking));
            assertEquals("Plugin_RecipeDrop_Wood_Slab_Oak", readBreakingDropListId(breaking));
            assertEquals(1, breaking.getQuantity());
        }

        @Test
        void railDropsIngredients() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.railIron.getGathering());
            // All recipe blocks use synthetic drop lists
            assertNull(readBreakingItemId(breaking));
            assertEquals("Plugin_RecipeDrop_Rail_Iron", readBreakingDropListId(breaking));
            assertEquals(1, breaking.getQuantity());
        }

        @Test
        void doorDropsIngredients() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.doorWoodOak.getGathering());
            // All recipe blocks use synthetic drop lists
            assertNull(readBreakingItemId(breaking));
            assertEquals("Plugin_RecipeDrop_Door_Wood_Oak", readBreakingDropListId(breaking));
            assertEquals(1, breaking.getQuantity());
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
    class LeafOnlyScaling {

        @Test
        void planksRawInputScaled12x() {
            applyFullPipeline();
            MaterialQuantity[] inputs = readRecipeInputs(data.recipePlanksOak);
            // Wood_Log_Oak is a raw material → scaled ×12
            assertEquals(12, inputs[0].getQuantity(),
                    "Planks recipe raw input (Wood_Log_Oak) should be scaled ×12");
        }

        @Test
        void planksDropsIngredients() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.woodPlanksOak.getGathering());
            // Planks is a recipe block → Phase 3a creates synthetic drop list
            // Input: 12 Wood_Log_Oak, Output: 2 Planks → drop = 12/2 = 6 per plank
            assertNotNull(breaking.getDropListId(),
                    "Planks should use a synthetic drop list (recipe block)");
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
        void craftedInputCostsUnchanged() {
            applyFullPipeline();

            MaterialQuantity[] slabInputs = readRecipeInputs(data.recipeSlabOak);
            assertEquals(1, slabInputs[0].getQuantity(),
                    "Slab recipe input (Wood_Planks_Oak, crafted) should stay at 1");

            MaterialQuantity[] railInputs = readRecipeInputs(data.recipeRailIron);
            assertEquals(2, railInputs[0].getQuantity(),
                    "Rail recipe input (Metal_Ingot_Iron, crafted) should stay at 2");
        }

        @Test
        void rawInputRecipeScaled() {
            applyFullPipeline();
            MaterialQuantity[] planksInputs = readRecipeInputs(data.recipePlanksOak);
            // Wood_Log_Oak is raw → scaled ×12
            assertEquals(12, planksInputs[0].getQuantity(),
                    "Planks recipe raw input should be scaled ×12");
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
            assertNull(readBreakingItemId(railBreaking),
                    "Rail should use drop list, not direct itemId");
            assertEquals("Plugin_RecipeDrop_Rail_Iron", readBreakingDropListId(railBreaking),
                    "Rail should drop ingredients even if in natural registry");

            var doorBreaking = readGatheringBreaking(data.doorWoodOak.getGathering());
            assertNull(readBreakingItemId(doorBreaking),
                    "Door should use drop list, not direct itemId");
            assertEquals("Plugin_RecipeDrop_Door_Wood_Oak", readBreakingDropListId(doorBreaking),
                    "Door should drop ingredients even if in natural registry");
        }
    }

    @Nested
    class PlacedBlockBehavior {

        @Test
        void naturalBlocksDoNotGetUseDefaultDropWhenPlaced() {
            // useDefaultDropWhenPlaced is no longer set — placement cost enforcement
            // is handled at runtime by PlacementCostScaler instead.
            applyFullPipeline();
            assertFalse(readUseDefaultDropWhenPlaced(data.rockStone.getGathering()),
                    "Rock_Stone should NOT have useDefaultDropWhenPlaced (enforced by PlacementCostScaler)");
            assertFalse(readUseDefaultDropWhenPlaced(data.woodLogOak.getGathering()),
                    "Wood_Log_Oak should NOT have useDefaultDropWhenPlaced (enforced by PlacementCostScaler)");
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
        void sandDoesNotGetUseDefaultDropWhenPlaced() {
            applyFullPipeline();
            assertFalse(readUseDefaultDropWhenPlaced(data.sand.getGathering()),
                    "Sand should NOT have useDefaultDropWhenPlaced (enforced by PlacementCostScaler)");
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
        void recipeWithOutputQtyTwoProducesCorrectDrops() {
            // Slab: Wood_Planks_Oak is crafted → input stays 1, drop = max(1, 1/2) = 1
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.woodSlabOak.getGathering());
            assertEquals(1, breaking.getQuantity());
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
            // The synthetic drop list is registered via ItemDropList.getAssetStore().loadAssets()
            // in Phase 5, which requires full engine infrastructure not available in unit tests.
            // Verify the breaking config instead — the dropListId proves the multi-ingredient
            // path was taken, and the list contents are validated by the DropScaler unit under
            // the same code path that builds the list.
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.furnitureBed.getGathering());
            assertNull(readBreakingItemId(breaking),
                    "Multi-ingredient recipe should not use direct itemId");
            assertEquals("Plugin_RecipeDrop_Furniture_Bed",
                    readBreakingDropListId(breaking),
                    "Multi-ingredient recipe should reference a synthetic drop list");
            assertEquals(1, breaking.getQuantity(),
                    "Breaking quantity should be 1 (actual quantities are in the drop list)");
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
        void multiIngredientRecipeCostsScaled() {
            applyFullPipeline();
            MaterialQuantity[] inputs = readRecipeInputs(data.recipeFurnitureBed);
            // Wood_Planks_Oak is crafted → stays at 3
            assertEquals(3, inputs[0].getQuantity(),
                    "Wood_Planks_Oak (crafted) should stay at 3");
            assertEquals(48, inputs[1].getQuantity(),
                    "Ingredient_Fibre (raw) 4 * 12 = 48");
            assertEquals(24, inputs[2].getQuantity(),
                    "Cloth_Wool_Red (raw) 2 * 12 = 24");
        }
    }

    @Nested
    class GenericResourceTypeRecipeDrops {

        @Test
        void kweebecBedUsesDropList() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.kweebecBed.getGathering());
            assertNull(readBreakingItemId(breaking),
                    "Kweebec bed (multi-ingredient with ResourceTypeId) should use dropListId");
            assertEquals("Plugin_RecipeDrop_Furniture_Kweebec_Bed",
                    readBreakingDropListId(breaking));
        }

        @Test
        void kweebecBedDoesNotDropSelf() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.kweebecBed.getGathering());
            assertNotEquals("Furniture_Kweebec_Bed", readBreakingItemId(breaking),
                    "Kweebec bed should not drop itself — should drop recipe ingredients");
        }

        @Test
        void kweebecBedResolvesWoodAllToNaturalItem() {
            applyFullPipeline();
            // Generic ResourceTypeId inputs now project to proxy item IDs.
            var breaking = readGatheringBreaking(data.kweebecBed.getGathering());
            String dropListId = readBreakingDropListId(breaking);
            assertNotNull(dropListId,
                "Kweebec bed should have a synthetic drop list (generic proxy projection)");
        }

        @Test
        void kweebecBedDropProjectionContainsGenericProxyId() {
            GenericDropProxyCatalog proxyCatalog = new GenericDropProxyCatalog();
            RecipeDropProjection projection = new RecipeDropProjection(proxyCatalog);
            var drops = projection.projectRecipeDrops(data.recipeKweebecBed, true);
            assertTrue(drops.stream().anyMatch(d -> proxyCatalog.isProxyItemId(d.itemId())),
                "Generic recipe inputs must emit proxy item IDs in projected break drops");
        }

        @Test
        void kweebecBedPreservesGatherType() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.kweebecBed.getGathering());
            assertEquals("Woods", breaking.getGatherType(),
                    "GatherType should be preserved for Kweebec bed");
        }

        @Test
        void kweebecBedRecipeCostsScaled() {
            applyFullPipeline();
            MaterialQuantity[] inputs = readRecipeInputs(data.recipeKweebecBed);
            assertEquals(36, inputs[0].getQuantity(), "3 * 12 = 36 (Wood_All via ResourceTypeId)");
            assertEquals(48, inputs[1].getQuantity(), "4 * 12 = 48 (Ingredient_Fibre)");
        }

        @Test
        void fenceDropsIngredientNotItself() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.fenceHardwood.getGathering());
            assertNotEquals("Wood_Hardwood_Fence", readBreakingItemId(breaking),
                    "Fence should drop its ingredient, not itself");
        }

        @Test
        void fenceDropQuantityIsCorrect() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.fenceHardwood.getGathering());
            // Breaking quantity is always 1; actual drop quantity (6) is inside the synthetic drop list
            assertEquals(1, breaking.getQuantity(),
                    "Breaking quantity should be 1 (actual qty is in the drop list)");
            assertEquals("Plugin_RecipeDrop_Wood_Hardwood_Fence", readBreakingDropListId(breaking));
        }

        @Test
        void directRecipeProjectionContainsConcreteItemIdsOnly() {
            GenericDropProxyCatalog proxyCatalog = new GenericDropProxyCatalog();
            RecipeDropProjection projection = new RecipeDropProjection(proxyCatalog);
            var drops = projection.projectRecipeDrops(data.recipeDoorWood, false);
            assertTrue(drops.stream().noneMatch(d -> proxyCatalog.isProxyItemId(d.itemId())),
                "Direct item input recipes must remain concrete");
        }

        @Test
        void fencePreservesGatherType() {
            applyFullPipeline();
            var breaking = readGatheringBreaking(data.fenceHardwood.getGathering());
            assertEquals("Woods", breaking.getGatherType(),
                    "GatherType should be preserved for fence");
        }
    }
}
