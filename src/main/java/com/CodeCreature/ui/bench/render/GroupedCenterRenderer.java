package com.CodeCreature.ui.bench.render;

/**
 * @node    GroupedCenterRenderer
 * @wiki    docs/wiki/StencilBook/StencilCenterRenderer.md
 * @intent  Renders the center area as set-group containers with recipe tiles
 *          while preserving incoming displayed recipe ordering and action payload
 *          compatibility with production selection handling.
 * @wave    1 (dual renderer migration)
 * @status  Wave 1 - implemented
 * @do-not  Re-filter or re-sort recipes here; consume displayedRecipes as-is.
 */

import com.CodeCreature.ui.bench.RecipeFilterPipeline;
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
import java.util.Set;

public final class GroupedCenterRenderer implements StencilCenterRenderer {

    private static final String GROUP_CONTAINER_UI = "Pages/StencilBook/Components/SetGroupContainer.ui";
    private static final String GROUP_TILE_UI = "Pages/StencilBook/Components/GroupedRecipeTile.ui";
    private static final String ACTION_PREFIX = "RecipeSelect:rid:";
    private static final String UNCATEGORIZED_SET = "Uncategorized";

    // Match sandbox row-fit behavior so visible groups pack contiguously.
    private static final int MAX_TILES_PER_ROW = 10;
    private static final int TILE_SIZE_PX = 72;
    private static final int TILE_GAP_PX = 4;
    private static final int CARD_PADDING_X_PX = 8;
    private static final int ROW_ITEM_SPACING_PX = 8;
    private static final int MAX_ROW_WIDTH_PX = 920;
    private static final int MIN_GROUP_WIDTH_PX = 120;

    private static final Value<String> CELL_SELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "SelectedCellButtonStyle");
    private static final Value<String> CELL_UNSELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "TransparentButtonStyle");

    private final int[] slotCapacities;

    public GroupedCenterRenderer(String[] setNames, int[] cellsPerSet) {
        int inferredSlotCount = Math.max(
                setNames == null ? 0 : setNames.length,
                cellsPerSet == null ? 0 : cellsPerSet.length
        );
        List<Integer> capacities = new ArrayList<>(inferredSlotCount);
        for (int i = 0; i < inferredSlotCount; i++) {
            int capacity = (cellsPerSet != null && i < cellsPerSet.length) ? Math.max(0, cellsPerSet[i]) : 0;
            capacities.add(capacity);
        }
        capacities.sort(Comparator.reverseOrder());

        this.slotCapacities = capacities.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void appendStructure(UICommandBuilder cmd) {
        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            cmd.append("#RecipeGridArea", GROUP_CONTAINER_UI);
            int cellCount = slotCapacities[groupIndex];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                cmd.append("#RecipeGridArea[" + groupIndex + "] #GroupCells", GROUP_TILE_UI);
            }
        }
    }

    @Override
    public void buildBindings(UIEventBuilder evt) {
        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            int cellCount = slotCapacities[groupIndex];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                String tileSelector = "#RecipeGridArea[" + groupIndex + "] #GroupCells[" + cellIndex + "]";
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        tileSelector + " #Btn",
                        EventData.of("Action", tileSelector + " #Action.Text")
                );
            }
        }
    }

    @Override
    public void updateUI(UICommandBuilder cmd,
                         List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                         String selectedRecipeId) {
        List<RenderGroup> visibleGroups = toRenderGroups(displayedRecipes);
        Map<Integer, RenderGroup> slotAssignments = assignGroupsToVisibleSlots(visibleGroups);
        Map<Integer, SlotRenderPlan> renderPlans = computeRenderPlans(slotAssignments);

        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            String groupSelector = "#RecipeGridArea[" + groupIndex + "]";
            int cellCount = slotCapacities[groupIndex];

            SlotRenderPlan plan = renderPlans.get(groupIndex);
            RenderGroup group = plan == null ? null : plan.group();
            List<RecipeFilterPipeline.TaggedRecipe> recipes = group == null ? List.of() : group.recipes();
            boolean hasRecipes = group != null && !recipes.isEmpty();

            cmd.set(groupSelector + ".Visible", hasRecipes);
            cmd.set(groupSelector + ".FlexWeight", plan == null ? 1 : plan.flexWeight());
            cmd.set(groupSelector + " #SetGroupLabel.Text",
                    hasRecipes ? setDisplayLabel(Objects.requireNonNull(group.effectiveSet())) : "");

            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                String tileSelector = groupSelector + " #GroupCells[" + cellIndex + "]";
                if (!hasRecipes || cellIndex >= recipes.size()) {
                    clearTile(cmd, tileSelector);
                    continue;
                }

                RecipeFilterPipeline.TaggedRecipe recipe = recipes.get(cellIndex);
                cmd.set(tileSelector + ".Visible", true);
                cmd.set(tileSelector + " #Icon.ItemId", recipe.outputItemId() == null ? "" : recipe.outputItemId());
                cmd.set(tileSelector + " #Dim.Visible", !recipe.affordable());
                cmd.set(tileSelector + " #Action.Text", toRecipeSelectPayload(recipe.recipeId()));

                boolean isSelected = recipe.recipeId().equals(selectedRecipeId);
                cmd.set(tileSelector + " #Btn.Style",
                        Objects.requireNonNull(isSelected ? CELL_SELECTED_STYLE : CELL_UNSELECTED_STYLE));
            }
        }
    }

    @Override
    public void clearOnDismiss(UICommandBuilder cmd) {
        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            String groupSelector = "#RecipeGridArea[" + groupIndex + "]";
            cmd.set(groupSelector + ".Visible", false);
            cmd.set(groupSelector + ".FlexWeight", 1);
            cmd.set(groupSelector + " #SetGroupLabel.Text", "");
            int cellCount = slotCapacities[groupIndex];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                clearTile(cmd, groupSelector + " #GroupCells[" + cellIndex + "]");
            }
        }
    }

    private Map<Integer, RenderGroup> assignGroupsToVisibleSlots(List<RenderGroup> visibleGroups) {
        int visibleSlotCount = Math.min(visibleGroups.size(), slotCapacities.length);
        if (visibleSlotCount == 0) {
            return Map.of();
        }

        List<RenderGroup> groupsToAssign = new ArrayList<>(visibleGroups.subList(0, visibleSlotCount));
        groupsToAssign.sort(Comparator.comparingInt((RenderGroup group) -> group.recipes().size()).reversed());

        Set<Integer> remainingSlots = new java.util.TreeSet<>();
        for (int slotIndex = 0; slotIndex < visibleSlotCount; slotIndex++) {
            remainingSlots.add(slotIndex);
        }

        Map<Integer, RenderGroup> assigned = new java.util.HashMap<>();
        for (RenderGroup group : groupsToAssign) {
            int required = group.recipes().size();
            int bestSlot = -1;
            int bestCapacity = Integer.MAX_VALUE;

            for (int slotIndex : remainingSlots) {
                int capacity = slotCapacities[slotIndex];
                if (capacity >= required && capacity < bestCapacity) {
                    bestSlot = slotIndex;
                    bestCapacity = capacity;
                }
            }

            if (bestSlot < 0) {
                for (int slotIndex : remainingSlots) {
                    if (bestSlot < 0 || slotCapacities[slotIndex] > slotCapacities[bestSlot]) {
                        bestSlot = slotIndex;
                    }
                }
            }

            if (bestSlot >= 0) {
                assigned.put(bestSlot, group);
                remainingSlots.remove(bestSlot);
            }
        }

        return assigned;
    }

    private Map<Integer, SlotRenderPlan> computeRenderPlans(Map<Integer, RenderGroup> slotAssignments) {
        if (slotAssignments.isEmpty()) {
            return Map.of();
        }

        List<SlotGroupRef> orderedGroups = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < slotCapacities.length; slotIndex++) {
            RenderGroup group = slotAssignments.get(slotIndex);
            if (group != null) {
                orderedGroups.add(new SlotGroupRef(slotIndex, group));
            }
        }

        Map<Integer, SlotRenderPlan> plans = new java.util.HashMap<>();
        int widthInRow = 0;
        int rowIndex = 0;
        List<SlotGroupRef> rowGroups = new ArrayList<>();

        for (SlotGroupRef slotGroup : orderedGroups) {
            int groupWidth = projectedGroupWidthPx(slotGroup.group());
            int projected = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING_PX + groupWidth;

            if (widthInRow > 0 && projected > MAX_ROW_WIDTH_PX) {
                appendRowPlans(plans, rowIndex, rowGroups);
                rowGroups.clear();
                widthInRow = 0;
                rowIndex++;
            }

            rowGroups.add(slotGroup);
            widthInRow = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING_PX + groupWidth;
        }

        appendRowPlans(plans, rowIndex, rowGroups);
        return plans;
    }

    private void appendRowPlans(Map<Integer, SlotRenderPlan> plans, int rowIndex, List<SlotGroupRef> rowGroups) {
        if (rowGroups.isEmpty()) {
            return;
        }

        List<Integer> baseWeights = new ArrayList<>(rowGroups.size());
        int totalWeight = 0;
        int smallestWeight = Integer.MAX_VALUE;

        for (SlotGroupRef rowGroup : rowGroups) {
            int baseWeight = visibleTileCount(rowGroup.group());
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
        for (int idx = 0; idx < rowGroups.size(); idx++) {
            int flexWeight = baseWeights.get(idx);
            if (baseWeights.get(idx) == smallestWeight) {
                flexWeight += bonusForSmallest;
            }

            SlotGroupRef rowGroup = rowGroups.get(idx);
            plans.put(rowGroup.slotIndex(), new SlotRenderPlan(rowGroup.slotIndex(), rowIndex, flexWeight, rowGroup.group()));
        }
    }

    private static int visibleTileCount(RenderGroup group) {
        return Math.max(1, Math.min(MAX_TILES_PER_ROW, group.recipes().size()));
    }

    private static int projectedGroupWidthPx(RenderGroup group) {
        int visibleTiles = visibleTileCount(group);
        int tileWidth = (visibleTiles * TILE_SIZE_PX) + (Math.max(0, visibleTiles - 1) * TILE_GAP_PX);
        return Math.max(MIN_GROUP_WIDTH_PX, (CARD_PADDING_X_PX * 2) + tileWidth);
    }

    private void clearTile(UICommandBuilder cmd, String tileSelector) {
        cmd.set(tileSelector + ".Visible", false);
        cmd.set(tileSelector + " #Icon.ItemId", "");
        cmd.set(tileSelector + " #Dim.Visible", false);
        cmd.set(tileSelector + " #Action.Text", "");
        cmd.set(tileSelector + " #Btn.Style", Objects.requireNonNull(CELL_UNSELECTED_STYLE));
    }

    public static String toRecipeSelectPayload(String recipeId) {
        return ACTION_PREFIX + recipeId;
    }

    public static List<RenderGroup> toRenderGroups(List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes) {
        Map<String, List<RecipeFilterPipeline.TaggedRecipe>> recipesBySet = new LinkedHashMap<>();
        for (RecipeFilterPipeline.TaggedRecipe recipe : displayedRecipes) {
            String effectiveSet = recipe.effectiveSet();
            if (effectiveSet == null || effectiveSet.isEmpty()) {
                effectiveSet = UNCATEGORIZED_SET;
            }
            recipesBySet.computeIfAbsent(effectiveSet, ignored -> new ArrayList<>()).add(recipe);
        }

        List<RenderGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<RecipeFilterPipeline.TaggedRecipe>> entry : recipesBySet.entrySet()) {
            groups.add(new RenderGroup(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return groups;
    }

    public record RenderGroup(String effectiveSet, List<RecipeFilterPipeline.TaggedRecipe> recipes) {}

    private record SlotGroupRef(int slotIndex, RenderGroup group) {}

    private record SlotRenderPlan(int slotIndex, int rowIndex, int flexWeight, RenderGroup group) {}

    private static String setDisplayLabel(String setName) {
        return setName.replace('_', ' ');
    }
}