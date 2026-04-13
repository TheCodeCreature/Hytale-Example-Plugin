package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IngredientDropModifier}.
 * Validates that natural blocks dropping recipe ingredients get those
 * drops scaled by RESOURCE_MULTIPLIER, while non-ingredient drops
 * remain unchanged.
 */
class IngredientDropModifierTest {

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

    @Test
    void harvestIngredientDirectItemConvertedToDropList() {
        // Leaves_Oak has harvest with itemId=Plant_Fiber (a recipe ingredient)
        // IngredientDropModifier should swap it to a drop list
        IngredientDropModifier.apply();

        String harvestItemId = readHarvestItemId(data.leavesHarvest);
        String harvestDropListId = readHarvestDropListId(data.leavesHarvest);

        // After swap: itemId should be null, dropListId should be set
        assertNull(harvestItemId,
                "After ingredient swap, harvest itemId should be null");
        assertNotNull(harvestDropListId,
                "After ingredient swap, harvest should have a dropListId");
        assertTrue(harvestDropListId.startsWith("Plugin_NaturalIngredient_"),
                "Drop list ID should use the plugin prefix");
    }

    @Test
    void softDropListIngredientQuantityScaled() {
        // Bush_Berry has soft with dropListId=DropList_Bush containing Plant_Fiber
        // Plant_Fiber is a recipe ingredient → its drop quantities should be scaled
        IngredientDropModifier.apply();

        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(1 * m, readItemDropQuantityMin(data.bushPlantFiberDrop),
                "Plant_Fiber drop quantityMin should be scaled to 12");
        assertEquals(2 * m, readItemDropQuantityMax(data.bushPlantFiberDrop),
                "Plant_Fiber drop quantityMax should be scaled to 24");
    }

    @Test
    void nonIngredientDropInSameListNotScaled() {
        // Berry is NOT a recipe ingredient — it should NOT be scaled
        IngredientDropModifier.apply();

        assertEquals(1, readItemDropQuantityMin(data.bushBerryDrop),
                "Berry drop quantityMin should remain at 1");
        assertEquals(1, readItemDropQuantityMax(data.bushBerryDrop),
                "Berry drop quantityMax should remain at 1");
    }

    @Test
    void recipeBlocksAreSkippedByIngredientModifier() {
        // Add recipe blocks to natural registry (simulating the leak)
        data.naturalBlockIds.add("Rail_Iron");
        data.naturalBlockIds.add("Door_Wood_Oak");
        data.install();

        IngredientDropModifier.apply();

        // Recipe blocks should NOT have their breaking configs modified
        assertEquals(1, readBreakingQuantity(data.railBreaking),
                "Rail_Iron should be skipped (has recipe)");
        assertEquals(1, readBreakingQuantity(data.doorBreaking),
                "Door_Wood_Oak should be skipped (has recipe)");
    }

    @Test
    void physicsDropIngredientIsScaled() {
        // Create a natural block with physics drop containing an ingredient
        PhysicsDropType ingredientPhysics = new PhysicsDropType(null, "DropList_Physics");
        BlockType physicsBlock = blockType("PhysicsIngredient",
                gathering(null, null, null, ingredientPhysics));

        ItemDrop physicsIngDrop = new ItemDrop("Wood_Planks_Oak", null, 1, 1);
        ItemDropList physicsList = new ItemDropList("DropList_Physics",
                new SingleItemDropContainer(physicsIngDrop, 100.0));

        data.blockTypes.put("PhysicsIngredient", physicsBlock);
        data.naturalBlockIds.add("PhysicsIngredient");
        data.dropLists.put("DropList_Physics", physicsList);
        data.install();

        IngredientDropModifier.apply();

        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(m, readItemDropQuantityMin(physicsIngDrop),
                "Physics ingredient drop quantityMin should be scaled");
        assertEquals(m, readItemDropQuantityMax(physicsIngDrop),
                "Physics ingredient drop quantityMax should be scaled");
    }

    @Test
    void breakingDropListIngredientIsScaled() {
        // Create a natural block with breaking config that uses a dropListId
        BlockBreakingDropType breakingWithList =
                new BlockBreakingDropType("Rocks", 0, 1, null, "DropList_Breaking");
        BlockType breakingListBlock = blockType("BreakingListBlock",
                gathering(breakingWithList, null, null, null));

        ItemDrop breakingIngDrop = new ItemDrop("Metal_Ingot_Iron", null, 1, 3);
        ItemDropList breakingList = new ItemDropList("DropList_Breaking",
                new SingleItemDropContainer(breakingIngDrop, 100.0));

        data.blockTypes.put("BreakingListBlock", breakingListBlock);
        data.naturalBlockIds.add("BreakingListBlock");
        data.dropLists.put("DropList_Breaking", breakingList);
        data.install();

        IngredientDropModifier.apply();

        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(m, readItemDropQuantityMin(breakingIngDrop));
        assertEquals(3 * m, readItemDropQuantityMax(breakingIngDrop));
    }

    @Test
    void sharedSoftConfigOnlyProcessedOnce() {
        // Two blocks share the same SoftBlockDropType with an ingredient
        SoftBlockDropType sharedSoft = new SoftBlockDropType("Plant_Fiber", null, true);
        BlockType blockA = blockType("SharedSoft_A", gathering(null, sharedSoft, null, null));
        BlockType blockB = blockType("SharedSoft_B", gathering(null, sharedSoft, null, null));

        data.blockTypes.put("SharedSoft_A", blockA);
        data.blockTypes.put("SharedSoft_B", blockB);
        data.naturalBlockIds.add("SharedSoft_A");
        data.naturalBlockIds.add("SharedSoft_B");
        data.install();

        IngredientDropModifier.apply();

        // The shared config should have been swapped to dropListId only once
        String dropListId = readSoftDropListId(sharedSoft);
        assertNotNull(dropListId,
                "Shared soft should be converted to use dropListId");
    }

    @Test
    void sharedDropListItemDropOnlyScaledOnce() {
        // Two blocks share the same dropListId → the ItemDrop should only be scaled once
        SoftBlockDropType softA = new SoftBlockDropType(null, "DropList_Shared", true);
        SoftBlockDropType softB = new SoftBlockDropType(null, "DropList_Shared", true);
        BlockType blockA = blockType("SharedList_A", gathering(null, softA, null, null));
        BlockType blockB = blockType("SharedList_B", gathering(null, softB, null, null));

        ItemDrop sharedDrop = new ItemDrop("Wood_Planks_Oak", null, 1, 1);
        ItemDropList sharedList = new ItemDropList("DropList_Shared",
                new SingleItemDropContainer(sharedDrop, 100.0));

        data.blockTypes.put("SharedList_A", blockA);
        data.blockTypes.put("SharedList_B", blockB);
        data.naturalBlockIds.add("SharedList_A");
        data.naturalBlockIds.add("SharedList_B");
        data.dropLists.put("DropList_Shared", sharedList);
        data.install();

        IngredientDropModifier.apply();

        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(m, readItemDropQuantityMin(sharedDrop),
                "Shared drop should only be scaled 1x (not 2x)");
        assertEquals(m, readItemDropQuantityMax(sharedDrop),
                "Shared drop should only be scaled 1x (not 2x)");
    }

    @Test
    void naturalNonIngredientItemStaysUnchanged() {
        // Dirt is natural but NOT a recipe ingredient — its soft config stays intact
        IngredientDropModifier.apply();

        assertEquals("Dirt", readSoftItemId(data.dirtSoft),
                "Dirt soft config should be unchanged (not an ingredient)");
        assertNull(readSoftDropListId(data.dirtSoft),
                "Dirt should not have a dropListId added");
    }

    @Test
    void softWithEmptyItemIdNotTreatedAsIngredient() {
        SoftBlockDropType emptySoft = new SoftBlockDropType("Empty", null, true);
        BlockType emptyDrop = blockType("EmptyDrop",
                gathering(null, emptySoft, null, null));

        data.blockTypes.put("EmptyDrop", emptyDrop);
        data.naturalBlockIds.add("EmptyDrop");
        data.install();

        IngredientDropModifier.apply();

        // "Empty" itemId should not match any ingredient
        assertEquals("Empty", readSoftItemId(emptySoft),
                "Empty-valued itemId should not be swapped");
    }

    @Test
    void blockWithNoGatheringSkipped() {
        data.naturalBlockIds.add("NoGathering_Block");
        BlockType noGathering = blockType("NoGathering_Block", null);
        data.blockTypes.put("NoGathering_Block", noGathering);
        data.install();

        assertDoesNotThrow(() -> IngredientDropModifier.apply());
    }
}
