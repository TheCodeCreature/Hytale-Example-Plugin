package com.CodeCreature.ui.bench;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFilterPipelineSearchTest {

    private final RecipeFilterPipeline pipeline = new RecipeFilterPipeline();

    @Test
    void fuzzySearchMatchesIdsNamesAndDescriptions() {
        List<RecipeFilterPipeline.InputRecipe> recipes = List.of(
                new RecipeFilterPipeline.InputRecipe(
                        "Wood_Hardwood_Planks",
                        "Hardwood_Planks",
                        "Hardwood_Planks",
                        Set.of("Carpentry"),
                        "Wood",
                        List.of("Wood"),
                        "Hardwood Planks",
                        "Sturdy building material"
                ),
                new RecipeFilterPipeline.InputRecipe(
                        "Stone_Brick",
                        "Stone_Brick",
                        "Stone_Brick",
                        Set.of("Masonry"),
                        "Stone",
                        List.of("Rock"),
                        "Stone Brick",
                        "Masonry block"
                ),
                new RecipeFilterPipeline.InputRecipe(
                        "Decorative_Test",
                        "Decorative_Test",
                        "Decorative_Test",
                        Set.of("Decor"),
                        "Decor",
                        List.of("Decor"),
                        "Decorative Test",
                        "Crystal lodge totem helm"
                ),
                new RecipeFilterPipeline.InputRecipe(
                        "Clay_Brick",
                        "Clay_Brick",
                        "Clay_Brick",
                        Set.of("Masonry"),
                        "Stone",
                        List.of("Rock"),
                        "Clay Brick",
                        "Clay based masonry block"
                )
        );

        assertEquals(1, pipeline.filterBySearch(recipes, "hardwood").size(), "ID/name search should match");
        assertEquals(1, pipeline.filterBySearch(recipes, "hrdwd").size(), "Typo fuzzy match should match");
        assertEquals(1, pipeline.filterBySearch(recipes, "hwp").size(), "Subsequence fuzzy match should match");
        assertEquals(1, pipeline.filterBySearch(recipes, "sturdy material").size(), "Description search should match");
        assertEquals(0L, pipeline.filterBySearch(recipes, "cloth" ).stream()
                .filter(r -> "Decorative_Test".equals(r.recipeId()))
                .count(), "Long-word subsequence false positives should be rejected");
        assertEquals(0L, pipeline.filterBySearch(recipes, "cloth" ).stream()
                .filter(r -> "Clay_Brick".equals(r.recipeId()))
                .count(), "Cloth should not fuzzy-match clay");
        assertEquals(0, pipeline.filterBySearch(recipes, "obsidian").size(), "Unrelated query should not match");
    }

    @Test
    void multiTermQueryRequiresAllTermsAcrossSearchableFields() {
        List<RecipeFilterPipeline.InputRecipe> recipes = List.of(
                new RecipeFilterPipeline.InputRecipe(
                        "Wood_Hardwood_Planks",
                        "Hardwood_Planks",
                        "Hardwood_Planks",
                        Set.of("Carpentry"),
                        "Wood",
                        List.of("Wood"),
                        "Hardwood Planks",
                        "Sturdy building material"
                )
        );

        List<RecipeFilterPipeline.InputRecipe> matches = pipeline.filterBySearch(recipes, "hardwood sturdy");
        assertEquals(1, matches.size());
        assertTrue(matches.get(0).recipeId().contains("Hardwood"));

        assertEquals(0, pipeline.filterBySearch(recipes, "hardwood obsidian").size());
    }
}
