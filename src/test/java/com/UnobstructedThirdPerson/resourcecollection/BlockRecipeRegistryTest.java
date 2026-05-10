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
        RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void initFindsBuildersRecipes() {
        BenchRecipeRegistries.init();

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
        BenchRecipeRegistries.init();

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        Map<String, CraftingRecipe> byId = reg.getAllRecipesById();
        assertFalse(byId.containsKey("Salvage_Slab_Oak"),
                "Salvage-prefix recipes should be excluded");
    }

    @Test
    void initExcludesNonMatchingBenchIds() {
        BenchRecipeRegistries.init();

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        Map<String, CraftingRecipe> byId = reg.getAllRecipesById();
        assertFalse(byId.containsKey("Ingot_Iron"),
                "Processing bench recipes should be excluded");
    }

    @Test
    void hasRecipeReturnsTrueForRecipeBlocks() {
        BenchRecipeRegistries.init();

        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Wood_Planks_Oak"));
        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Wood_Slab_Oak"));
        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Rail_Iron"));
        assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Door_Wood_Oak"));
    }

    @Test
    void hasRecipeReturnsFalseForNaturalBlocks() {
        BenchRecipeRegistries.init();

        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Rock_Stone"));
        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Wood_Log_Oak"));
        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Dirt"));
    }

    @Test
    void hasRecipeReturnsFalseForUnknownBlocks() {
        BenchRecipeRegistries.init();

        assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Nonexistent_Block"));
    }

    @Test
    void getRecipeForBlockReturnsCorrectRecipe() {
        BenchRecipeRegistries.init();

        CraftingRecipe planks = BenchRecipeRegistries.getRecipeForBlock("Wood_Planks_Oak");
        assertNotNull(planks);
        assertEquals("Planks_Oak", planks.getId());
    }

    @Test
    void getRecipeForBlockReturnsNullForNaturalBlock() {
        BenchRecipeRegistries.init();

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

        RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
        BenchRecipeRegistries.init();

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

        RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
        BenchRecipeRegistries.init();

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

        RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
        BenchRecipeRegistries.init();

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        assertFalse(reg.getAllRecipesById().containsKey("NoBench"),
                "Recipe with no bench requirement should be excluded");
    }

    @Test
    void allRecipesByBlockTypeKeyedCorrectly() {
        BenchRecipeRegistries.init();

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
        BenchRecipeRegistries.init();

        BenchRecipeRegistry reg = BenchRecipeRegistries.getRegistry("Builders");
        assertThrows(UnsupportedOperationException.class,
                () -> reg.getAllRecipesById().put("test", null),
                "recipesById should be unmodifiable");
        assertThrows(UnsupportedOperationException.class,
                () -> reg.getAllRecipesByBlockType().put("test", null),
                "recipesByBlockType should be unmodifiable");
    }
}
