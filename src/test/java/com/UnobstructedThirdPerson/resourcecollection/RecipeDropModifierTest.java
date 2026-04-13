package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RecipeDropModifier}.
 * Validates that recipe blocks get their breaking config replaced with
 * ingredient drops at (inputQty / outputQty), base blocks are skipped,
 * and gatherType/quality are preserved.
 */
class RecipeDropModifierTest {

    private TestDataSet data;

    @BeforeEach
    void setUp() {
        data = new TestDataSet();
        // First scale inputs (as in real init order)
        data.install();
        CraftingCostModifier.apply();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void nonBaseRecipeBlockGetsIngredientDrop() {
        RecipeDropModifier.apply();

        BlockGathering slabGathering = data.woodSlabOak.getGathering();
        BlockBreakingDropType newBreaking = readGatheringBreaking(slabGathering);
        assertNotNull(newBreaking, "Slab should still have a breaking config");

        // Recipe: 1x Wood_Planks_Oak → 2x Wood_Slab_Oak
        // After CraftingCostModifier: input scaled to 12x → 12x Wood_Planks_Oak
        // Drop qty = 12 / 2 = 6
        assertEquals("Wood_Planks_Oak", newBreaking.getItemId(),
                "Slab should drop its recipe ingredient (planks)");
        assertEquals(6, newBreaking.getQuantity(),
                "Drop quantity should be inputQty(12) / outputQty(2) = 6");
    }

    @Test
    void railDropsCorrectIngredientQuantity() {
        RecipeDropModifier.apply();

        BlockGathering railGathering = data.railIron.getGathering();
        BlockBreakingDropType newBreaking = readGatheringBreaking(railGathering);
        assertNotNull(newBreaking);

        // Recipe: 2x Metal_Ingot_Iron → 1x Rail_Iron
        // After CraftingCostModifier: 2 * 12 = 24
        // Drop qty = 24 / 1 = 24
        assertEquals("Metal_Ingot_Iron", newBreaking.getItemId());
        assertEquals(24, newBreaking.getQuantity(),
                "Rail should drop 24 ingots (2 * 12 / 1)");
    }

    @Test
    void doorDropsCorrectIngredientQuantity() {
        RecipeDropModifier.apply();

        BlockGathering doorGathering = data.doorWoodOak.getGathering();
        BlockBreakingDropType newBreaking = readGatheringBreaking(doorGathering);
        assertNotNull(newBreaking);

        // Recipe: 2x Wood_Planks_Oak → 1x Door_Wood_Oak
        // After CraftingCostModifier: 2 * 12 = 24
        // Drop qty = 24 / 1 = 24
        assertEquals("Wood_Planks_Oak", newBreaking.getItemId());
        assertEquals(24, newBreaking.getQuantity(),
                "Door should drop 24 planks (2 * 12 / 1)");
    }

    @Test
    void baseBlockRecipeIsSkipped() {
        RecipeDropModifier.apply();

        BlockGathering planksGathering = data.woodPlanksOak.getGathering();
        BlockBreakingDropType breaking = readGatheringBreaking(planksGathering);

        // Planks is a base block (all-natural inputs) — should NOT be replaced
        assertEquals("Wood_Planks_Oak", breaking.getItemId(),
                "Base block (planks) should keep its original drop, not be replaced");
        assertEquals(1, breaking.getQuantity(),
                "Base block should keep original quantity");
    }

    @Test
    void gatherTypePreservedOnReplacement() {
        RecipeDropModifier.apply();

        BlockGathering slabGathering = data.woodSlabOak.getGathering();
        BlockBreakingDropType newBreaking = readGatheringBreaking(slabGathering);

        assertEquals("Woods", newBreaking.getGatherType(),
                "GatherType should be preserved from original breaking config");
    }

    @Test
    void qualityPreservedOnReplacement() {
        RecipeDropModifier.apply();

        BlockGathering railGathering = data.railIron.getGathering();
        BlockBreakingDropType newBreaking = readGatheringBreaking(railGathering);

        assertEquals(1, newBreaking.getQuality(),
                "Quality should be preserved from original breaking config");
    }

    @Test
    void dropQuantityNeverBelowOne() {
        // Create a recipe with very low input ratio
        var tinyRecipe = recipe("Tiny_Recipe",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                materialQty("Tiny_Block", 100),
                com.hypixel.hytale.protocol.BenchType.StructuralCrafting);

        var tinyItem = item("Tiny_Block", "Tiny_Block", true, 100);
        var tinyBreaking = new BlockBreakingDropType("Rocks", 0, 1, "Tiny_Block", null);
        var tinyBlock = blockType("Tiny_Block", gathering(tinyBreaking, null, null, null));

        data.blockTypes.put("Tiny_Block", tinyBlock);
        data.items.put("Tiny_Block", tinyItem);
        data.recipesById.put("Tiny_Recipe", tinyRecipe);
        data.recipesByBlockType.put("Tiny_Block", tinyRecipe);
        data.install();
        CraftingCostModifier.apply();

        RecipeDropModifier.apply();

        BlockBreakingDropType newBreaking = readGatheringBreaking(tinyBlock.getGathering());
        // Input 1 * 12 = 12, output qty 100 → 12 / 100 = 0 → clamped to 1
        assertTrue(newBreaking.getQuantity() >= 1,
                "Drop quantity should never fall below 1");
    }

    @Test
    void blockWithNoGatheringSkipped() {
        // Add a recipe block with no gathering config
        var noGatherRecipe = recipe("NoGather_Recipe",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                materialQty("NoGather_Block", 1),
                com.hypixel.hytale.protocol.BenchType.StructuralCrafting);
        var noGatherItem = item("NoGather_Block", "NoGather_Block", true, 100);
        var noGatherBlock = blockType("NoGather_Block", null);

        data.blockTypes.put("NoGather_Block", noGatherBlock);
        data.items.put("NoGather_Block", noGatherItem);
        data.recipesById.put("NoGather_Recipe", noGatherRecipe);
        data.recipesByBlockType.put("NoGather_Block", noGatherRecipe);
        data.install();
        CraftingCostModifier.apply();

        // Should not throw
        assertDoesNotThrow(() -> RecipeDropModifier.apply());
    }

    @Test
    void dropListIdNotSetOnNewBreaking() {
        RecipeDropModifier.apply();

        BlockBreakingDropType newBreaking =
                readGatheringBreaking(data.woodSlabOak.getGathering());
        assertNull(newBreaking.getDropListId(),
                "New breaking config should use direct itemId, not dropListId");
    }
}
