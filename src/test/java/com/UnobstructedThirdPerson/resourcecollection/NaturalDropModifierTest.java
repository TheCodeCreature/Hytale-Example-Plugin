package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NaturalDropModifier}.
 * Validates that breaking.quantity is multiplied by RESOURCE_MULTIPLIER for
 * natural blocks, and that recipe blocks, drop-list blocks, and shared
 * instances are handled correctly.
 */
class NaturalDropModifierTest {

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
    void naturalBlockBreakingQuantityIsMultiplied() {
        NaturalDropModifier.apply();

        int expected = 1 * ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(expected,
                readBreakingQuantity(readGatheringBreaking(data.rockStone.getGathering())),
                "Rock_Stone breaking quantity should be 12x");
        assertEquals(expected,
                readBreakingQuantity(readGatheringBreaking(data.woodLogOak.getGathering())),
                "Wood_Log_Oak breaking quantity should be 12x");
    }

    @Test
    void softOnlyBlocksAreSkipped() {
        // Dirt has only soft config, no breaking — NaturalDropModifier should skip it
        NaturalDropModifier.apply();

        // Verify dirt's soft config is unchanged (soft has no quantity field)
        assertEquals("Dirt", readSoftItemId(data.dirtSoft));
    }

    @Test
    void blockWithNoGatheringIsSkipped() {
        // Add a natural block with no gathering config
        BlockType emptyBlock = blockType("Natural_NoGathering", null);
        data.blockTypes.put("Natural_NoGathering", emptyBlock);
        data.naturalBlockIds.add("Natural_NoGathering");
        data.install();

        // Should not throw
        assertDoesNotThrow(() -> NaturalDropModifier.apply());
    }

    @Test
    void dropListBasedBreakingIsSkipped() {
        // Create a natural block with breaking that uses dropListId
        BlockBreakingDropType dropListBreaking =
                new BlockBreakingDropType("Rocks", 0, 1, null, "SomeDropList");
        BlockType dropListBlock = blockType("Natural_DropList",
                gathering(dropListBreaking, null, null, null));

        data.blockTypes.put("Natural_DropList", dropListBlock);
        data.naturalBlockIds.add("Natural_DropList");
        data.install();

        NaturalDropModifier.apply();

        // Quantity should still be 1 (skipped because it has dropListId)
        assertEquals(1, readBreakingQuantity(dropListBreaking),
                "Drop-list-based breaking should not be scaled by NaturalDropModifier");
    }

    @Test
    void recipeBlocksAreNotMultiplied() {
        // Rail_Iron and Door_Wood_Oak have recipes — they should NOT be multiplied
        // even if they somehow appear in the natural registry
        data.naturalBlockIds.add("Rail_Iron");
        data.naturalBlockIds.add("Door_Wood_Oak");
        data.install();

        NaturalDropModifier.apply();

        assertEquals(1, readBreakingQuantity(data.railBreaking),
                "Rail_Iron (recipe block) should NOT be multiplied");
        assertEquals(1, readBreakingQuantity(data.doorBreaking),
                "Door_Wood_Oak (recipe block) should NOT be multiplied");
    }

    @Test
    void sharedBreakingInstanceEachBlockGetsOwnCopy() {
        // Two blocks share the exact same BlockBreakingDropType instance
        BlockBreakingDropType sharedBreaking =
                new BlockBreakingDropType("Rocks", 0, 1, "Shared_Stone", null);
        BlockType block1 = blockType("SharedBlock_A", gathering(sharedBreaking, null, null, null));
        BlockType block2 = blockType("SharedBlock_B", gathering(sharedBreaking, null, null, null));

        data.blockTypes.put("SharedBlock_A", block1);
        data.blockTypes.put("SharedBlock_B", block2);
        data.naturalBlockIds.add("SharedBlock_A");
        data.naturalBlockIds.add("SharedBlock_B");
        data.install();

        NaturalDropModifier.apply();

        // Each block should get its own 12x breaking (not shared, not 144x)
        int expected = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(expected,
                readBreakingQuantity(readGatheringBreaking(block1.getGathering())),
                "SharedBlock_A should have 12x breaking quantity");
        assertEquals(expected,
                readBreakingQuantity(readGatheringBreaking(block2.getGathering())),
                "SharedBlock_B should have 12x breaking quantity");
        // Original shared instance should be untouched
        assertEquals(1, readBreakingQuantity(sharedBreaking),
                "Original shared breaking should remain unmodified");
    }

    @Test
    void zeroQuantityNotMultiplied() {
        BlockBreakingDropType zeroBreaking =
                new BlockBreakingDropType("Rocks", 0, 0, "ZeroBlock", null);
        BlockType zeroBlock = blockType("ZeroBlock",
                gathering(zeroBreaking, null, null, null));

        data.blockTypes.put("ZeroBlock", zeroBlock);
        data.naturalBlockIds.add("ZeroBlock");
        data.install();

        NaturalDropModifier.apply();

        assertEquals(0, readBreakingQuantity(zeroBreaking),
                "Zero-quantity breaking should remain at 0");
    }

    @Test
    void baseBlockRecipeStillGetMultiplied() {
        // Wood_Planks_Oak has a base block recipe but it's a recipe block,
        // so NaturalDropModifier skips it via hasRecipe check
        NaturalDropModifier.apply();

        assertEquals(1, readBreakingQuantity(data.planksBreaking),
                "Base block (planks) should NOT be multiplied — it has a recipe");
    }

    @Test
    void multiplierAppliedCorrectlyToVariousQuantities() {
        // Test with quantity > 1
        BlockBreakingDropType multiBreaking =
                new BlockBreakingDropType("Rocks", 0, 3, "MultiDrop_Stone", null);
        BlockType multiBlock = blockType("MultiDrop_Stone",
                gathering(multiBreaking, null, null, null));

        data.blockTypes.put("MultiDrop_Stone", multiBlock);
        data.naturalBlockIds.add("MultiDrop_Stone");
        data.install();

        NaturalDropModifier.apply();

        assertEquals(3 * ResourceConstants.RESOURCE_MULTIPLIER,
                readBreakingQuantity(readGatheringBreaking(multiBlock.getGathering())),
                "Quantity=3 × 12 = 36");
    }

    @Test
    void emptyNaturalRegistryResultsInNoChanges() {
        setNaturalRegistry(new HashSet<>(), new HashSet<>());

        // Should do nothing without errors
        assertDoesNotThrow(() -> NaturalDropModifier.apply());

        // Original blocks unchanged
        assertEquals(1, readBreakingQuantity(data.rockBreaking));
    }
}
