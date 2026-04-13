package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BlockRecipeRegistry}.
 * Validates that init() correctly identifies StructuralCrafting recipes
 * producing placeable blocks, filters out Salvage prefixes and wrong bench
 * types, and classifies base block recipes.
 */
class BlockRecipeRegistryTest {

    private TestDataSet data;

    @BeforeEach
    void setUp() {
        data = new TestDataSet();
        // Install asset stores (BlockType, Item, CraftingRecipe) for registry init
        installBlockTypes(data.blockTypes);
        installItems(data.items);
        installRecipes(data.recipes);
        installDropLists(data.dropLists);
        // Pre-populate NaturalResourceRegistry (BlockRecipeRegistry.init needs it
        // for base block classification)
        setNaturalRegistry(data.naturalBlockIds, data.naturalItemIds);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void initFindsStructuralCraftingRecipes() {
        BlockRecipeRegistry.init();

        Map<String, CraftingRecipe> byId = BlockRecipeRegistry.getAllRecipesById();
        assertTrue(byId.containsKey("Planks_Oak"), "Planks_Oak should be registered");
        assertTrue(byId.containsKey("Slab_Oak"), "Slab_Oak should be registered");
        assertTrue(byId.containsKey("Rail_Iron"), "Rail_Iron should be registered");
        assertTrue(byId.containsKey("Door_Wood"), "Door_Wood should be registered");
    }

    @Test
    void initExcludesSalvageRecipes() {
        BlockRecipeRegistry.init();

        Map<String, CraftingRecipe> byId = BlockRecipeRegistry.getAllRecipesById();
        assertFalse(byId.containsKey("Salvage_Slab_Oak"),
                "Salvage-prefix recipes should be excluded");
    }

    @Test
    void initExcludesNonStructuralBenchTypes() {
        BlockRecipeRegistry.init();

        Map<String, CraftingRecipe> byId = BlockRecipeRegistry.getAllRecipesById();
        assertFalse(byId.containsKey("Ingot_Iron"),
                "Processing bench recipes should be excluded");
    }

    @Test
    void hasRecipeReturnsTrueForRecipeBlocks() {
        BlockRecipeRegistry.init();

        assertTrue(BlockRecipeRegistry.hasRecipe("Wood_Planks_Oak"));
        assertTrue(BlockRecipeRegistry.hasRecipe("Wood_Slab_Oak"));
        assertTrue(BlockRecipeRegistry.hasRecipe("Rail_Iron"));
        assertTrue(BlockRecipeRegistry.hasRecipe("Door_Wood_Oak"));
    }

    @Test
    void hasRecipeReturnsFalseForNaturalBlocks() {
        BlockRecipeRegistry.init();

        assertFalse(BlockRecipeRegistry.hasRecipe("Rock_Stone"));
        assertFalse(BlockRecipeRegistry.hasRecipe("Wood_Log_Oak"));
        assertFalse(BlockRecipeRegistry.hasRecipe("Dirt"));
    }

    @Test
    void hasRecipeReturnsFalseForUnknownBlocks() {
        BlockRecipeRegistry.init();

        assertFalse(BlockRecipeRegistry.hasRecipe("Nonexistent_Block"));
    }

    @Test
    void baseBlockRecipeIdentified() {
        BlockRecipeRegistry.init();

        // Planks_Oak: input is Wood_Log_Oak which IS a natural item
        assertTrue(BlockRecipeRegistry.isBaseBlockRecipe("Planks_Oak"),
                "Planks_Oak should be a base block recipe (all inputs natural)");
    }

    @Test
    void nonBaseBlockRecipeIdentified() {
        BlockRecipeRegistry.init();

        // Slab_Oak: input is Wood_Planks_Oak which is NOT a natural item
        assertFalse(BlockRecipeRegistry.isBaseBlockRecipe("Slab_Oak"),
                "Slab_Oak should NOT be a base block recipe");
        assertFalse(BlockRecipeRegistry.isBaseBlockRecipe("Rail_Iron"),
                "Rail_Iron should NOT be a base block recipe");
    }

    @Test
    void isBaseBlockTypeWorks() {
        BlockRecipeRegistry.init();

        assertTrue(BlockRecipeRegistry.isBaseBlockType("Wood_Planks_Oak"),
                "Wood_Planks_Oak should be a base block type");
        assertFalse(BlockRecipeRegistry.isBaseBlockType("Wood_Slab_Oak"),
                "Wood_Slab_Oak should NOT be a base block type");
    }

    @Test
    void getRecipeForBlockReturnsCorrectRecipe() {
        BlockRecipeRegistry.init();

        CraftingRecipe planks = BlockRecipeRegistry.getRecipeForBlock("Wood_Planks_Oak");
        assertNotNull(planks);
        assertEquals("Planks_Oak", planks.getId());
    }

    @Test
    void getRecipeForBlockReturnsNullForNaturalBlock() {
        BlockRecipeRegistry.init();

        assertNull(BlockRecipeRegistry.getRecipeForBlock("Rock_Stone"));
    }

    @Test
    void recipeWithNullPrimaryOutputExcluded() {
        CraftingRecipe nullOutput = recipe("NullOutput",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                null,
                BenchType.StructuralCrafting);
        data.recipes.put("NullOutput", nullOutput);
        installRecipes(data.recipes);

        BlockRecipeRegistry.init();

        assertFalse(BlockRecipeRegistry.getAllRecipesById().containsKey("NullOutput"),
                "Recipe with null primaryOutput should be excluded");
    }

    @Test
    void recipeWithNonBlockOutputExcluded() {
        // Metal_Ingot_Iron has hasBlockType=false
        CraftingRecipe nonBlockRecipe = recipe("NonBlock_Output",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                materialQty("Metal_Ingot_Iron", 1),
                BenchType.StructuralCrafting);
        data.recipes.put("NonBlock_Output", nonBlockRecipe);
        installRecipes(data.recipes);

        BlockRecipeRegistry.init();

        assertFalse(BlockRecipeRegistry.getAllRecipesById().containsKey("NonBlock_Output"),
                "Recipe whose output item has hasBlockType=false should be excluded");
    }

    @Test
    void recipeWithNoBenchRequirementExcluded() {
        CraftingRecipe noBench = recipe("NoBench",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                materialQty("Rock_Stone", 1),
                null);  // null benchType = no bench requirement
        data.recipes.put("NoBench", noBench);
        installRecipes(data.recipes);

        BlockRecipeRegistry.init();

        assertFalse(BlockRecipeRegistry.getAllRecipesById().containsKey("NoBench"),
                "Recipe with no bench requirement should be excluded");
    }

    @Test
    void allRecipesByBlockTypeKeyedCorrectly() {
        BlockRecipeRegistry.init();

        Map<String, CraftingRecipe> byBlock = BlockRecipeRegistry.getAllRecipesByBlockType();
        // Keys should be block type IDs (from item.getBlockId())
        assertTrue(byBlock.containsKey("Wood_Planks_Oak"));
        assertTrue(byBlock.containsKey("Rail_Iron"));

        // Values should be the recipe objects
        assertEquals("Planks_Oak", byBlock.get("Wood_Planks_Oak").getId());
        assertEquals("Rail_Iron", byBlock.get("Rail_Iron").getId());
    }

    @Test
    void returnsUnmodifiableMaps() {
        BlockRecipeRegistry.init();

        assertThrows(UnsupportedOperationException.class,
                () -> BlockRecipeRegistry.getAllRecipesById().put("test", null),
                "recipesById should be unmodifiable");
        assertThrows(UnsupportedOperationException.class,
                () -> BlockRecipeRegistry.getAllRecipesByBlockType().put("test", null),
                "recipesByBlockType should be unmodifiable");
    }
}
