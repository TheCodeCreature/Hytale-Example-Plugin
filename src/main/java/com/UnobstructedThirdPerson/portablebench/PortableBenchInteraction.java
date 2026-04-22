package com.UnobstructedThirdPerson.portablebench;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.Collector;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Custom interaction that opens a portable bench window when the player presses Q.
 *
 * <p>Extends {@link SimpleInteraction} (not {@code SimpleBlockInteraction}) because
 * this interaction does not target a block — it reads the held item's ID, looks up
 * its {@link PortableBenchConfig}, and opens a {@link PortableBenchWindow}.</p>
 */
public class PortableBenchInteraction extends SimpleInteraction {

    private static final Logger LOGGER = Logger.getLogger(PortableBenchInteraction.class.getSimpleName());

    public static final BuilderCodec<PortableBenchInteraction> CODEC =
            BuilderCodec.builder(PortableBenchInteraction.class, PortableBenchInteraction::new, SimpleInteraction.CODEC)
                    .build();

    protected PortableBenchInteraction() {
    }

    public PortableBenchInteraction(@Nonnull String id) {
        super(id);
    }

    @Override
    protected void tick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        if (!firstRun) {
            return;
        }

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Item item = heldItem.getItem();
        if (item == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        String itemId = item.getId();
        PortableBenchConfig config = PortableBenchRegistry.getConfig(itemId);
        if (config == null) {
            LOGGER.warning("No portable bench config for item: " + itemId);
            context.getState().state = InteractionState.Failed;
            return;
        }

        PortableBenchWindow window = new PortableBenchWindow(config);
        playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);

        context.getState().state = InteractionState.Finished;
    }

    @Override
    protected void simulateTick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        // No client-side simulation needed
    }

    @Override
    public boolean walk(@Nonnull Collector collector, @Nonnull InteractionContext context) {
        return false;
    }

    @Nonnull
    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.SimpleInteraction();
    }
}
