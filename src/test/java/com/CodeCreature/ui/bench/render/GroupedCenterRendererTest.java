package com.CodeCreature.ui.bench.render;

/**
 * @node    GroupedCenterRendererTest
 * @wiki    docs/wiki/StencilBook/StencilCenterRenderer.md
 * @intent  Verifies grouped renderer data transformation preserves displayed
 *          recipe order and payload formatting contract expected by page events.
 * @wave    1 (dual renderer migration)
 * @status  Wave 1 - tests added
 * @do-not  Assert pipeline sorting here; this test only validates renderer mapping.
 */

import com.CodeCreature.ui.bench.RecipeFilterPipeline;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupedCenterRendererTest {

    @Test
    void transformPreservesEncounterOrderAndRecipeOrderWithinGroups() {
        List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes = List.of(
                tagged("recipeB1", "Set_B"),
                tagged("recipeA1", "Set_A"),
                tagged("recipeA2", "Set_A"),
                tagged("recipeB2", "Set_B")
        );

        List<GroupedCenterRenderer.RenderGroup> groups = GroupedCenterRenderer.toRenderGroups(displayedRecipes);

        assertEquals(2, groups.size());
        assertEquals("Set_B", groups.get(0).effectiveSet());
        assertEquals(List.of("recipeB1", "recipeB2"),
                groups.get(0).recipes().stream().map(RecipeFilterPipeline.TaggedRecipe::recipeId).toList());

        assertEquals("Set_A", groups.get(1).effectiveSet());
        assertEquals(List.of("recipeA1", "recipeA2"),
                groups.get(1).recipes().stream().map(RecipeFilterPipeline.TaggedRecipe::recipeId).toList());
    }

    @Test
    void transformNormalizesMissingSetToUncategorized() {
        List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes = List.of(
                tagged("recipe1", null),
                tagged("recipe2", "")
        );

        List<GroupedCenterRenderer.RenderGroup> groups = GroupedCenterRenderer.toRenderGroups(displayedRecipes);

        assertEquals(1, groups.size());
        assertEquals("Uncategorized", groups.get(0).effectiveSet());
        assertEquals(List.of("recipe1", "recipe2"),
                groups.get(0).recipes().stream().map(RecipeFilterPipeline.TaggedRecipe::recipeId).toList());
    }

    @Test
    void payloadMatchesExistingRecipeSelectContract() {
        assertEquals("RecipeSelect:rid:my_recipe_id",
                GroupedCenterRenderer.toRecipeSelectPayload("my_recipe_id"));
    }

    private static RecipeFilterPipeline.TaggedRecipe tagged(String recipeId, String effectiveSet) {
        Set<String> benches = new LinkedHashSet<>();
        benches.add("All");
        return new RecipeFilterPipeline.TaggedRecipe(
                recipeId,
                "item_" + recipeId,
                "block_" + recipeId,
                benches,
                effectiveSet,
                true,
                List.of()
        );
    }
}