package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import java.util.logging.Logger;

/**
 * Custom interaction that opens the PlaceBlock recipe selector window
 * when the player presses F (Use key) while holding a PlaceBlock item.
 *
 * <p>Extends {@link SimpleInteraction} — same pattern as
 * {@link com.UnobstructedThirdPerson.portablebench.PortableBenchInteraction}.</p>
 *
 * <p>The opened {@link PlaceBlockSelectorWindow} allows the player to:
 * <ul>
 *   <li>Browse recipes from all configured benches (Builders + Furniture)</li>
 *   <li>Assign a recipe to the held PlaceBlock</li>
 *   <li>Clear the current recipe assignment</li>
 * </ul>
 *
 * <p>Registered as interaction type {@code "PlaceBlock_Menu"} in the
 * plugin's codec registry.</p>
 */
public class PlaceBlockMenuInteraction extends SimpleInteraction {

    private static final Logger LOGGER = Logger.getLogger(PlaceBlockMenuInteraction.class.getSimpleName());

    public static final BuilderCodec<PlaceBlockMenuInteraction> CODEC =
            BuilderCodec.builder(PlaceBlockMenuInteraction.class, PlaceBlockMenuInteraction::new, SimpleInteraction.CODEC)
                    .build();

    protected PlaceBlockMenuInteraction() {
    }

    public PlaceBlockMenuInteraction(@Nonnull String id) {
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

        // TODO: Implement the following logic:
        //
        // 1. Get entity ref and store from context:
        //    Ref<EntityStore> ref = context.getEntity();
        //    Store<EntityStore> store = ref.getStore();
        //
        // 2. Get Player component:
        //    Player player = store.getComponent(ref, Player.getComponentType());
        //    if (player == null) { context.getState().state = InteractionState.Failed; return; }
        //
        // 3. Validate held item is a PlaceBlock:
        //    ItemStack heldItem = context.getHeldItem();
        //    if (heldItem == null || !PlaceBlockMetadata.isPlaceBlock(heldItem)) {
        //        context.getState().state = InteractionState.Failed; return;
        //    }
        //
        // 4. Look up config for the Assignment Bench (or a combined config
        //    that aggregates Builders + Furniture categories):
        //    PortableBenchConfig config = PortableBenchRegistry.getConfig("Bench_Assignment");
        //
        // 5. Create and open the selector window:
        //    PlaceBlockSelectorWindow window = new PlaceBlockSelectorWindow(config, heldItem);
        //    player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
        //
        // 6. Mark interaction as finished:
        //    context.getState().state = InteractionState.Finished;

        context.getState().state = InteractionState.Failed; // Placeholder until implemented
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
