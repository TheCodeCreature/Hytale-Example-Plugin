package com.CodeCreature.ui.bench.render;

import com.CodeCreature.ui.bench.RecipeFilterPipeline;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.ArrayList;

/**
 * Sandbox-style center renderer mounted into the production center panel.
 */
public final class BlankCenterRenderer implements StencilCenterRenderer {

    private static final String SET_ROW_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxSetRow.ui";
    private static final String SET_GROUP_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxSetGroup.ui";
    private static final String ICON_TILE_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxIconTile.ui";

    private static final String WORKBENCH_ICON_PATH = "Common/Icons/ItemsGenerated/Bench_WorkBench.png";
    private static final String ARMORY_ICON_PATH = "Common/Icons/ItemsGenerated/Bench_Armory.png";

    private static final int MAX_TILES_PER_ROW = 10;
    private static final int TILE_SIZE_PX = 72;
    private static final int TILE_GAP_PX = 4;
    private static final int CARD_PADDING_X_PX = 8;
    private static final int ROW_ITEM_SPACING_PX = 8;
    private static final int MAX_ROW_WIDTH_PX = 920;
    private static final int MIN_GROUP_WIDTH_PX = 120;

    private static final List<GroupViewModel> GROUPS = buildGroups();
    private static final List<GroupCardPlacement> GROUP_PLACEMENTS = computePlacements(GROUPS);

    private boolean appended;

    @Override
    public void appendStructure(UICommandBuilder cmd) {
        if (appended) {
            return;
        }

        int currentRow = -1;
        for (GroupCardPlacement placement : GROUP_PLACEMENTS) {
            if (placement.rowIndex() != currentRow) {
                currentRow = placement.rowIndex();
                cmd.append("#RecipeGridArea", SET_ROW_UI_PATH);
            }

            String rowSelector = rowGroupsSelector(placement.rowIndex());
            cmd.append(rowSelector, SET_GROUP_UI_PATH);

            GroupViewModel group = placement.group();
            List<String> tilePaths = group.tileIconPaths();
            String tileHost = groupSelector(placement) + " #Tiles";
            for (int tileIndex = 0; tileIndex < tilePaths.size(); tileIndex++) {
                cmd.append(tileHost, ICON_TILE_UI_PATH);
            }
        }

        appended = true;
    }

    @Override
    public void buildBindings(UIEventBuilder evt) {
        // Sandbox center view is static in this renderer.
    }

    @Override
    public void updateUI(UICommandBuilder cmd,
                         List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                         String selectedRecipeId) {
        for (GroupCardPlacement placement : GROUP_PLACEMENTS) {
            GroupViewModel group = placement.group();
            List<String> tilePaths = group.tileIconPaths();
            String groupSelector = groupSelector(placement);

            cmd.set(groupSelector + ".FlexWeight", placement.flexWeight());
            cmd.set(groupSelector + " #SetTitle.Text", group.title());
            cmd.set(groupSelector + " #SetTitle.TooltipText", group.title());

            for (int tileIndex = 0; tileIndex < tilePaths.size(); tileIndex++) {
                String tilePath = tilePaths.get(tileIndex);
                String tileSelector = groupSelector + " #Tiles[" + tileIndex + "]";
                cmd.set(tileSelector + " #TileIcon.Background", tilePath);
                cmd.set(tileSelector + " #TileBtn.TooltipText", group.title() + " - Tile " + (tileIndex + 1));
            }
        }
    }

    @Override
    public void clearOnDismiss(UICommandBuilder cmd) {
        // Leave sandbox rows in place; page close handles full UI lifecycle.
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
        return Math.max(1, Math.min(MAX_TILES_PER_ROW, group.tileIconPaths().size()));
    }

    private static int projectedGroupWidthPx(@NonNull GroupViewModel group) {
        int visibleTiles = visibleTileCount(group);
        int tileWidth = (visibleTiles * TILE_SIZE_PX) + (Math.max(0, visibleTiles - 1) * TILE_GAP_PX);

        return Math.max(MIN_GROUP_WIDTH_PX, (CARD_PADDING_X_PX * 2) + tileWidth);
    }

    private static String rowGroupsSelector(int rowIndex) {
        return "#RecipeGridArea[" + rowIndex + "] #RowGroups";
    }

    private static String groupSelector(@NonNull GroupCardPlacement placement) {
        return rowGroupsSelector(placement.rowIndex()) + "[" + placement.groupIndexInRow() + "]";
    }

    private static List<GroupViewModel> buildGroups() {
        return List.of(
                new GroupViewModel("Deco Iron", alternatingTiles(6, WORKBENCH_ICON_PATH, ARMORY_ICON_PATH)),
                new GroupViewModel("Deco Iron", List.of(WORKBENCH_ICON_PATH)),
                new GroupViewModel("Deco Iron asdf asdf asdf asdf asdf", List.of(ARMORY_ICON_PATH)),
                new GroupViewModel("Deco Iron asdf asdf asdf asdf asdf ", List.of(WORKBENCH_ICON_PATH)),
                new GroupViewModel("Deco Iron", List.of(ARMORY_ICON_PATH)),
                new GroupViewModel("Deco Iron", List.of(WORKBENCH_ICON_PATH)),
                new GroupViewModel("Deco Iron", List.of(ARMORY_ICON_PATH)),
                new GroupViewModel("Deco Iron", List.of(WORKBENCH_ICON_PATH)),
                new GroupViewModel("Deco Iron", List.of(ARMORY_ICON_PATH)),
                new GroupViewModel("Furniture Adventure", alternatingTiles(28, ARMORY_ICON_PATH, WORKBENCH_ICON_PATH)),
                new GroupViewModel("Furniture Castle", alternatingTiles(4, WORKBENCH_ICON_PATH, ARMORY_ICON_PATH))
        );
    }

    private static List<String> alternatingTiles(int count, @NonNull String first, @NonNull String second) {
        List<String> tiles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tiles.add(i % 2 == 0 ? first : second);
        }
        return List.copyOf(tiles);
    }

    private record GroupViewModel(String title, List<String> tileIconPaths) {
    }

    private record GroupCardPlacement(int rowIndex, int groupIndexInRow, int flexWeight, GroupViewModel group) {
    }
}
