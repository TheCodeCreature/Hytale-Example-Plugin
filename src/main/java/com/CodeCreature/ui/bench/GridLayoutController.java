package com.CodeCreature.ui.bench;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns grid rendering for the Stencil Crafting selection page.
 * Option-1 row model: fixed row containers with fixed cells-per-row,
 * populated contiguously each frame to avoid phantom gaps.
 */
public class GridLayoutController {

    // Selected cell highlight styles
    private static final Value<String> CELL_SELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "SelectedCellButtonStyle");
    private static final Value<String> CELL_UNSELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "TransparentButtonStyle");

    private final int totalRowCount;
    private final int cellsPerRow;

    public GridLayoutController(int totalRowCount, int cellsPerRow) {
        this.totalRowCount = totalRowCount;
        this.cellsPerRow = cellsPerRow;
    }

    /** Binds click events for all grid cells. Called once during build(). */
    public void buildBindings(UIEventBuilder evt) {
        for (int r = 0; r < totalRowCount; r++) {
            for (int c = 0; c < cellsPerRow; c++) {
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        "#RecipeGridArea[" + r + "] #RowCells[" + c + "] #RecipeCell #Btn",
                        EventData.of("Action", "#RecipeGridArea[" + r + "] #RowCells[" + c + "] #RecipeCell #RecipeAction.Text"));
            }
        }
    }

    /** Updates the recipe grid UI. Call whenever displayedRecipes or selectedRecipeId changes. */
    public void updateUI(UICommandBuilder cmd,
                         List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                         String selectedRecipeId) {
        Map<String, List<RecipeFilterPipeline.TaggedRecipe>> recipesBySet = new LinkedHashMap<>();
        for (RecipeFilterPipeline.TaggedRecipe entry : displayedRecipes) {
            String effectiveSet = entry.effectiveSet();
            if (effectiveSet == null || effectiveSet.isEmpty()) {
                effectiveSet = RecipeFilterPipeline.UNCATEGORIZED_SET;
            }
            recipesBySet.computeIfAbsent(effectiveSet, ignored -> new ArrayList<>()).add(entry);
        }

        List<RenderCell> renderCells = new ArrayList<>();
        for (Map.Entry<String, List<RecipeFilterPipeline.TaggedRecipe>> bySet : recipesBySet.entrySet()) {
            String setName = bySet.getKey();
            List<RecipeFilterPipeline.TaggedRecipe> recipes = bySet.getValue();
            renderCells.add(RenderCell.separator(
                Objects.requireNonNull(RecipeFilterPipeline.setDisplayLabel(setName))));

            for (RecipeFilterPipeline.TaggedRecipe entry : recipes) {
                renderCells.add(RenderCell.recipe(entry));
            }
        }

        int renderIdx = 0;
        int row = 0;
        int col = 0;

        while (row < totalRowCount && renderIdx < renderCells.size()) {
            String rowSel = "#RecipeGridArea[" + row + "]";
            cmd.set(rowSel + ".Visible", true);

            RenderCell renderCell = renderCells.get(renderIdx);
            String cellSel = rowSel + " #RowCells[" + col + "]";

            if (renderCell.separator()) {
                // Keep 2-cell separator together. If one slot remains, push to next row.
                if (col == cellsPerRow - 1) {
                    clearCell(cmd, cellSel);
                    row++;
                    col = 0;
                    continue;
                }

                cmd.set(cellSel + ".Visible", true);
                cmd.set(cellSel + " #RecipeCell.Visible", false);
                cmd.set(cellSel + " #CategoryCell.Visible", true);
                cmd.set(cellSel + " #CategoryCell #CategoryBtn.Text", Objects.requireNonNull(renderCell.labelText()));

                String nextCellSel = rowSel + " #RowCells[" + (col + 1) + "]";
                cmd.set(nextCellSel + ".Visible", true);
                cmd.set(nextCellSel + " #RecipeCell.Visible", false);
                cmd.set(nextCellSel + " #RecipeCell #RecipeAction.Text", "");
                cmd.set(nextCellSel + " #CategoryCell.Visible", true);
                cmd.set(nextCellSel + " #CategoryCell #CategoryBtn.Text", "");

                col += 2;
                renderIdx++;
            } else {
                RecipeFilterPipeline.TaggedRecipe entry = renderCell.recipe();
                cmd.set(cellSel + ".Visible", true);
                cmd.set(cellSel + " #CategoryCell.Visible", false);
                cmd.set(cellSel + " #RecipeCell.Visible", true);

                String outputItemId = renderCell.iconItemId();
                cmd.set(cellSel + " #RecipeCell #Icon.ItemId", Objects.requireNonNull(outputItemId));
                cmd.set(cellSel + " #RecipeCell #Dim.Visible", !entry.affordable());
                cmd.set(cellSel + " #RecipeCell #RecipeAction.Text", "RecipeSelect:rid:" + entry.recipeId());
                boolean isSelected = entry.recipeId().equals(selectedRecipeId);
                cmd.set(cellSel + " #RecipeCell #Btn.Style",
                        Objects.requireNonNull(isSelected ? CELL_SELECTED_STYLE : CELL_UNSELECTED_STYLE));

                col++;
                renderIdx++;
            }

            if (col >= cellsPerRow) {
                row++;
                col = 0;
            }
        }

        // Clear any unfilled slots in the partially filled row.
        if (row < totalRowCount && col > 0) {
            String rowSel = "#RecipeGridArea[" + row + "]";
            cmd.set(rowSel + ".Visible", true);
            for (int c = col; c < cellsPerRow; c++) {
                clearCell(cmd, rowSel + " #RowCells[" + c + "]");
            }
            row++;
        }

        // Hide remaining rows completely.
        for (int r = row; r < totalRowCount; r++) {
            String rowSel = "#RecipeGridArea[" + r + "]";
            cmd.set(rowSel + ".Visible", false);
            for (int c = 0; c < cellsPerRow; c++) {
                clearCell(cmd, rowSel + " #RowCells[" + c + "]");
            }
        }
    }

    private void clearCell(UICommandBuilder cmd, String cellSel) {
        cmd.set(cellSel + ".Visible", false);
        cmd.set(cellSel + " #CategoryCell.Visible", false);
        cmd.set(cellSel + " #CategoryCell #CategoryBtn.Text", "");
        cmd.set(cellSel + " #RecipeCell.Visible", false);
        cmd.set(cellSel + " #RecipeCell #Dim.Visible", false);
        cmd.set(cellSel + " #RecipeCell #Btn.Style", Objects.requireNonNull(CELL_UNSELECTED_STYLE));
        cmd.set(cellSel + " #RecipeCell #RecipeAction.Text", "");
    }

    private record RenderCell(boolean separator,
                              String labelText,
                              String iconItemId,
                              RecipeFilterPipeline.TaggedRecipe recipe) {
        static RenderCell separator(String labelText) {
            return new RenderCell(true, labelText, "", null);
        }

        static RenderCell recipe(RecipeFilterPipeline.TaggedRecipe recipe) {
            String itemId = recipe.outputItemId();
            return new RenderCell(false, "", itemId == null ? "" : itemId, recipe);
        }
    }
}
