package com.CodeCreature.ui.bench.render;

import com.CodeCreature.ui.bench.RecipeFilterPipeline;
import com.CodeCreature.ui.common.IconPathResolver;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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

/**
 * Center renderer that uses sandbox center components under Pages/StencilBook/Sandbox/Components.
 */
public final class SandboxCenterRenderer implements StencilCenterRenderer {

    private static final String SET_ROW_UI = "Pages/StencilBook/Sandbox/Components/SandboxSetRow.ui";
    private static final String SET_GROUP_UI = "Pages/StencilBook/Sandbox/Components/SandboxSetGroup.ui";
    private static final String ICON_TILE_UI = "Pages/StencilBook/Sandbox/Components/SandboxIconTile.ui";

    private static final String ACTION_PREFIX = "RecipeSelect:rid:";
    private static final String UNCATEGORIZED_SET = "Uncategorized";

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

    public SandboxCenterRenderer(String[] setNames, int[] cellsPerSet) {
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
        cmd.append("#RecipeGridArea", SET_ROW_UI);

        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            cmd.append("#RecipeGridArea[0] #RowGroups", SET_GROUP_UI);
            int cellCount = slotCapacities[groupIndex];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                cmd.append(groupSelector(groupIndex) + " #Tiles", ICON_TILE_UI);
            }
        }
    }

    @Override
    public void buildBindings(UIEventBuilder evt) {
        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            int cellCount = slotCapacities[groupIndex];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                String tileSelector = groupSelector(groupIndex) + " #Tiles[" + cellIndex + "]";
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
        List<RenderGroup> visibleGroups = toRenderGroups(displayedRecipes);
        Map<Integer, RenderGroup> slotAssignments = assignGroupsToVisibleSlots(visibleGroups);
        Map<Integer, SlotRenderPlan> renderPlans = computeRenderPlans(slotAssignments);

        cmd.set("#RecipeGridArea[0].Visible", !renderPlans.isEmpty());

        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            String currentGroupSelector = groupSelector(groupIndex);
            int cellCount = slotCapacities[groupIndex];

            SlotRenderPlan plan = renderPlans.get(groupIndex);
            RenderGroup group = plan == null ? null : plan.group();
            List<RecipeFilterPipeline.TaggedRecipe> recipes = group == null ? List.of() : group.recipes();
            boolean hasRecipes = group != null && !recipes.isEmpty();

            cmd.set(currentGroupSelector + ".Visible", hasRecipes);
            cmd.set(currentGroupSelector + ".FlexWeight", plan == null ? 1 : plan.flexWeight());
            cmd.set(currentGroupSelector + " #SetTitle.Text",
                    hasRecipes ? setDisplayLabel(Objects.requireNonNull(group.effectiveSet())) : "");
            cmd.set(currentGroupSelector + " #SetTitle.TooltipText",
                    hasRecipes ? setDisplayLabel(Objects.requireNonNull(group.effectiveSet())) : "");

            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                String tileSelector = currentGroupSelector + " #Tiles[" + cellIndex + "]";
                if (!hasRecipes || cellIndex >= recipes.size()) {
                    clearTile(cmd, tileSelector);
                    continue;
                }

                RecipeFilterPipeline.TaggedRecipe recipe = recipes.get(cellIndex);
                cmd.set(tileSelector + ".Visible", true);
                cmd.set(tileSelector + " #TileIcon.Background", resolveTileBackground(recipe));
                cmd.set(tileSelector + " #TileBtn.TooltipText", toRecipeSelectPayload(recipe.recipeId()));

                boolean isSelected = recipe.recipeId().equals(selectedRecipeId);
                cmd.set(tileSelector + " #TileBtn.Style",
                        Objects.requireNonNull(isSelected ? TILE_SELECTED_STYLE : TILE_UNSELECTED_STYLE));
            }
        }
    }

    @Override
    public void clearOnDismiss(UICommandBuilder cmd) {
        cmd.set("#RecipeGridArea[0].Visible", false);

        for (int groupIndex = 0; groupIndex < slotCapacities.length; groupIndex++) {
            String currentGroupSelector = groupSelector(groupIndex);
            int cellCount = slotCapacities[groupIndex];

            cmd.set(currentGroupSelector + ".Visible", false);
            cmd.set(currentGroupSelector + ".FlexWeight", 1);
            cmd.set(currentGroupSelector + " #SetTitle.Text", "");
            cmd.set(currentGroupSelector + " #SetTitle.TooltipText", "");

            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                clearTile(cmd, currentGroupSelector + " #Tiles[" + cellIndex + "]");
            }
        }
    }

    private String groupSelector(int groupIndex) {
        return "#RecipeGridArea[0] #RowGroups[" + groupIndex + "]";
    }

    private static String toRecipeSelectPayload(String recipeId) {
        return ACTION_PREFIX + recipeId;
    }

    private void clearTile(UICommandBuilder cmd, String tileSelector) {
        cmd.set(tileSelector + ".Visible", false);
        cmd.set(tileSelector + " #TileIcon.Background", "");
        cmd.set(tileSelector + " #TileBtn.TooltipText", "");
        cmd.set(tileSelector + " #TileBtn.Style", Objects.requireNonNull(TILE_UNSELECTED_STYLE));
    }

    private static String resolveTileBackground(RecipeFilterPipeline.TaggedRecipe recipe) {
        if (recipe.outputItemId() == null || recipe.outputItemId().isEmpty()) {
            return "";
        }

        Item item = Item.getAssetMap().getAsset(recipe.outputItemId());
        if (item != null) {
            String normalized = IconPathResolver.normalizeItemIcon(item.getIcon());
            if (normalized != null) {
                return normalized;
            }
        }

        String fallback = IconPathResolver.normalizeItemIcon(recipe.outputItemId() + ".png");
        return fallback == null ? "" : fallback;
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

    private static List<RenderGroup> toRenderGroups(List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes) {
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

    private record RenderGroup(String effectiveSet, List<RecipeFilterPipeline.TaggedRecipe> recipes) {}

    private record SlotGroupRef(int slotIndex, RenderGroup group) {}

    private record SlotRenderPlan(int slotIndex, int rowIndex, int flexWeight, RenderGroup group) {}

    private static String setDisplayLabel(String setName) {
        return setName.replace('_', ' ');
    }
}
