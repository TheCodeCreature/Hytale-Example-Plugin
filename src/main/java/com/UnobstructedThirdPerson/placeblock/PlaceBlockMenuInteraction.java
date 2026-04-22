package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
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
 * Interaction handler for the {@code PlaceBlock_Menu} interaction codec.
 *
 * <p>This interaction fires when the player uses the PlaceBlock tool (e.g., right-click
 * or designated key). In Phase 3, this will integrate with {@link com.UnobstructedThirdPerson.preview.PreviewBlockManager}
 * to show ghost blocks when the player aims at a surface while holding an armed placeholder.</p>
 *
 * <p>The interaction codec is already registered in
 * {@link com.UnobstructedThirdPersonPlugin#setup()} as {@code "PlaceBlock_Menu"}.</p>
 *
 * <p><strong>Interaction flow:</strong>
 * <ul>
 *   <li>Phase 1: Stub — interaction registered but does nothing meaningful</li>
 *   <li>Phase 3: When armed, integrates with preview system to show ghost blocks</li>
 * </ul>
 *
 * <p>Note: Actual block placement is handled by {@link PlaceBlockPlacementSystem}
 * via the ECS {@code PlaceBlockEvent}, NOT by this interaction. This interaction
 * handles the UI/preview side of the tool.</p>
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
            @Nonnull CooldownHandler cooldownHandler) {

        // TODO Phase 1: Stub — log that the interaction fired, set state to Finished.
        //
        // TODO Phase 3: If the held item is an armed PlaceBlock:
        //       1. Get player's aim position (look direction + raycast)
        //       2. Use PreviewBlockManager to show a ghost block at the aim position
        //       3. Update the ghost as the player looks around (on each tick)
        //       4. Clear ghost blocks when the interaction ends or item changes
        //
        // Note: The actual PlaceBlockEvent (placement + consumption) is handled
        // by PlaceBlockPlacementSystem, not here. The engine fires PlaceBlockEvent
        // independently when the player right-clicks to place.

        if (!firstRun) return;
        context.getState().state = InteractionState.Finished;
    }

    @Override
    protected void simulateTick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
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
