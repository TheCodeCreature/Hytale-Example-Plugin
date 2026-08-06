package com.CodeCreature.crafting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RawProjectionExecutionBoundaryTest {

    @Test
    void stencilPlacementSystemDoesNotCallRawProjectionHelpers() throws IOException {
        String source = readSource("src/main/java/com/CodeCreature/stencil/StencilPlacementSystem.java");

        assertTrue(source.contains("container.removeMaterials(consumptionMaterials"));
        assertFalse(source.contains("RecipeTreeResolver.resolveRecipeToRaw("));
        assertFalse(source.contains("projectDisplayOnlyResolvedInputItemId("));
        assertFalse(source.contains("projectDisplayOnlyResolvedItemId("));
    }

    @Test
    void plannerDoesNotCallRecipeRawProjectionEntryPoint() throws IOException {
        String source = readSource("src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java");

        assertFalse(source.contains("RecipeTreeResolver.resolveRecipeToRaw("));
        assertFalse(source.contains("projectDisplayOnlyResolvedInputItemId("));
        assertFalse(source.contains("projectDisplayOnlyResolvedItemId("));
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
