package com.CodeCreature.crafting;

/**
 * @node    RawProjectionExecutionBoundaryTest
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Guards the non-runtime contract that execution/removal call paths do not invoke
 *          RecipeTreeResolver display projection helpers.
 * @wave    5 (surface parity + regression coverage)
 * @status  Wave 5 - implemented source-level boundary guards for projection helper isolation
 * @do-not  Treat these tests as runtime proof of engine inventory internals.
 *          Expand this suite into live parity probe coverage.
 */

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawProjectionExecutionBoundaryTest {

    /** @intent Execution boundary should remove explicit planner consumptions and avoid raw projection APIs.
     *  @wave   5 - implemented stencil placement execution-boundary guard
     *  @status implemented
     *  @node   RawProjectionExecutionBoundaryTest#stencilPlacementSystemDoesNotCallRawProjectionHelpers
     */
    @Test
    void stencilPlacementSystemDoesNotCallRawProjectionHelpers() throws IOException {
        String source = readSource("src/main/java/com/CodeCreature/stencil/StencilPlacementSystem.java");

        assertTrue(source.contains("container.removeMaterials(consumptionMaterials"));
        assertFalse(source.contains("RecipeTreeResolver.resolveRecipeToRaw("));
        assertFalse(source.contains("projectDisplayOnlyResolvedInputItemId("));
        assertFalse(source.contains("projectDisplayOnlyResolvedItemId("));
    }

    /** @intent Planner path should avoid display-projection helper entrypoints when building final consumption plans.
     *  @wave   5 - implemented planner projection-isolation guard
     *  @status implemented
     *  @node   RawProjectionExecutionBoundaryTest#plannerDoesNotCallRecipeRawProjectionEntryPoint
     */
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
