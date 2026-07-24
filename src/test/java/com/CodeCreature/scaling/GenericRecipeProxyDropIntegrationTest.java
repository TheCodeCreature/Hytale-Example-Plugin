package com.CodeCreature.scaling;

/**
 * @node    GenericRecipeProxyDropIntegrationTest
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Verifies recipe drop projection preserves generic ResourceTypeId inputs as proxy drops
 *          while direct item inputs stay concrete.
 * @wave    3 (integration tests)
 * @status  Wave 3 - integration coverage implemented
 * @do-not  Change runtime parity probe behavior; this test only validates drop projection output shape.
 */

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

class GenericRecipeProxyDropIntegrationTest {

    private TestDataSet data;

    @BeforeEach
    void setUp() {
        data = new TestDataSet();
        data.install();
    }

    /** @intent Generic ResourceTypeId recipe inputs should project to proxy item IDs.
     *  @wave   3 - implemented
     *  @status implemented
     *  @node   GenericRecipeProxyDropIntegrationTest#resourceTypeRecipeInputDropsProxyItem */
    @Test
    void resourceTypeRecipeInputDropsProxyItem() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();
        RecipeDropProjection projection = new RecipeDropProjection(catalog);

        CraftingRecipe recipe = data.recipeKweebecBed;
        List<RecipeDropProjection.ProjectedDrop> drops = projection.projectRecipeDrops(recipe, true);

        assertFalse(drops.isEmpty(), "Expected projected drops for generic-input recipe");
        assertTrue(drops.stream().anyMatch(d -> d.generic() && catalog.isProxyItemId(d.itemId())),
                "Expected at least one generic proxy drop entry");
        assertTrue(drops.stream().anyMatch(d -> "Ingredient_Fibre".equals(d.itemId())),
                "Direct item inputs should still appear as concrete IDs in mixed recipes");
    }

    /** @intent Direct item recipes should remain concrete and never emit proxy IDs.
     *  @wave   3 - implemented
     *  @status implemented
     *  @node   GenericRecipeProxyDropIntegrationTest#directItemInputStillDropsConcreteItem */
    @Test
    void directItemInputStillDropsConcreteItem() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();
        RecipeDropProjection projection = new RecipeDropProjection(catalog);

        CraftingRecipe recipe = data.recipeDoorWood;
        List<RecipeDropProjection.ProjectedDrop> drops = projection.projectRecipeDrops(recipe, false);

        assertFalse(drops.isEmpty(), "Expected projected drops for direct-input recipe");
        assertTrue(drops.stream().allMatch(d -> !catalog.isProxyItemId(d.itemId())),
                "Direct-only recipes must not emit proxy item IDs");
        assertTrue(drops.stream().anyMatch(d -> "Wood_Planks_Oak".equals(d.itemId())),
                "Concrete direct input should remain unchanged");
    }
}
