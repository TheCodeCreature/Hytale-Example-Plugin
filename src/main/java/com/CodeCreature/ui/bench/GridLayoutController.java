package com.CodeCreature.ui.bench;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns grid rendering for the Stencil Crafting selection page.
 * Renders set containers with per-set wrapped icon grids, mirroring the sandbox middle-column model.
 */
public class GridLayoutController {

    // Selected cell highlight styles
    private static final Value<String> CELL_SELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "SelectedCellButtonStyle");
    private static final Value<String> CELL_UNSELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "TransparentButtonStyle");

    private final String[] setNames;
    private final int[] cellsPerSet;
    private final Map<String, Integer> setIndexByName;

    public GridLayoutController(String[] setNames, int[] cellsPerSet) {
        this.setNames = setNames == null ? new String[0] : setNames.clone();
        this.cellsPerSet = cellsPerSet == null ? new int[0] : cellsPerSet.clone();

        this.setIndexByName = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < this.setNames.length; i++) {
            this.setIndexByName.put(this.setNames[i], i);
        }
    }

    /** Binds click events for all preallocated recipe icon cells. Called once during build(). */
    public void buildBindings(UIEventBuilder evt) {
        for (int setIndex = 0; setIndex < cellsPerSet.length; setIndex++) {
            int cellCount = Math.max(0, cellsPerSet[setIndex]);
            for (int c = 0; c < cellCount; c++) {
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        "#RecipeGridArea[" + setIndex + "] #GroupCells[" + c + "] #CellBtn",
                        EventData.of("Action", "#RecipeGridArea[" + setIndex + "] #GroupCells[" + c + "] #CellBtn.TooltipText"));
            }
        }
    }

    /** Updates grouped set containers. Call whenever displayedRecipes or selectedRecipeId changes. */
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

        List<Map.Entry<String, List<RecipeFilterPipeline.TaggedRecipe>>> orderedSets = new java.util.ArrayList<>(recipesBySet.entrySet());
        orderedSets.sort(Comparator.comparingInt(e -> setIndexByName.getOrDefault(e.getKey(), Integer.MAX_VALUE)));

        boolean[] setVisible = new boolean[setNames.length];

        for (Map.Entry<String, List<RecipeFilterPipeline.TaggedRecipe>> bySet : orderedSets) {
            Integer setIndex = setIndexByName.get(bySet.getKey());
            if (setIndex == null || setIndex < 0 || setIndex >= setNames.length) {
                continue;
            }

            setVisible[setIndex] = true;
            String setSel = "#RecipeGridArea[" + setIndex + "]";
            List<RecipeFilterPipeline.TaggedRecipe> recipes = bySet.getValue();

            cmd.set(setSel + ".Visible", true);
            cmd.set(setSel + " #SetGroupLabel.Text",
                    Objects.requireNonNull(RecipeFilterPipeline.setDisplayLabel(bySet.getKey())));

            int capacity = setIndex < cellsPerSet.length ? Math.max(0, cellsPerSet[setIndex]) : 0;
            int renderCount = Math.min(capacity, recipes.size());

            for (int i = 0; i < renderCount; i++) {
                RecipeFilterPipeline.TaggedRecipe recipe = recipes.get(i);
                String cellSel = setSel + " #GroupCells[" + i + "]";
                boolean isSelected = recipe.recipeId().equals(selectedRecipeId);

                cmd.set(cellSel + ".Visible", true);
                cmd.set(cellSel + " #CellIcon.ItemId",
                        recipe.outputItemId() == null ? "" : recipe.outputItemId());
                cmd.set(cellSel + " #CellDim.Visible", !recipe.affordable());
                cmd.set(cellSel + " #CellBtn.TooltipText", "RecipeSelect:rid:" + recipe.recipeId());
                cmd.set(cellSel + " #CellBtn.Style",
                        Objects.requireNonNull(isSelected ? CELL_SELECTED_STYLE : CELL_UNSELECTED_STYLE));
            }

            for (int i = renderCount; i < capacity; i++) {
                clearCell(cmd, setSel + " #GroupCells[" + i + "]");
            }
        }

        for (int setIndex = 0; setIndex < setNames.length; setIndex++) {
            if (setVisible[setIndex]) {
                continue;
            }
            String setSel = "#RecipeGridArea[" + setIndex + "]";
            cmd.set(setSel + ".Visible", false);
            cmd.set(setSel + " #SetGroupLabel.Text", "");

            int capacity = setIndex < cellsPerSet.length ? Math.max(0, cellsPerSet[setIndex]) : 0;
            for (int i = 0; i < capacity; i++) {
                clearCell(cmd, setSel + " #GroupCells[" + i + "]");
            }
        }
    }

    private void clearCell(UICommandBuilder cmd, String cellSel) {
        cmd.set(cellSel + ".Visible", false);
        cmd.set(cellSel + " #CellIcon.ItemId", "");
        cmd.set(cellSel + " #CellDim.Visible", false);
        cmd.set(cellSel + " #CellBtn.Style", Objects.requireNonNull(CELL_UNSELECTED_STYLE));
        cmd.set(cellSel + " #CellBtn.TooltipText", "");
    }
}
