package com.CodeCreature.ui.bench;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Owns grid rendering for the Blueprint Bench selection page.
 * Manages cell layout, set group containers, and the indirection map
 * that translates flat slot indices to displayed recipe indices.
 */
public class GridLayoutController {

    // Selected cell highlight styles
    private static final Value<String> CELL_SELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "SelectedCellButtonStyle");
    private static final Value<String> CELL_UNSELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "TransparentButtonStyle");

    private final int totalSetCount;
    private final String[] maxLayoutSetNames;
    private final int[] cellsPerSet;
    private final int[] groupCellOffset;
    private final int totalCellCount;
    private final int[] cellSlotToRecipeIndex;
    private final Map<String, Integer> setNameToGroupIndex;

    public GridLayoutController(int totalSetCount, String[] maxLayoutSetNames,
                                int[] cellsPerSet, int[] groupCellOffset,
                                int totalCellCount, Map<String, Integer> setNameToGroupIndex) {
        this.totalSetCount = totalSetCount;
        this.maxLayoutSetNames = maxLayoutSetNames;
        this.cellsPerSet = cellsPerSet;
        this.groupCellOffset = groupCellOffset;
        this.totalCellCount = totalCellCount;
        this.setNameToGroupIndex = setNameToGroupIndex;
        this.cellSlotToRecipeIndex = new int[totalCellCount];
    }

    /** Binds click events for all grid cells. Called once during build(). */
    public void buildBindings(UIEventBuilder evt) {
        for (int g = 0; g < totalSetCount; g++) {
            for (int c = 0; c < cellsPerSet[g]; c++) {
                int flatIdx = groupCellOffset[g] + c;
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        "#RecipeGridArea[" + g + "] #GroupCells[" + c + "] #Btn",
                        EventData.of("Action", "RecipeSelect:idx:" + flatIdx));
            }
        }
    }

    /** Updates the recipe grid UI. Call whenever displayedRecipes or selectedRecipeId changes. */
    public void updateUI(UICommandBuilder cmd,
                         List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                         String selectedRecipeId) {
        // Reset indirection map
        Arrays.fill(cellSlotToRecipeIndex, -1);

        // Track which groups are used this frame
        boolean[] groupUsed = new boolean[totalSetCount];
        // Track how many cells were filled per group (for hiding remaining)
        int[] cellsFilled = new int[totalSetCount];

        // Walk displayedRecipes (sorted by effectiveSet) and detect set boundaries
        int currentGroupIdx = -1;
        String currentSet = null;

        for (int recipeIdx = 0; recipeIdx < displayedRecipes.size(); recipeIdx++) {
            RecipeFilterPipeline.TaggedRecipe entry = displayedRecipes.get(recipeIdx);

            // Set boundary → look up the fixed group slot for this set
            if (!entry.effectiveSet().equals(currentSet)) {
                currentSet = entry.effectiveSet();
                Integer mappedIdx = setNameToGroupIndex.get(currentSet);
                if (mappedIdx == null) continue; // unknown set — skip
                currentGroupIdx = mappedIdx;
                groupUsed[currentGroupIdx] = true;

                // Show group and set its label
                String groupSel = "#RecipeGridArea[" + currentGroupIdx + "]";
                cmd.set(groupSel + ".Visible", true);
                cmd.set(groupSel + " #SetGroupLabel.Text",
                        RecipeFilterPipeline.setDisplayLabel(currentSet));
            }

            if (currentGroupIdx < 0) continue;

            int cellInGroup = cellsFilled[currentGroupIdx];

            // Overflow within group — skip recipe (no cell slot available)
            if (cellInGroup >= cellsPerSet[currentGroupIdx]) continue;

            // Populate cell
            int globalIdx = groupCellOffset[currentGroupIdx] + cellInGroup;
            String cellSel = "#RecipeGridArea[" + currentGroupIdx + "] #GroupCells[" + cellInGroup + "]";
            cmd.set(cellSel + ".Visible", true);
            cmd.set(cellSel + " #Icon.ItemId", entry.outputItemId());
            cmd.set(cellSel + " #Dim.Visible", !entry.affordable());
            boolean isSelected = entry.recipeId().equals(selectedRecipeId);
            cmd.set(cellSel + " #Btn.Style", isSelected ? CELL_SELECTED_STYLE : CELL_UNSELECTED_STYLE);

            cellSlotToRecipeIndex[globalIdx] = recipeIdx;
            cellsFilled[currentGroupIdx]++;
        }

        // Hide remaining cells in used groups and hide all unused groups
        for (int g = 0; g < totalSetCount; g++) {
            if (groupUsed[g]) {
                hideRemainingCells(cmd, g, cellsFilled[g]);
            } else {
                cmd.set("#RecipeGridArea[" + g + "].Visible", false);
            }
        }
    }

    /**
     * Resolves a flat slot index to the recipe index within displayedRecipes.
     * Returns -1 if the slot is empty.
     */
    public int resolveRecipeIndex(int slotIdx) {
        if (slotIdx < 0 || slotIdx >= totalCellCount) return -1;
        return cellSlotToRecipeIndex[slotIdx];
    }

    public int getTotalCellCount() {
        return totalCellCount;
    }

    /** Hide cells [startCell..cellsPerSet[groupIdx]) in the given group. */
    private void hideRemainingCells(UICommandBuilder cmd, int groupIdx, int startCell) {
        for (int c = startCell; c < cellsPerSet[groupIdx]; c++) {
            String cellSel = "#RecipeGridArea[" + groupIdx + "] #GroupCells[" + c + "]";
            cmd.set(cellSel + ".Visible", false);
            cmd.set(cellSel + " #Btn.Style", CELL_UNSELECTED_STYLE);
        }
    }
}
