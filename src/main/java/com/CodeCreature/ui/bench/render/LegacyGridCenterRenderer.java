package com.CodeCreature.ui.bench.render;

/**
 * @node    LegacyGridCenterRenderer
 * @wiki    docs/wiki/StencilBook/StencilCenterRenderer.md
 * @intent  Adapts the existing fixed-row grid renderer behind the shared center
 *          renderer contract to preserve legacy behavior as a fallback path.
 * @wave    1 (dual renderer migration)
 * @status  Wave 1 - implemented
 * @do-not  Change selector or payload contracts used by GridLayoutController.
 */

import com.CodeCreature.ui.bench.GridLayoutController;
import com.CodeCreature.ui.bench.RecipeFilterPipeline;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import java.util.List;

public final class LegacyGridCenterRenderer implements StencilCenterRenderer {

    private static final String RECIPE_GRID_ROW_UI = "Pages/StencilBook/Components/RecipeGridRow.ui";
    private static final String LABEL_CELL_UI = "Pages/StencilBook/Components/LabelCell.ui";

    private final int totalRowCount;
    private final int cellsPerRow;
    private final GridLayoutController gridController;

    public LegacyGridCenterRenderer(int totalRowCount, int cellsPerRow) {
        this.totalRowCount = totalRowCount;
        this.cellsPerRow = cellsPerRow;
        this.gridController = new GridLayoutController(totalRowCount, cellsPerRow);
    }

    @Override
    public void appendStructure(UICommandBuilder cmd) {
        for (int row = 0; row < totalRowCount; row++) {
            cmd.append("#RecipeGridArea", RECIPE_GRID_ROW_UI);
            for (int col = 0; col < cellsPerRow; col++) {
                cmd.append("#RecipeGridArea[" + row + "] #RowCells", LABEL_CELL_UI);
            }
        }
    }

    @Override
    public void buildBindings(UIEventBuilder evt) {
        gridController.buildBindings(evt);
    }

    @Override
    public void updateUI(UICommandBuilder cmd,
                         List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                         String selectedRecipeId) {
        gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
    }

    @Override
    public void clearOnDismiss(UICommandBuilder cmd) {
        for (int row = 0; row < totalRowCount; row++) {
            cmd.set("#RecipeGridArea[" + row + "].Visible", false);
        }
    }
}