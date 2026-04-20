package com.UnobstructedThirdPerson.assignbench;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.Collector;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.UnobstructedThirdPerson.portablebench.PortableBenchConfig;
import com.UnobstructedThirdPerson.portablebench.PortableBenchRegistry;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Block interaction that opens the {@link AssignBenchWindow} when a player
 * right-clicks (Use) the Assignment Bench block.
 *
 * <p>Extends {@link SimpleBlockInteraction} because it targets a specific
 * block position. The block must exist at the target position for the
 * interaction to proceed.</p>
 *
 * <p>The window is stateless — created fresh on each interaction. Recipe
 * state lives in the PlaceBlock item's BSON metadata, not in the block.</p>
 *
 * <p>Registered as interaction type {@code "Assign_Bench"} in the
 * plugin's codec registry.</p>
 */
public class AssignBenchInteraction extends SimpleBlockInteraction {

    private static final Logger LOGGER = Logger.getLogger(AssignBenchInteraction.class.getSimpleName());

    /** Config key used to look up the Assignment Bench's recipe categories. */
    private static final String BENCH_CONFIG_KEY = "Bench_Assignment";

    public static final BuilderCodec<AssignBenchInteraction> CODEC =
            BuilderCodec.builder(AssignBenchInteraction.class, AssignBenchInteraction::new, SimpleBlockInteraction.CODEC)
                    .build();

    protected AssignBenchInteraction() {
    }

    public AssignBenchInteraction(@Nonnull String id) {
        super(id);
    }

    @Override
    protected void interactWithBlock(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler,
            int targetX, int targetY, int targetZ
    ) {
        if (!firstRun) {
            return;
        }

        // TODO: Implement the following logic:
        //
        // 1. Get entity ref and store:
        //    Ref<EntityStore> ref = context.getEntity();
        //    Store<EntityStore> store = ref.getStore();
        //
        // 2. Get Player component:
        //    Player player = store.getComponent(ref, Player.getComponentType());
        //    if (player == null) { context.getState().state = InteractionState.Failed; return; }
        //
        // 3. Look up Assignment Bench config:
        //    PortableBenchConfig config = PortableBenchRegistry.getConfig(BENCH_CONFIG_KEY);
        //    if (config == null) {
        //        LOGGER.warning("No config found for " + BENCH_CONFIG_KEY);
        //        context.getState().state = InteractionState.Failed;
        //        return;
        //    }
        //
        // 4. Create and open the Assignment Bench window:
        //    AssignBenchWindow window = new AssignBenchWindow(config);
        //    player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
        //
        // 5. Mark interaction as finished:
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
