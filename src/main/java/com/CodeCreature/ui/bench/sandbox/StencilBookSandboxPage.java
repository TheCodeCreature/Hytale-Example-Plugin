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

    // Width model used to assign groups into invisible row hosts before append.
    private static final int MAX_ROW_WIDTH = 920;
    private static final int ROW_ITEM_SPACING = 8;
    private static final int GROUP_SIDE_PADDING = 8;
    private static final int TILE_SIZE = 72;
    private static final int TILE_GAP = 4;
    private static final int MIN_GROUP_WIDTH = 120;

    private static final List<GroupViewModel> GROUPS = List.of(
        new GroupViewModel("Deco Iron", List.of(
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH
        )),
        new GroupViewModel("Deco Iron", List.of(
            WORKBENCH_ICON_PATH
        )),
        new GroupViewModel("Deco Iron asdf asdf asdf asdf asdf", List.of(
            ARMORY_ICON_PATH
        )),
        new GroupViewModel("Deco Iron asdf asdf asdf asdf asdf ", List.of(
            WORKBENCH_ICON_PATH
        )),
        new GroupViewModel("Deco Iron", List.of(
            ARMORY_ICON_PATH
        )),
        new GroupViewModel("Deco Iron", List.of(
            WORKBENCH_ICON_PATH
        )),
        new GroupViewModel("Deco Iron", List.of(
            ARMORY_ICON_PATH
        )),
        new GroupViewModel("Deco Iron", List.of(
            WORKBENCH_ICON_PATH
        )),
        new GroupViewModel("Deco Iron", List.of(
            ARMORY_ICON_PATH
        )),
        new GroupViewModel("Furniture Adventure", List.of(
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH
        )),
        new GroupViewModel("Furniture Castle", List.of(
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH,
            WORKBENCH_ICON_PATH,
            ARMORY_ICON_PATH
        ))
    );

    private static final List<GroupPlacement> GROUP_PLACEMENTS = computePlacements(GROUPS);

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
        for (GroupPlacement placement : GROUP_PLACEMENTS) {
            if (placement.rowIndex() != currentRow) {
                currentRow = placement.rowIndex();
                cmd.append("#SetRows", SET_ROW_UI_PATH);
            }

            String rowSelector = "#SetRows[" + placement.rowIndex() + "] #RowGroups";
            cmd.append(rowSelector, SET_GROUP_UI_PATH);

            int tileCount = placement.group().tileIconPaths().size();
            String tileHost = rowSelector + "[" + placement.groupIndexInRow() + "] #Tiles";
            for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
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

        for (GroupPlacement placement : GROUP_PLACEMENTS) {
            GroupViewModel group = placement.group();
            String groupSelector = "#SetRows["
                    + placement.rowIndex()
                    + "] #RowGroups["
                    + placement.groupIndexInRow()
                    + "]";

            cmd.set(groupSelector + " #SetTitle.Text", group.title());
            cmd.set(groupSelector + " #SetTitle.TooltipText", group.title());

            for (int tileIndex = 0; tileIndex < group.tileIconPaths().size(); tileIndex++) {
                String tilePath = group.tileIconPaths().get(tileIndex);
                String tileSelector = groupSelector + " #Tiles[" + tileIndex + "]";
                cmd.set(tileSelector + " #TileIcon.Background", tilePath);
                cmd.set(tileSelector + " #TileBtn.TooltipText", group.title() + " - Tile " + (tileIndex + 1));
            }
        }

        cmd.set("#DetailHost #DetailTitle.Text", "Sandbox Detail");
        cmd.set("#DetailHost #DetailIcon.Background", WORKBENCH_ICON_PATH);
        cmd.set("#DetailHost #DetailBody.Text", "Static placeholder side panel for quick layout testing.");
    }

    private static List<GroupPlacement> computePlacements(@NonNull List<GroupViewModel> groups) {
        int row = 0;
        int widthInRow = 0;
        int indexInRow = 0;
        List<GroupPlacement> placements = new java.util.ArrayList<>();

        for (GroupViewModel group : groups) {
            int groupWidth = estimateGroupWidth(group.tileIconPaths().size());
            int projected = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING + groupWidth;

            if (widthInRow > 0 && projected > MAX_ROW_WIDTH) {
                row++;
                widthInRow = 0;
                indexInRow = 0;
            }

            placements.add(new GroupPlacement(row, indexInRow, group));
            widthInRow = widthInRow == 0 ? groupWidth : widthInRow + ROW_ITEM_SPACING + groupWidth;
            indexInRow++;
        }

        return placements;
    }

    private static int estimateGroupWidth(int tileCount) {
        int tilesWidth = (tileCount * TILE_SIZE) + Math.max(0, tileCount - 1) * TILE_GAP;
        return Math.max(MIN_GROUP_WIDTH, GROUP_SIDE_PADDING + tilesWidth);
    }

    private record GroupViewModel(String title, List<String> tileIconPaths) {}

    private record GroupPlacement(int rowIndex, int groupIndexInRow, GroupViewModel group) {}

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC =
                BuilderCodec.builder(EventPayload.class, EventPayload::new).build();
    }
}
