package com.CodeCreature.ui.bench;

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

        StencilSelectionPage page = new StencilSelectionPage(playerRef);
        Store<EntityStore> store = commandBuffer.getStore();
        pageManager.openCustomPage(ref, store, page);
    }
}
