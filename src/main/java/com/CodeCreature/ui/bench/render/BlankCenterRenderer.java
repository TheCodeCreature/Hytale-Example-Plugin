package com.CodeCreature.ui.bench.render;

import com.CodeCreature.ui.bench.RecipeFilterPipeline;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Sandbox-style center renderer mounted into the production center panel.
 * Uses dynamic displayed recipe data instead of static sandbox sample arrays.
 */
public final class BlankCenterRenderer implements StencilCenterRenderer {

    private static final Logger LOGGER = Logger.getLogger(BlankCenterRenderer.class.getSimpleName());

    private static final String SET_ROW_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxSetRow.ui";
    private static final String SET_GROUP_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxSetGroup.ui";
    private static final String ICON_TILE_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxIconTile.ui";

    private static final String ACTION_PREFIX = "RecipeSelect:rid:";

    private static final int MAX_TILES_PER_ROW = 10;
    private static final int TILE_SIZE_PX = 72;
    private static final int TILE_GAP_PX = 4;
    private static final int CARD_PADDING_X_PX = 8;
    private static final int ROW_ITEM_SPACING_PX = 8;
    private static final int MAX_ROW_WIDTH_PX = 920;
    private static final int MIN_GROUP_WIDTH_PX = 120;

    private static final Value<String> TILE_SELECTED_STYLE = Value.ref("Styles/Buttons.ui", "SelectedCellButtonStyle");
    private static final Value<String> TILE_UNSELECTED_STYLE = Value.ref("Styles/Buttons.ui", "TransparentButtonStyle");

    private final int[] slotCapacities;
    private final int[] slotRowIndices;
    private final int[] slotIndexInRow;
    private final int[][] rowSlotIndices;
    private final int rowCount;

    private final boolean[] slotWasVisible;
    private final int[] slotLastRenderedTileCount;
    private final boolean[] rowWasVisible;

    private String lastLayoutLogSignature = "";

    private boolean appended;

    public BlankCenterRenderer(String[] setNames, int[] cellsPerSet) {
        int inferredSlotCount = Math.max(
                setNames == null ? 0 : setNames.length,
                cellsPerSet == null ? 0 : cellsPerSet.length
        );

        this.slotCapacities = new int[inferredSlotCount];
        for (int i = 0; i < inferredSlotCount; i++) {
            int capacity = (cellsPerSet != null && i < cellsPerSet.length) ? Math.max(0, cellsPerSet[i]) : 0;
            this.slotCapacities[i] = Math.max(1, capacity);
        }

        List<Integer> rowSlotCounts = buildRowSlotCounts(this.slotCapacities);
        this.rowCount = rowSlotCounts.size();
        this.slotRowIndices = new int[this.slotCapacities.length];
        this.slotIndexInRow = new int[this.slotCapacities.length];
        this.rowSlotIndices = new int[this.rowCount][];

        int slotCursor = 0;
        for (int rowIndex = 0; rowIndex < rowSlotCounts.size(); rowIndex++) {
            int slotsInRow = rowSlotCounts.get(rowIndex);
            this.rowSlotIndices[rowIndex] = new int[slotsInRow];
            for (int idxInRow = 0; idxInRow < slotsInRow && slotCursor < this.slotCapacities.length; idxInRow++) {
                this.slotRowIndices[slotCursor] = rowIndex;
                this.slotIndexInRow[slotCursor] = idxInRow;
                this.rowSlotIndices[rowIndex][idxInRow] = slotCursor;
                slotCursor++;
            }
        }

        this.slotWasVisible = new boolean[this.slotCapacities.length];
        this.slotLastRenderedTileCount = new int[this.slotCapacities.length];
        this.rowWasVisible = new boolean[this.rowCount];
    }

    @Override
    public void appendStructure(UICommandBuilder cmd) {
        if (appended) {
            return;
        }

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            cmd.append("#RecipeGridArea", SET_ROW_UI_PATH);
        }

        for (int slotIndex = 0; slotIndex < slotCapacities.length; slotIndex++) {
            cmd.append(rowGroupsSelector(slotRowIndices[slotIndex]), SET_GROUP_UI_PATH);
            int cellCount = slotCapacities[slotIndex];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                cmd.append(groupSelector(slotIndex) + " #Tiles", ICON_TILE_UI_PATH);
            }
        }

        appended = true;
    }

    @Override
    public void buildBindings(UIEventBuilder evt) {
        for (int slotIndex = 0; slotIndex < slotCapacities.length; slotIndex++) {
            int cellCount = slotCapacities[slotIndex];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                String tileSelector = groupSelector(slotIndex) + " #Tiles[" + cellIndex + "]";
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        tileSelector + " #TileBtn",
                        EventData.of("Action", tileSelector + " #TileBtn.TooltipText")
                );
            }
        }
    }

    @Override
    public void updateUI(UICommandBuilder cmd,
                         List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                         String selectedRecipeId) {
        List<GroupViewModel> groups = toGroupViewModels(displayedRecipes);
        List<GroupCardPlacement> placements = computePlacements(groups);

        boolean[] slotActiveNow = new boolean[slotCapacities.length];
        boolean[] rowActiveNow = new boolean[rowCount];
        boolean[] rowSlotTaken = new boolean[slotCapacities.length];
        List<ResolvedPlacement> resolvedPlacements = new ArrayList<>(placements.size());

        for (GroupCardPlacement placement : placements) {
            SlotAssignment assignment = resolveSlotAssignment(placement, rowSlotTaken);
            int slotIndex = assignment.slotIndex();
            if (slotIndex < 0) {
                continue;
            }

            rowSlotTaken[slotIndex] = true;
            slotActiveNow[slotIndex] = true;
            rowActiveNow[slotRowIndices[slotIndex]] = true;
            resolvedPlacements.add(new ResolvedPlacement(placement, slotIndex, assignment.usedFallback()));
            applyGroupToSlot(cmd, slotIndex, placement, selectedRecipeId);
        }

        logLayoutIfChanged(displayedRecipes, groups, placements, resolvedPlacements);

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            if (rowActiveNow[rowIndex]) {
                cmd.set(rowSelector(rowIndex) + ".Visible", true);
                rowWasVisible[rowIndex] = true;
            } else {
                cmd.set(rowSelector(rowIndex) + ".Visible", false);
                rowWasVisible[rowIndex] = false;
            }
        }

        for (int slotIndex = 0; slotIndex < slotCapacities.length; slotIndex++) {
            if (slotActiveNow[slotIndex]) {
                continue;
            }
            clearGroupSlot(cmd, slotIndex);
        }
    }

    @Override
    public void clearOnDismiss(UICommandBuilder cmd) {
        for (int slotIndex = 0; slotIndex < slotCapacities.length; slotIndex++) {
            if (!slotWasVisible[slotIndex] && slotLastRenderedTileCount[slotIndex] == 0) {
                continue;
            }
            clearGroupSlot(cmd, slotIndex);
        }

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            if (rowWasVisible[rowIndex]) {
                cmd.set(rowSelector(rowIndex) + ".Visible", false);
                rowWasVisible[rowIndex] = false;
            }
        }
    }

    private void applyGroupToSlot(UICommandBuilder cmd,
                                  int slotIndex,
                                  GroupCardPlacement placement,
                                  String selectedRecipeId) {
        String currentGroupSelector = groupSelector(slotIndex);
        GroupViewModel group = placement.group();
        List<RecipeFilterPipeline.TaggedRecipe> recipes = group.recipes();
        int cellCapacity = slotCapacities[slotIndex];
        int renderCount = Math.min(cellCapacity, recipes.size());
        int previousCount = Math.min(slotLastRenderedTileCount[slotIndex], cellCapacity);

        cmd.set(currentGroupSelector + ".Visible", true);
        cmd.set(currentGroupSelector + ".FlexWeight", placement.flexWeight());
        cmd.set(currentGroupSelector + " #SetTitle.Text", group.title());
        cmd.set(currentGroupSelector + " #SetTitle.TooltipText", group.title());

        for (int tileIndex = 0; tileIndex < renderCount; tileIndex++) {
            String tileSelector = currentGroupSelector + " #Tiles[" + tileIndex + "]";
            RecipeFilterPipeline.TaggedRecipe recipe = recipes.get(tileIndex);

            cmd.set(tileSelector + ".Visible", true);
            cmd.set(tileSelector + " #TileIcon.ItemId", recipe.outputItemId() == null ? "" : recipe.outputItemId());
            cmd.set(tileSelector + " #Dim.Visible", !recipe.affordable());
            cmd.set(tileSelector + " #TileBtn.TooltipText", toRecipeSelectPayload(recipe.recipeId()));

            boolean isSelected = recipe.recipeId().equals(selectedRecipeId);
            cmd.set(tileSelector + " #TileBtn.Style",
                    Objects.requireNonNull(isSelected ? TILE_SELECTED_STYLE : TILE_UNSELECTED_STYLE));
        }

        int clearStart = Math.min(renderCount, cellCapacity);
        int clearEnd = Math.max(previousCount, cellCapacity);
        for (int tileIndex = clearStart; tileIndex < clearEnd; tileIndex++) {
            clearTile(cmd, currentGroupSelector + " #Tiles[" + tileIndex + "]");
        }

        slotWasVisible[slotIndex] = true;
        slotLastRenderedTileCount[slotIndex] = renderCount;
    }

    private void clearGroupSlot(UICommandBuilder cmd, int slotIndex) {
        String currentGroupSelector = groupSelector(slotIndex);
        int previousCount = Math.min(slotLastRenderedTileCount[slotIndex], slotCapacities[slotIndex]);
        int clearEnd = Math.max(previousCount, slotCapacities[slotIndex]);

        cmd.set(currentGroupSelector + ".Visible", false);
        cmd.set(currentGroupSelector + ".FlexWeight", 1);
        cmd.set(currentGroupSelector + " #SetTitle.Text", "");
        cmd.set(currentGroupSelector + " #SetTitle.TooltipText", "");

        for (int tileIndex = 0; tileIndex < clearEnd; tileIndex++) {
            clearTile(cmd, currentGroupSelector + " #Tiles[" + tileIndex + "]");
        }

        slotWasVisible[slotIndex] = false;
        slotLastRenderedTileCount[slotIndex] = 0;
    }

    private SlotAssignment resolveSlotAssignment(GroupCardPlacement placement, boolean[] rowSlotTaken) {
        int rowIndex = placement.rowIndex();
        int indexInRow = placement.groupIndexInRow();

        if (rowIndex >= 0 && rowIndex < rowSlotIndices.length) {
            int[] rowSlots = rowSlotIndices[rowIndex];
            if (indexInRow >= 0 && indexInRow < rowSlots.length) {
                int slotIndex = rowSlots[indexInRow];
                if (!rowSlotTaken[slotIndex]) {
                    return new SlotAssignment(slotIndex, false);
                }
            }
        }

        for (int slotIndex = 0; slotIndex < slotCapacities.length; slotIndex++) {
            if (!rowSlotTaken[slotIndex]) {
                return new SlotAssignment(slotIndex, true);
            }
        }
        return new SlotAssignment(-1, true);
    }

    private void logLayoutIfChanged(List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                                    List<GroupViewModel> groups,
                                    List<GroupCardPlacement> placements,
                                    List<ResolvedPlacement> resolvedPlacements) {
        String signature = buildLayoutSignature(displayedRecipes, placements);
        if (signature.equals(lastLayoutLogSignature)) {
            return;
        }
        lastLayoutLogSignature = signature;

        LOGGER.info("[StencilCenterLayout][SUMMARY] groups="
            + groups.size()
            + " displayedRecipes="
            + displayedRecipes.size()
            + " placements="
            + placements.size());

        for (ResolvedPlacement resolved : resolvedPlacements) {
            GroupCardPlacement placement = resolved.placement();
            GroupViewModel group = placement.group();
            int affordableCount = 0;
            for (RecipeFilterPipeline.TaggedRecipe recipe : group.recipes()) {
                if (recipe.affordable()) {
                    affordableCount++;
                }
            }

            LOGGER.info("[StencilCenterLayout][CONTAINER] category='"
                    + group.title()
                    + "' row="
                    + placement.rowIndex()
                    + " rowIndex="
                    + placement.groupIndexInRow()
                    + " flex="
                    + placement.flexWeight()
                    + " recipes="
                    + group.recipes().size()
                    + " affordable="
                    + affordableCount
                    + " slot="
                    + resolved.slotIndex()
                    + " slotRow="
                    + slotRowIndices[resolved.slotIndex()]
                    + " slotIdx="
                    + slotIndexInRow[resolved.slotIndex()]
                    + " fallback="
                    + resolved.usedFallback());
        }

        for (int rowIndex = 0; rowIndex < rowSlotIndices.length; rowIndex++) {
            int[] slots = rowSlotIndices[rowIndex];
            StringBuilder indices = new StringBuilder();
            for (int i = 0; i < slots.length; i++) {
                if (i > 0) {
                    indices.append(',');
                }
                indices.append(slots[i]);
            }

            LOGGER.info("[StencilCenterLayout][ROW] row="
                    + rowIndex
                    + " slots="
                    + slots.length
                    + " indices="
                    + indices);
        }
    }

    private static String buildLayoutSignature(List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                                               List<GroupCardPlacement> placements) {
        StringBuilder sb = new StringBuilder();
        sb.append("r:").append(displayedRecipes.size()).append("|p:").append(placements.size());
        for (RecipeFilterPipeline.TaggedRecipe recipe : displayedRecipes) {
            sb.append('|')
                    .append(recipe.recipeId())
                    .append(':')
                    .append(recipe.effectiveSet())
                    .append(':')
                    .append(recipe.affordable());
        }
        for (GroupCardPlacement placement : placements) {
            sb.append("|pl:")
                    .append(placement.group().title())
                    .append(':')
                    .append(placement.rowIndex())
                    .append(':')
                    .append(placement.groupIndexInRow())
                    .append(':')
                    .append(placement.flexWeight());
        }
        return sb.toString();
    }

    private static String toRecipeSelectPayload(String recipeId) {
        return ACTION_PREFIX + recipeId;
    }

    private void clearTile(UICommandBuilder cmd, String tileSelector) {
        cmd.set(tileSelector + ".Visible", false);
        cmd.set(tileSelector + " #TileIcon.ItemId", "");
        cmd.set(tileSelector + " #Dim.Visible", false);
        cmd.set(tileSelector + " #TileBtn.TooltipText", "");
        cmd.set(tileSelector + " #TileBtn.Style", Objects.requireNonNull(TILE_UNSELECTED_STYLE));
    }

    private static List<GroupCardPlacement> computePlacements(@NonNull List<GroupViewModel> groups) {
        int row = 0;
        int widthInRow = 0;
        List<GroupCardPlacement> placements = new ArrayList<>();
        List<GroupViewModel> rowGroups = new ArrayList<>();

        for (GroupViewModel group : groups) {
            int groupWidth = projectedGroupWidthPx(group);
            int projected = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING_PX + groupWidth;

            if (widthInRow > 0 && projected > MAX_ROW_WIDTH_PX) {
                appendRowPlacements(placements, row, rowGroups);
                rowGroups.clear();
                widthInRow = 0;
                row++;
            }

            rowGroups.add(group);
            widthInRow = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING_PX + groupWidth;
        }

        appendRowPlacements(placements, row, rowGroups);

        return placements;
    }

    private static void appendRowPlacements(@NonNull List<GroupCardPlacement> placements,
                                            int rowIndex,
                                            @NonNull List<GroupViewModel> rowGroups) {
        if (rowGroups.isEmpty()) {
            return;
        }

        List<Integer> baseWeights = new ArrayList<>(rowGroups.size());
        int totalWeight = 0;
        int smallestWeight = Integer.MAX_VALUE;

        for (GroupViewModel group : rowGroups) {
            int baseWeight = visibleTileCount(group);
            baseWeights.add(baseWeight);
            totalWeight += baseWeight;
            smallestWeight = Math.min(smallestWeight, baseWeight);
        }

        int remainder = Math.max(0, MAX_TILES_PER_ROW - totalWeight);
        int smallestCount = 0;
        for (int baseWeight : baseWeights) {
            if (baseWeight == smallestWeight) {
                smallestCount++;
            }
        }

        int bonusForSmallest = smallestCount == 0 ? 0 : (remainder + smallestCount - 1) / smallestCount;

        for (int indexInRow = 0; indexInRow < rowGroups.size(); indexInRow++) {
            int flexWeight = baseWeights.get(indexInRow);
            if (baseWeights.get(indexInRow) == smallestWeight) {
                flexWeight += bonusForSmallest;
            }

            placements.add(new GroupCardPlacement(rowIndex, indexInRow, flexWeight, rowGroups.get(indexInRow)));
        }
    }

    private static int visibleTileCount(@NonNull GroupViewModel group) {
        return Math.max(1, Math.min(MAX_TILES_PER_ROW, group.recipes().size()));
    }

    private static int projectedGroupWidthPx(@NonNull GroupViewModel group) {
        int visibleTiles = visibleTileCount(group);
        int tileWidth = (visibleTiles * TILE_SIZE_PX) + (Math.max(0, visibleTiles - 1) * TILE_GAP_PX);

        return Math.max(MIN_GROUP_WIDTH_PX, (CARD_PADDING_X_PX * 2) + tileWidth);
    }

    private static String rowGroupsSelector(int rowIndex) {
        return "#RecipeGridArea[" + rowIndex + "] #RowGroups";
    }

    private static String rowSelector(int rowIndex) {
        return "#RecipeGridArea[" + rowIndex + "]";
    }

    private String groupSelector(int slotIndex) {
        return rowGroupsSelector(slotRowIndices[slotIndex]) + "[" + slotIndexInRow[slotIndex] + "]";
    }

    private static List<Integer> buildRowSlotCounts(int[] capacities) {
        List<Integer> rowSlotCounts = new ArrayList<>();
        if (capacities.length == 0) {
            return rowSlotCounts;
        }

        int widthInRow = 0;
        int slotsInRow = 0;
        for (int capacity : capacities) {
            int groupWidth = projectedGroupWidthPxForTileCount(capacity);
            int projected = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING_PX + groupWidth;
            if (widthInRow > 0 && projected > MAX_ROW_WIDTH_PX) {
                rowSlotCounts.add(slotsInRow);
                widthInRow = 0;
                slotsInRow = 0;
            }

            slotsInRow++;
            widthInRow = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING_PX + groupWidth;
        }

        if (slotsInRow > 0) {
            rowSlotCounts.add(slotsInRow);
        }
        return rowSlotCounts;
    }

    private static int projectedGroupWidthPxForTileCount(int tileCount) {
        int visibleTiles = Math.max(1, Math.min(MAX_TILES_PER_ROW, tileCount));
        int tileWidth = (visibleTiles * TILE_SIZE_PX) + (Math.max(0, visibleTiles - 1) * TILE_GAP_PX);
        return Math.max(MIN_GROUP_WIDTH_PX, (CARD_PADDING_X_PX * 2) + tileWidth);
    }

    private static List<GroupViewModel> toGroupViewModels(List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes) {
        Map<String, List<RecipeFilterPipeline.TaggedRecipe>> recipesBySet = new LinkedHashMap<>();
        for (RecipeFilterPipeline.TaggedRecipe recipe : displayedRecipes) {
            recipesBySet.computeIfAbsent(recipe.effectiveSet(), ignored -> new ArrayList<>()).add(recipe);
        }

        List<GroupViewModel> groups = new ArrayList<>();
        for (Map.Entry<String, List<RecipeFilterPipeline.TaggedRecipe>> entry : recipesBySet.entrySet()) {
            List<RecipeFilterPipeline.TaggedRecipe> recipes = List.copyOf(entry.getValue());
            boolean hasMatchingRecipe = false;
            for (RecipeFilterPipeline.TaggedRecipe recipe : recipes) {
                if (recipe.affordable()) {
                    hasMatchingRecipe = true;
                    break;
                }
            }
            if (!hasMatchingRecipe) {
                continue;
            }
            groups.add(new GroupViewModel(entry.getKey(), recipes));
        }
        return groups;
    }

    private record GroupViewModel(String title, List<RecipeFilterPipeline.TaggedRecipe> recipes) {
    }

    private record GroupCardPlacement(int rowIndex, int groupIndexInRow, int flexWeight, GroupViewModel group) {
    }

    private record SlotAssignment(int slotIndex, boolean usedFallback) {
    }

    private record ResolvedPlacement(GroupCardPlacement placement, int slotIndex, boolean usedFallback) {
    }
}
