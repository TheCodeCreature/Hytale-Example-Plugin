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
 * Tests for {@link BenchRecipeRegistry} and {@link BenchRecipeRegistries}.
 * Validates that init() correctly identifies recipes by bench ID,
 * filters out Salvage prefixes and wrong bench IDs, and classifies
 * base block recipes.
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
        // Pre-populate NaturalResourceRegistry (BenchRecipeRegistry.init needs it
        // for base block classification)
        setNaturalRegistry(data.naturalBlockIds, data.naturalItemIds);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void initFindsBuildersRecipes() {
        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        assertNotNull(reg);
        Map<String, CraftingRecipe> byId = reg.getAllRecipesById();
        assertTrue(byId.containsKey("Planks_Oak"), "Planks_Oak should be registered");
        assertTrue(byId.containsKey("Slab_Oak"), "Slab_Oak should be registered");
        assertTrue(byId.containsKey("Rail_Iron"), "Rail_Iron should be registered");
        assertTrue(byId.containsKey("Door_Wood"), "Door_Wood should be registered");
    }

    @Test
    void initExcludesSalvageRecipes() {
        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        Map<String, CraftingRecipe> byId = reg.getAllRecipesById();
        assertFalse(byId.containsKey("Salvage_Slab_Oak"),
                "Salvage-prefix recipes should be excluded");
    }

    @Test
    void initExcludesNonMatchingBenchIds() {
        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        Map<String, CraftingRecipe> byId = reg.getAllRecipesById();
        assertFalse(byId.containsKey("Ingot_Iron"),
                "Processing bench recipes should be excluded");
    }

    @Test
    void hasRecipeReturnsTrueForRecipeBlocks() {
        BenchRecipeRegistries.init("Builders");

        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Wood_Planks_Oak"));
        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Wood_Slab_Oak"));
        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Rail_Iron"));
        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Door_Wood_Oak"));
    }

    @Test
    void hasRecipeReturnsFalseForNaturalBlocks() {
        BenchRecipeRegistries.init("Builders");

        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Rock_Stone"));
        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Wood_Log_Oak"));
        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Dirt"));
    }

    @Test
    void hasRecipeReturnsFalseForUnknownBlocks() {
        BenchRecipeRegistries.init("Builders");

        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Nonexistent_Block"));
    }

    @Test
    void baseBlockRecipeIdentified() {
        BenchRecipeRegistries.init("Builders");

        // Planks_Oak: input is Wood_Log_Oak which IS a natural item
        assertTrue(BenchRecipeRegistries.isBaseBlockRecipeAnywhere("Planks_Oak"),
                "Planks_Oak should be a base block recipe (all inputs natural)");
    }

    @Test
    void nonBaseBlockRecipeIdentified() {
        BenchRecipeRegistries.init("Builders");

        // Slab_Oak: input is Wood_Planks_Oak which is NOT a natural item
        assertFalse(BenchRecipeRegistries.isBaseBlockRecipeAnywhere("Slab_Oak"),
                "Slab_Oak should NOT be a base block recipe");
        assertFalse(BenchRecipeRegistries.isBaseBlockRecipeAnywhere("Rail_Iron"),
                "Rail_Iron should NOT be a base block recipe");
    }

    @Test
    void isBaseBlockTypeWorks() {
        BenchRecipeRegistries.init("Builders");

        assertTrue(BenchRecipeRegistries.isBaseBlockTypeAnywhere("Wood_Planks_Oak"),
                "Wood_Planks_Oak should be a base block type");
        assertFalse(BenchRecipeRegistries.isBaseBlockTypeAnywhere("Wood_Slab_Oak"),
                "Wood_Slab_Oak should NOT be a base block type");
    }

    @Test
    void getRecipeForBlockReturnsCorrectRecipe() {
        BenchRecipeRegistries.init("Builders");

        CraftingRecipe planks = BenchRecipeRegistries.getRecipeForBlock("Wood_Planks_Oak");
        assertNotNull(planks);
        assertEquals("Planks_Oak", planks.getId());
    }

    @Test
    void getRecipeForBlockReturnsNullForNaturalBlock() {
        BenchRecipeRegistries.init("Builders");

        assertNull(BenchRecipeRegistries.getRecipeForBlock("Rock_Stone"));
    }

    @Test
    void recipeWithNullPrimaryOutputExcluded() {
        CraftingRecipe nullOutput = recipe("NullOutput",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                null,
                BenchType.StructuralCrafting, "Builders");
        data.recipes.put("NullOutput", nullOutput);
        installRecipes(data.recipes);

        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        assertFalse(reg.getAllRecipesById().containsKey("NullOutput"),
                "Recipe with null primaryOutput should be excluded");
    }

    @Test
    void recipeWithNonBlockOutputExcluded() {
        // Metal_Ingot_Iron has hasBlockType=false and no blockId
        CraftingRecipe nonBlockRecipe = recipe("NonBlock_Output",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                materialQty("Metal_Ingot_Iron", 1),
                BenchType.StructuralCrafting, "Builders");
        data.recipes.put("NonBlock_Output", nonBlockRecipe);
        installRecipes(data.recipes);

        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        assertFalse(reg.getAllRecipesById().containsKey("NonBlock_Output"),
                "Recipe whose output item has no blockId should be excluded");
    }

    @Test
    void recipeWithNoBenchRequirementExcluded() {
        CraftingRecipe noBench = recipe("NoBench",
                new MaterialQuantity[]{materialQty("Rock_Stone", 1)},
                materialQty("Rock_Stone", 1),
                null);  // null benchType = no bench requirement
        data.recipes.put("NoBench", noBench);
        installRecipes(data.recipes);

        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        assertFalse(reg.getAllRecipesById().containsKey("NoBench"),
                "Recipe with no bench requirement should be excluded");
    }

    @Test
    void allRecipesByBlockTypeKeyedCorrectly() {
        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        Map<String, CraftingRecipe> byBlock = reg.getAllRecipesByBlockType();
        // Keys should be block type IDs (from item.getBlockId())
        assertTrue(byBlock.containsKey("Wood_Planks_Oak"));
        assertTrue(byBlock.containsKey("Rail_Iron"));

        // Values should be the recipe objects
        assertEquals("Planks_Oak", byBlock.get("Wood_Planks_Oak").getId());
        assertEquals("Rail_Iron", byBlock.get("Rail_Iron").getId());
    }

    @Test
    void returnsUnmodifiableMaps() {
        BenchRecipeRegistries.init("Builders");

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        assertThrows(UnsupportedOperationException.class,
                () -> reg.getAllRecipesById().put("test", null),
                "recipesById should be unmodifiable");
        assertThrows(UnsupportedOperationException.class,
                () -> reg.getAllRecipesByBlockType().put("test", null),
                "recipesByBlockType should be unmodifiable");
    }
}
