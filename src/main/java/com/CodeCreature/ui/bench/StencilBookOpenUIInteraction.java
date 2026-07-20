package com.CodeCreature.ui.bench;

/**
 * @node    StencilBookOpenUIInteraction
 * @wiki    docs/wiki/StencilBook/StencilBookOpenUIInteraction.md
 * @intent  Opens the production stencil selection page directly so renderer
 *          migration is handled inside page-level feature flags, not route swaps.
 * @wave    1 (dual renderer migration)
 * @status  Wave 1 - sandbox route dependency removed
 * @do-not  Reintroduce sandbox routing as a production migration path.
 */

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class StencilBookOpenUIInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<StencilBookOpenUIInteraction> CODEC =
            BuilderCodec.builder(StencilBookOpenUIInteraction.class,
                    StencilBookOpenUIInteraction::new, SimpleInstantInteraction.CODEC)
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        PageManager pageManager = playerComponent.getPageManager();
        if (pageManager.getCustomPage() != null) return;

        Store<EntityStore> store = commandBuffer.getStore();
        pageManager.openCustomPage(ref, store, new StencilSelectionPage(playerRef));
    }
}
