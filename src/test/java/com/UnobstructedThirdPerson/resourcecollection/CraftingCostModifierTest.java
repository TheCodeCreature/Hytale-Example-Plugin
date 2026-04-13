package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CraftingCostModifier}.
 * Validates that recipe input quantities are multiplied by RESOURCE_MULTIPLIER
 * and that base block recipes are skipped.
 */
class CraftingCostModifierTest {

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
    void nonBaseRecipeInputsScaledBy12x() {
        CraftingCostModifier.apply();

        MaterialQuantity[] slabInputs = readRecipeInputs(data.recipeSlabOak);
        assertNotNull(slabInputs);
        assertEquals(1, slabInputs.length, "Slab recipe should have 1 input");
        assertEquals(1 * ResourceConstants.RESOURCE_MULTIPLIER, slabInputs[0].getQuantity(),
                "Slab input (Wood_Planks_Oak) should be scaled: 1 * 12 = 12");
    }

    @Test
    void multiInputRecipeAllScaled() {
        // Rail recipe: 2x Metal_Ingot_Iron
        CraftingCostModifier.apply();

        MaterialQuantity[] railInputs = readRecipeInputs(data.recipeRailIron);
        assertNotNull(railInputs);
        assertEquals(2 * ResourceConstants.RESOURCE_MULTIPLIER, railInputs[0].getQuantity(),
                "Rail input should be scaled: 2 * 12 = 24");
    }

    @Test
    void baseBlockRecipeNotScaled() {
        CraftingCostModifier.apply();

        MaterialQuantity[] planksInputs = readRecipeInputs(data.recipePlanksOak);
        assertNotNull(planksInputs);
        assertEquals(1, planksInputs[0].getQuantity(),
                "Base block (planks) input should remain at 1x");
    }

    @Test
    void scaledInputPreservesItemId() {
        CraftingCostModifier.apply();

        MaterialQuantity[] slabInputs = readRecipeInputs(data.recipeSlabOak);
        assertEquals("Wood_Planks_Oak", slabInputs[0].getItemId(),
                "Scaled input should preserve the original itemId");
    }

    @Test
    void doorRecipeScaledCorrectly() {
        CraftingCostModifier.apply();

        MaterialQuantity[] doorInputs = readRecipeInputs(data.recipeDoorWood);
        assertNotNull(doorInputs);
        assertEquals(2 * ResourceConstants.RESOURCE_MULTIPLIER, doorInputs[0].getQuantity(),
                "Door input should be scaled: 2 * 12 = 24");
    }

    @Test
    void applyIsIdempotentForBaseBlocks() {
        // Apply twice — base blocks should still be 1x
        CraftingCostModifier.apply();
        CraftingCostModifier.apply();

        MaterialQuantity[] planksInputs = readRecipeInputs(data.recipePlanksOak);
        assertEquals(1, planksInputs[0].getQuantity(),
                "Base block should remain at 1x even after double apply");
    }

    @Test
    void nonBaseRecipesDoubleApplyCausesDoubleScaling() {
        // This verifies that apply() is NOT idempotent for non-base recipes
        // (important to know — should only be called once)
        CraftingCostModifier.apply();
        CraftingCostModifier.apply();

        MaterialQuantity[] railInputs = readRecipeInputs(data.recipeRailIron);
        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(2 * m * m, railInputs[0].getQuantity(),
                "Double-apply scales twice (this is expected non-idempotent behavior)");
    }

    @Test
    void emptyRecipeRegistryDoesNothing() {
        setBlockRecipeRegistry(
                new java.util.HashMap<>(),
                new java.util.HashMap<>(),
                new java.util.HashSet<>());

        assertDoesNotThrow(() -> CraftingCostModifier.apply());
    }

    @Test
    void nullInputArrayHandledGracefully() {
        // Recipe with null inputs
        CraftingRecipe nullInputRecipe = recipe("NullInput",
                null,
                materialQty("Some_Block", 1),
                com.hypixel.hytale.protocol.BenchType.StructuralCrafting);

        data.recipesById.put("NullInput", nullInputRecipe);
        data.recipesByBlockType.put("Some_Block", nullInputRecipe);
        data.install();

        assertDoesNotThrow(() -> CraftingCostModifier.apply());
    }
}
