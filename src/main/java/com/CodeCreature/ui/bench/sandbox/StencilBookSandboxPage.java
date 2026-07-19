package com.CodeCreature.ui.bench.sandbox;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated sandbox page route for Stencil Book UI experimentation.
 *
 * <p>This page intentionally bypasses all production StencilSelectionPage logic
 * and only loads the sandbox .ui document.
 */
public class StencilBookSandboxPage extends InteractiveCustomUIPage<StencilBookSandboxPage.EventPayload> {

    private static final String SANDBOX_UI_PATH = "Pages/StencilBook/Sandbox/StencilBookSandboxPage.ui";
    private static final String NOTES_PANEL_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxNotesPanel.ui";
    private static final String SET_ROW_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxSetRow.ui";
    private static final String SET_GROUP_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxSetGroup.ui";
    private static final String ICON_TILE_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxIconTile.ui";
    private static final String DETAIL_PANEL_UI_PATH = "Pages/StencilBook/Sandbox/Components/SandboxDetailPanel.ui";

    private static final String WORKBENCH_ICON_PATH = "Common/Icons/ItemsGenerated/Bench_WorkBench.png";
    private static final String ARMORY_ICON_PATH = "Common/Icons/ItemsGenerated/Bench_Armory.png";

    /**
     * Single grid tuning knob.
     *
     * <p>Changing this value changes the projected width used to decide whether a group
     * fits in the current row:
     * <pre>
     * rowFitWidth = paddingX * 2 + (visibleTiles * tileSize) + ((visibleTiles - 1) * tileGap)
     * </pre>
     */
    private static final int MAX_TILES_PER_ROW = 10;

    private static final int TILE_SIZE_PX = 72;
    private static final int TILE_GAP_PX = 4;
    private static final int CARD_PADDING_X_PX = 8;
    private static final int ROW_ITEM_SPACING_PX = 8;
    private static final int MAX_ROW_WIDTH_PX = 920;
    private static final int MIN_GROUP_WIDTH_PX = 120;

    private static final List<GroupViewModel> GROUPS = buildGroups();

    private static final List<GroupCardPlacement> GROUP_PLACEMENTS = computePlacements(GROUPS);

    public StencilBookSandboxPage(@NonNull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {
        cmd.append(SANDBOX_UI_PATH);

        cmd.append("#NotesHost", NOTES_PANEL_UI_PATH);
        cmd.append("#DetailHost", DETAIL_PANEL_UI_PATH);

        int currentRow = -1;
        for (GroupCardPlacement placement : GROUP_PLACEMENTS) {
            if (placement.rowIndex() != currentRow) {
                currentRow = placement.rowIndex();
                cmd.append("#SetRows", SET_ROW_UI_PATH);
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

        applyViewState(cmd);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {
        UICommandBuilder cmd = new UICommandBuilder();
        applyViewState(cmd);
        sendUpdate(cmd, null, false);
    }

    private void applyViewState(@NonNull UICommandBuilder cmd) {
        cmd.set("#PageTitle.Text", "Stencil Crafting Sandbox:");
        cmd.set("#PageSubtitle.Text", "Static Icon Grid Sample");

        cmd.set("#NotesHost #NotesTitle.Text", "Sandbox Notes");
        cmd.set("#NotesHost #NotesBodyPrimary.Text", "Use this static layout to prototype a dynamic grid-in-grid view.");
        cmd.set("#NotesHost #NotesBodySecondary.Text", "No runtime filtering is wired on this page.");

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

        cmd.set("#DetailHost #DetailTitle.Text", "Sandbox Detail");
        cmd.set("#DetailHost #DetailIcon.Background", WORKBENCH_ICON_PATH);
        cmd.set("#DetailHost #DetailBody.Text", "Static placeholder side panel for quick layout testing.");
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
        return "#SetRows[" + rowIndex + "] #RowGroups";
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

    private record GroupViewModel(String title, List<String> tileIconPaths) {}

    private record GroupCardPlacement(int rowIndex, int groupIndexInRow, int flexWeight, GroupViewModel group) {}

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC =
                BuilderCodec.builder(EventPayload.class, EventPayload::new).build();
    }
}
