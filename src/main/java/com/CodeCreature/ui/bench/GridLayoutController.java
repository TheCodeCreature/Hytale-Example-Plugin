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
    private final int maxTilesPerRow;
    private final List<RowSpec> rowSpecs;

    public GridLayoutController(String[] setNames, int[] cellsPerSet, int maxTilesPerRow) {
        this.setNames = setNames == null ? new String[0] : setNames.clone();
        this.cellsPerSet = cellsPerSet == null ? new int[0] : cellsPerSet.clone();
        this.maxTilesPerRow = Math.max(1, maxTilesPerRow);

        List<List<GroupSeed>> rowSeeds = buildRowSeeds();
        this.rowSpecs = new ArrayList<>(rowSeeds.size());

        for (int rowIndex = 0; rowIndex < rowSeeds.size(); rowIndex++) {
            List<GroupSeed> seeds = rowSeeds.get(rowIndex);
            List<GroupSlot> slots = new ArrayList<>(seeds.size());
            for (int groupIndex = 0; groupIndex < seeds.size(); groupIndex++) {
                GroupSeed seed = seeds.get(groupIndex);
                GroupSlot slot = new GroupSlot(rowIndex, groupIndex, seed.capacity());
                slots.add(slot);
            }
            rowSpecs.add(new RowSpec(slots));
        }
    }

    public int getRowCount() {
        return rowSpecs.size();
    }

    public int getGroupCountInRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowSpecs.size()) {
            return 0;
        }
        return rowSpecs.get(rowIndex).groups().size();
    }

    public int getGroupCapacity(int rowIndex, int groupIndexInRow) {
        if (rowIndex < 0 || rowIndex >= rowSpecs.size()) {
            return 0;
        }
        List<GroupSlot> groups = rowSpecs.get(rowIndex).groups();
        if (groupIndexInRow < 0 || groupIndexInRow >= groups.size()) {
            return 0;
        }
        return groups.get(groupIndexInRow).capacity();
    }

    /** Binds click events for all preallocated recipe icon cells. Called once during build(). */
    public void buildBindings(UIEventBuilder evt) {
        for (RowSpec row : rowSpecs) {
            for (GroupSlot slot : row.groups()) {
                for (int c = 0; c < slot.capacity(); c++) {
                    String base = groupSelector(slot) + " #GroupCells[" + c + "]";
                    evt.addEventBinding(CustomUIEventBindingType.Activating,
                            base + " #CellBtn",
                            EventData.of("Action", base + " #CellBtn.TooltipText"));
                }
            }
        }
    }

    /** Updates grouped set containers. Call whenever displayedRecipes or selectedRecipeId changes. */
    public void updateUI(UICommandBuilder cmd,
                         List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                         String selectedRecipeId) {
        // Recompute per-set placement from the current filtered view so row/group
        // packing reflows whenever tab/search/material/set filters change.
        Map<String, List<RecipeFilterPipeline.TaggedRecipe>> recipesBySet = new LinkedHashMap<>();
        for (RecipeFilterPipeline.TaggedRecipe entry : displayedRecipes) {
            String effectiveSet = entry.effectiveSet();
            if (effectiveSet == null || effectiveSet.isEmpty()) {
                effectiveSet = RecipeFilterPipeline.UNCATEGORIZED_SET;
            }
            recipesBySet.computeIfAbsent(effectiveSet, ignored -> new ArrayList<>()).add(entry);
        }

        boolean[] rowVisible = new boolean[rowSpecs.size()];
        List<GroupSlot> orderedSlots = new ArrayList<>();
        for (RowSpec row : rowSpecs) {
            orderedSlots.addAll(row.groups());
        }

        List<RenderChunk> chunks = new ArrayList<>();
        for (Map.Entry<String, List<RecipeFilterPipeline.TaggedRecipe>> bySet : recipesBySet.entrySet()) {
            String setLabel = Objects.requireNonNull(RecipeFilterPipeline.setDisplayLabel(bySet.getKey()));
            List<RecipeFilterPipeline.TaggedRecipe> recipes = bySet.getValue();
            chunks.add(new RenderChunk(setLabel, recipes));
        }

        boolean[] slotUsed = new boolean[orderedSlots.size()];
        int searchStart = 0;

        for (RenderChunk chunk : chunks) {
            int slotIndex = findSlotIndex(orderedSlots, slotUsed, searchStart, chunk.recipes().size());
            if (slotIndex < 0) {
                continue;
            }

            GroupSlot slot = orderedSlots.get(slotIndex);
            slotUsed[slotIndex] = true;
            searchStart = slotIndex + 1;

            String slotSel = groupSelector(slot);
            int renderCount = chunk.recipes().size();

            rowVisible[slot.rowIndex()] = true;
            cmd.set(slotSel + ".Visible", true);
            cmd.set(slotSel + " #SetGroupLabel.Text", Objects.requireNonNull(chunk.setLabel()));
            cmd.set(slotSel + ".FlexWeight", renderCount);

            for (int i = 0; i < renderCount; i++) {
                RecipeFilterPipeline.TaggedRecipe recipe = chunk.recipes().get(i);
                String cellSel = slotSel + " #GroupCells[" + i + "]";
                boolean isSelected = recipe.recipeId().equals(selectedRecipeId);
                String outputItemId = recipe.outputItemId() == null ? "" : recipe.outputItemId();

                cmd.set(cellSel + ".Visible", true);
                cmd.set(cellSel + " #CellIcon.ItemId", Objects.requireNonNull(outputItemId));
                cmd.set(cellSel + " #CellDim.Visible", !recipe.affordable());
                cmd.set(cellSel + " #CellBtn.TooltipText", "RecipeSelect:rid:" + recipe.recipeId());
                cmd.set(cellSel + " #CellBtn.Style",
                        Objects.requireNonNull(isSelected ? CELL_SELECTED_STYLE : CELL_UNSELECTED_STYLE));
            }

            for (int i = renderCount; i < slot.capacity(); i++) {
                clearCell(cmd, slotSel + " #GroupCells[" + i + "]");
            }
        }

        for (int i = 0; i < orderedSlots.size(); i++) {
            if (!slotUsed[i]) {
                hideSlot(cmd, orderedSlots.get(i));
            }
        }

        for (int rowIndex = 0; rowIndex < rowSpecs.size(); rowIndex++) {
            cmd.set(rowSelector(rowIndex) + ".Visible", rowVisible[rowIndex]);
        }
    }

    private List<List<GroupSeed>> buildRowSeeds() {
        List<List<GroupSeed>> rows = new ArrayList<>();
        List<GroupSeed> currentRow = new ArrayList<>();

        for (int i = 0; i < setNames.length; i++) {
            String setName = setNames[i];
            int tileCount = (i < cellsPerSet.length) ? Math.max(0, cellsPerSet[i]) : 0;

            if (tileCount > maxTilesPerRow) {
                if (!currentRow.isEmpty()) {
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                }

                List<GroupSeed> oversizedRow = new ArrayList<>(1);
                oversizedRow.add(new GroupSeed(setName, tileCount));
                rows.add(oversizedRow);
            } else {
                currentRow.add(new GroupSeed(setName, tileCount));
            }
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        return rows;
    }

    private String rowSelector(int rowIndex) {
        return "#RecipeGridArea[" + rowIndex + "]";
    }

    private String groupSelector(GroupSlot slot) {
        return rowSelector(slot.rowIndex()) + " #RowGroups[" + slot.groupIndexInRow() + "]";
    }

    private int findSlotIndex(List<GroupSlot> orderedSlots, boolean[] slotUsed, int startIndex, int requiredCapacity) {
        for (int i = Math.max(0, startIndex); i < orderedSlots.size(); i++) {
            if (!slotUsed[i] && orderedSlots.get(i).capacity() >= requiredCapacity) {
                return i;
            }
        }

        for (int i = 0; i < Math.max(0, startIndex); i++) {
            if (!slotUsed[i] && orderedSlots.get(i).capacity() >= requiredCapacity) {
                return i;
            }
        }

        return -1;
    }

    private void hideSlot(UICommandBuilder cmd, GroupSlot slot) {
        String slotSel = groupSelector(slot);
        cmd.set(slotSel + ".Visible", false);
        cmd.set(slotSel + ".FlexWeight", 0);
        cmd.set(slotSel + " #SetGroupLabel.Text", "");
        for (int i = 0; i < slot.capacity(); i++) {
            clearCell(cmd, slotSel + " #GroupCells[" + i + "]");
        }
    }

    private void clearCell(UICommandBuilder cmd, String cellSel) {
        cmd.set(cellSel + ".Visible", false);
        cmd.set(cellSel + " #CellIcon.ItemId", "");
        cmd.set(cellSel + " #CellDim.Visible", false);
        cmd.set(cellSel + " #CellBtn.Style", Objects.requireNonNull(CELL_UNSELECTED_STYLE));
        cmd.set(cellSel + " #CellBtn.TooltipText", "");
    }

    private record GroupSeed(String setName, int capacity) {}

    private record GroupSlot(int rowIndex, int groupIndexInRow, int capacity) {}

    private record RenderChunk(String setLabel, List<RecipeFilterPipeline.TaggedRecipe> recipes) {}

    private record RowSpec(List<GroupSlot> groups) {}
}
