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

/**
 * Dedicated sandbox page route for Stencil Book UI experimentation.
 *
 * <p>This page intentionally bypasses all production StencilSelectionPage logic
 * and only loads the sandbox .ui document.
 */
public class StencilBookSandboxPage extends InteractiveCustomUIPage<StencilBookSandboxPage.EventPayload> {

    private static final String SANDBOX_UI_PATH = "Pages/StencilBook/Sandbox/StencilBookSandboxPage.ui";

    public StencilBookSandboxPage(@NonNull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {
        cmd.append(SANDBOX_UI_PATH);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {
        sendUpdate(new UICommandBuilder(), null, false);
    }

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC =
                BuilderCodec.builder(EventPayload.class, EventPayload::new).build();
    }
}
