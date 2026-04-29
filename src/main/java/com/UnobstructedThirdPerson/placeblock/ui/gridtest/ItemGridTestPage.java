package com.UnobstructedThirdPerson.placeblock.ui.gridtest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

/**
 * Test page — ItemGrid with all slot/drag events to discover drag-and-drop behavior.
 */
public class ItemGridTestPage extends InteractiveCustomUIPage<ItemGridTestPage.EventPayload> {

    private static final Logger LOGGER = Logger.getLogger("ItemGridTestPage");
    private static final int MAX_LOG_LINES = 5;

    private final Deque<String> eventLog = new ArrayDeque<>();

    public ItemGridTestPage(@NonNull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {

        cmd.append("Pages/BlueprintBench/ItemGridTestPage.ui");
        LOGGER.info("[GridTest] build() start");

        // Populate grid with real items during build — activatable for click/drag events
        ItemGridSlot s1 = makeSlot("Rock_Stone", 4, "Rock Stone");
        ItemGridSlot s2 = makeSlot("Rock_Stone_Cobble", 8, "Cobblestone");
        ItemGridSlot s3 = makeSlot("Wood_Oak_Trunk", 2, "Oak Trunk");
        ItemGridSlot s4 = makeSlot("Wood_Hardwood_Planks", 16, "Hardwood Planks");
        cmd.set("#SourceGrid.Slots", new ItemGridSlot[] { s1, s2, s3, s4 });

        // Empty activatable input slot — drop target
        ItemGridSlot inputSlot = new ItemGridSlot();
        inputSlot.setActivatable(true);
        inputSlot.setName("Drop here");
        cmd.set("#InputGrid.Slots", new ItemGridSlot[] { inputSlot });

        // Bind all slot/drag events on both grids
        bindGridEvents(evt, "#SourceGrid", "Src");
        bindGridEvents(evt, "#InputGrid", "Input");

        cmd.set("#StatusLabel.Text", "Grid loaded — try dragging to input slot");
        LOGGER.info("[GridTest] build() complete");
    }

    private static ItemGridSlot makeSlot(String itemId, int qty, String name) {
        ItemGridSlot slot = new ItemGridSlot(new ItemStack(itemId, qty));
        slot.setActivatable(true);
        slot.setName(name);
        // No setIcon() — let the engine auto-resolve from ItemStack + SlotIconSize
        return slot;
    }

    private void bindGridEvents(UIEventBuilder evt, String gridId, String prefix) {
        // SlotClicking (13) — slot clicked
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, gridId,
                EventData.of("Action", prefix + ":SlotClicking"), false);

        // SlotDoubleClicking (14) — slot double-clicked
        evt.addEventBinding(CustomUIEventBindingType.SlotDoubleClicking, gridId,
                EventData.of("Action", prefix + ":SlotDoubleClicking"), false);

        // SlotMouseEntered (15) — mouse entered slot
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, gridId,
                EventData.of("Action", prefix + ":SlotMouseEntered"), false);

        // SlotMouseExited (16) — mouse exited slot
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseExited, gridId,
                EventData.of("Action", prefix + ":SlotMouseExited"), false);

        // DragCancelled (17) — drag cancelled
        evt.addEventBinding(CustomUIEventBindingType.DragCancelled, gridId,
                EventData.of("Action", prefix + ":DragCancelled"), false);

        // Dropped (18) — item dropped
        evt.addEventBinding(CustomUIEventBindingType.Dropped, gridId,
                EventData.of("Action", prefix + ":Dropped"), false);

        // SlotMouseDragCompleted (19) — drag completed
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseDragCompleted, gridId,
                EventData.of("Action", prefix + ":SlotMouseDragCompleted"), false);

        // SlotMouseDragExited (20) — drag exited bounds
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseDragExited, gridId,
                EventData.of("Action", prefix + ":SlotMouseDragExited"), false);

        // SlotClickReleaseWhileDragging (21)
        evt.addEventBinding(CustomUIEventBindingType.SlotClickReleaseWhileDragging, gridId,
                EventData.of("Action", prefix + ":SlotClickReleaseWhileDragging"), false);

        // SlotClickPressWhileDragging (22)
        evt.addEventBinding(CustomUIEventBindingType.SlotClickPressWhileDragging, gridId,
                EventData.of("Action", prefix + ":SlotClickPressWhileDragging"), false);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        if (data.action != null) {
            LOGGER.info("[GridTest] Event: " + data.action);
            logEvent(data.action);
            cmd.set("#StatusLabel.Text", data.action);
        } else {
            LOGGER.info("[GridTest] Unknown event (null action)");
            logEvent("null action event");
            cmd.set("#StatusLabel.Text", "Event with null action");
        }

        // Re-bind all grid events on every update
        bindGridEvents(evt, "#SourceGrid", "Src");
        bindGridEvents(evt, "#InputGrid", "Input");

        // Update event log display
        cmd.set("#EventLog.Text", getLogText());

        this.sendUpdate(cmd, evt, false);
    }

    private void logEvent(String msg) {
        eventLog.addFirst(msg);
        while (eventLog.size() > MAX_LOG_LINES) {
            eventLog.removeLast();
        }
    }

    private String getLogText() {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String entry : eventLog) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(i++).append(". ").append(entry);
        }
        return sb.toString();
    }

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .build();

        String action;
    }
}
