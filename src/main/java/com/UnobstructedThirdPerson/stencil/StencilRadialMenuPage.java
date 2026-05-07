package com.UnobstructedThirdPerson.stencil;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import com.UnobstructedThirdPerson.resourcecollection.FilteredRecipeEntry;
import com.UnobstructedThirdPerson.resourcecollection.RecipeFilterRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * POC radial menu page for stencil items.
 *
 * <p>Displays {@link #SEGMENTS_PER_PAGE} rectangular buttons arranged in a
 * circular orientation around center prev/next navigation controls. Each button
 * shows an {@code ItemIcon} and can be clicked to select a segment.
 *
 * <h3>Layout strategy</h3>
 * Since Hytale has no native radial/circular layout mode, this page uses
 * {@code LayoutMode: Full} with absolute positioning. Segment positions are
 * computed trigonometrically at build time and applied via {@code Left}/{@code Top}
 * anchor values.
 *
 * <h3>Events handled</h3>
 * <ul>
 *   <li>{@code select:<index>} — segment clicked → log selection and close</li>
 *   <li>{@code hover:<index>} — mouse entered segment → highlight + show label</li>
 *   <li>{@code unhover:<index>} — mouse exited segment → remove highlight</li>
 *   <li>{@code nextPage} — advance to next page of items</li>
 *   <li>{@code prevPage} — go back to previous page</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * This page is opened via {@code PageManager.openCustomPage()} on the world thread.
 * All {@code handleDataEvent} calls also execute on the world thread.
 */
public class StencilRadialMenuPage extends InteractiveCustomUIPage<StencilRadialMenuPage.EventPayload> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── Layout constants ──

    /** Number of segment buttons displayed per page. */
    private static final int SEGMENTS_PER_PAGE = 6;

    /** Radius in pixels from the container center to each segment center. */
    private static final int RADIUS = 140;

    /** Container center X (half of 400px container width). */
    private static final int CENTER_X = 200;

    /** Container center Y (half of 400px container height). */
    private static final int CENTER_Y = 200;

    /** Segment button width in pixels. */
    private static final int SEGMENT_W = 56;

    /** Segment button height in pixels. */
    private static final int SEGMENT_H = 56;

    // ── State ──

    private int currentPage = 0;
    private final List<RadialSegmentItem> allItems;

    /**
     * Creates a new radial menu page for the given player.
     * Scans the player's hotbar for stencil items and populates the menu.
     *
     * @param playerRef the player who triggered the menu
     * @param player    the player entity (used to read hotbar stencils)
     */
    public StencilRadialMenuPage(@NonNull PlayerRef playerRef, @NonNull Player player) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
        this.allItems = scanStencils(player);
    }

    /**
     * Scans the player's hotbar for stencil items and builds the radial menu items list.
     */
    private static List<RadialSegmentItem> scanStencils(@NonNull Player player) {
        ItemStack activeItem = player.getInventory().getActiveHotbarItem();
        if (activeItem == null || !StencilMetadata.isStencil(activeItem)) {
            return List.of();
        }

        String recipeId = StencilMetadata.getRecipeId(activeItem);
        if (recipeId == null) return List.of();

        FilteredRecipeEntry activeEntry = RecipeFilterRegistry.getEntry(recipeId);
        if (activeEntry == null) return List.of();

        String setName = activeEntry.set();
        if (setName == null || setName.isEmpty()) {
            // No set — just show the active item
            String label = activeEntry.outputItemId().replace('_', ' ');
            return List.of(new RadialSegmentItem(activeEntry.outputItemId(), activeEntry.recipeId(), label, 0));
        }

        List<FilteredRecipeEntry> setEntries = RecipeFilterRegistry.getEntriesForSet(setName);
        List<RadialSegmentItem> items = new ArrayList<>(setEntries.size());
        for (int i = 0; i < setEntries.size(); i++) {
            FilteredRecipeEntry entry = setEntries.get(i);
            String label = entry.outputItemId().replace('_', ' ');
            items.add(new RadialSegmentItem(entry.outputItemId(), entry.recipeId(), label, i));
        }
        return items;
    }

    /**
     * Builds the initial UI layout: loads the page template, appends segment
     * templates into {@code #Segments}, positions them in a circle, sets their
     * item icons, and binds all events.
     *
     * <p>Called once when the page is opened. All event bindings are established
     * here — {@code handleDataEvent} only sends incremental updates.
     */
    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {
        cmd.append("Pages/StencilRadial/StencilRadialMenu.ui");
        appendSegments(cmd, evt, currentPage);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevBtn",
                EventData.of("Action", "prevPage"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextBtn",
                EventData.of("Action", "nextPage"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteBtnHit",
                EventData.of("Action", "delete"), false);
        cmd.set("#PageLabel.Text", (currentPage + 1) + " / " + getTotalPages());
    }

    /**
     * Handles all UI events routed through the {@link EventPayload#action} field.
     *
     * <p>Action routing:
     * <ul>
     *   <li>{@code select:<globalIndex>} — log the selected item and close the menu</li>
     *   <li>{@code hover:<slotIndex>} — highlight the hovered segment, show label in #HoverLabel</li>
     *   <li>{@code unhover:<slotIndex>} — remove highlight, clear #HoverLabel</li>
     *   <li>{@code nextPage} — increment currentPage (wrapping), call updateSegments()</li>
     *   <li>{@code prevPage} — decrement currentPage (wrapping), call updateSegments()</li>
     * </ul>
     */
    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {
        UICommandBuilder cmd = new UICommandBuilder();
        String action = data.action;

        if (action.startsWith("select:")) {
            int index = Integer.parseInt(action.substring("select:".length()));
            List<RadialSegmentItem> pageItems = getPageItems(currentPage);
            if (index >= 0 && index < pageItems.size()) {
                RadialSegmentItem selected = pageItems.get(index);
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    byte activeSlot = player.getInventory().getActiveHotbarSlot();
                    if (activeSlot >= 0) {
                        ItemStack newStencil = StencilMetadata.createStencil(selected.itemId(), selected.recipeId());
                        player.getInventory().getHotbar().setItemStackForSlot(activeSlot, newStencil);
                    }
                }
            }
            this.close();
            return;
        } else if (action.startsWith("hover:")) {
            int slot = Integer.parseInt(action.substring("hover:".length()));
            List<RadialSegmentItem> items = getPageItems(currentPage);
            if (slot < items.size()) {
                cmd.set("#HoverLabel.Text", items.get(slot).label());
            }
        } else if (action.startsWith("unhover:")) {
            cmd.set("#HoverLabel.Text", "");
        } else if ("nextPage".equals(action)) {
            currentPage = (currentPage + 1) % getTotalPages();
            updateSegments(cmd, currentPage);
            cmd.set("#PageLabel.Text", (currentPage + 1) + " / " + getTotalPages());
        } else if ("prevPage".equals(action)) {
            currentPage = (currentPage - 1 + getTotalPages()) % getTotalPages();
            updateSegments(cmd, currentPage);
            cmd.set("#PageLabel.Text", (currentPage + 1) + " / " + getTotalPages());
        } else if ("delete".equals(action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                byte activeSlot = player.getInventory().getActiveHotbarSlot();
                if (activeSlot >= 0) {
                    player.getInventory().getHotbar().removeItemStackFromSlot(activeSlot);
                }
            }
            this.close();
            return;
        }

        sendUpdate(cmd, null, false);
    }

    /**
     * Appends {@link #SEGMENTS_PER_PAGE} segment templates into {@code #Segments},
     * positions each one in a circle using trigonometry, sets their item icons,
     * and binds Activating/MouseEntered/MouseExited events.
     *
     * <p>Called once during {@link #build}. For subsequent page changes, use
     * {@link #updateSegments} which only updates property values.
     *
     * @param cmd  the command builder to append templates and set positions
     * @param evt  the event builder to bind segment interactions
     * @param page the page index (0-based) to determine which items to show
     */
    private void appendSegments(UICommandBuilder cmd, UIEventBuilder evt, int page) {
        List<RadialSegmentItem> items = getPageItems(page);
        for (int i = 0; i < SEGMENTS_PER_PAGE; i++) {
            cmd.append("#Segments", "Pages/StencilRadial/StencilRadialSegment.ui");

            double angle = (2 * Math.PI * i / SEGMENTS_PER_PAGE) - (Math.PI / 2);
            int left = CENTER_X + (int) (RADIUS * Math.cos(angle)) - SEGMENT_W / 2;
            int top  = CENTER_Y + (int) (RADIUS * Math.sin(angle)) - SEGMENT_H / 2;

            Anchor anchor = new Anchor();
            anchor.setWidth(Value.of(SEGMENT_W));
            anchor.setHeight(Value.of(SEGMENT_H));
            anchor.setLeft(Value.of(left));
            anchor.setTop(Value.of(top));
            cmd.setObject("#Segments[" + i + "].Anchor", anchor);

            if (i < items.size()) {
                cmd.set("#Segments[" + i + "] #SegIcon.ItemId", items.get(i).itemId());
                cmd.set("#Segments[" + i + "].Visible", true);
                evt.addEventBinding(CustomUIEventBindingType.Activating, "#Segments[" + i + "] #SegBtn",
                        EventData.of("Action", "select:" + items.get(i).index()), false);
            } else {
                cmd.set("#Segments[" + i + "].Visible", false);
            }

            evt.addEventBinding(CustomUIEventBindingType.MouseEntered, "#Segments[" + i + "] #SegBtn",
                    EventData.of("Action", "hover:" + i), false);
            evt.addEventBinding(CustomUIEventBindingType.MouseExited, "#Segments[" + i + "] #SegBtn",
                    EventData.of("Action", "unhover:" + i), false);
        }
    }

    /**
     * Updates the displayed items on the current page without re-appending templates.
     * Used for pagination — changes item icons and updates event data for selection.
     *
     * <p>Sends an incremental update via {@link #sendUpdate}.
     *
     * @param cmd  the command builder for property updates
     * @param page the new page index
     */
    private void updateSegments(UICommandBuilder cmd, int page) {
        List<RadialSegmentItem> items = getPageItems(page);
        for (int i = 0; i < SEGMENTS_PER_PAGE; i++) {
            if (i < items.size()) {
                cmd.set("#Segments[" + i + "] #SegIcon.ItemId", items.get(i).itemId());
                cmd.set("#Segments[" + i + "].Visible", true);
            } else {
                cmd.set("#Segments[" + i + "] #SegIcon.ItemId", "");
                cmd.set("#Segments[" + i + "].Visible", false);
            }
        }
        cmd.set("#PageLabel.Text", (page + 1) + " / " + getTotalPages());
    }

    /**
     * Returns the sublist of items for the given page.
     *
     * @param page 0-based page index
     * @return the items for that page, may be smaller than SEGMENTS_PER_PAGE on the last page
     */
    private List<RadialSegmentItem> getPageItems(int page) {
        int fromIndex = page * SEGMENTS_PER_PAGE;
        int toIndex = Math.min(fromIndex + SEGMENTS_PER_PAGE, allItems.size());
        if (fromIndex >= allItems.size()) return List.of();
        return allItems.subList(fromIndex, toIndex);
    }

    /**
     * Returns the total number of pages for the current item list.
     */
    private int getTotalPages() {
        return (int) Math.ceil((double) allItems.size() / SEGMENTS_PER_PAGE);
    }

    // ── Event Payload ──

    /**
     * Typed event data for radial menu interactions. Only the {@code action}
     * field is used — all routing is done via the action string prefix.
     *
     * <p>Action string format:
     * <ul>
     *   <li>{@code "select:<globalIndex>"} — segment selection</li>
     *   <li>{@code "hover:<slotIndex>"} — mouse entered segment</li>
     *   <li>{@code "unhover:<slotIndex>"} — mouse exited segment</li>
     *   <li>{@code "nextPage"} — pagination forward</li>
     *   <li>{@code "prevPage"} — pagination backward</li>
     * </ul>
     */
    public static final class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .build();

        String action;
    }
}
