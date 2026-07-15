package com.CodeCreature.scaling;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Enforces scaled placement cost for natural resource blocks.
 *
 * <p>When a player places a block whose type is registered in
 * {@link NaturalResourceRegistry} as a natural block, this system
 * consumes ({@link ResourceConstants#RESOURCE_MULTIPLIER} - 1) additional
 * items from their inventory. The engine's own placement logic consumes
 * 1 more, totaling RESOURCE_MULTIPLIER items per placement.
 *
 * <p>This closes the duplication exploit where placing 1 item and
 * breaking it returned RESOURCE_MULTIPLIER items. With this system,
 * placing costs 12 and breaking returns 12 — net zero.
 *
 * <p>If the player's combined inventory (hotbar + storage) does not
 * contain enough items, the remaining items are consumed and placement
 * still proceeds (partial deduction rather than cancellation).
 *
 * <p><strong>Threading:</strong> Runs on the entity store's event
 * dispatch thread (same as all ECS event systems). No external
 * synchronization needed.
 */
public class PlacementCostScaler extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final int EXTRA_COST = ResourceConstants.RESOURCE_MULTIPLIER - 1;

    public PlacementCostScaler() {
        super(PlaceBlockEvent.class);
    }

    /**
     * Handles a block placement event. For natural resource blocks,
     * verifies the player has enough items and consumes the extra cost.
     *
     * <p>Execution order within the placement flow:
     * <ol>
     *   <li>Engine creates PlaceBlockEvent (item not yet consumed)</li>
     *   <li>This handler runs — may cancel or pre-consume items</li>
     *   <li>If not cancelled, engine consumes 1 from active slot</li>
     *   <li>Engine places block in world</li>
     * </ol>
     *
     * @param index          entity index within the archetype chunk
     * @param archetypeChunk archetype chunk containing entity components
     * @param store          entity store
     * @param commandBuffer  ECS command buffer
     * @param event          the placement event (cancellable)
     */
    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull PlaceBlockEvent event) {

        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null) return;

        // Stencil stencils handle their own resource consumption — skip extra cost
        if (StencilMetadata.isStencil(itemInHand)) return;

        String blockTypeId = itemInHand.getBlockKey();
        if (blockTypeId == null) return;

        if (!NaturalResourceRegistry.isNaturalBlock(blockTypeId)) return;

        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        if (player.getGameMode() != GameMode.Adventure) return;

        ItemStack removalStack = itemInHand.withQuantity(EXTRA_COST);
        if (removalStack == null) return;

        ItemContainer combined = player.getInventory().getCombinedHotbarFirst();

        if (combined.canRemoveItemStack(removalStack)) {
            combined.removeItemStack(removalStack);
        } else {
            // Insufficient for full cost — remove whatever remains.
            // Engine will still consume 1 from active slot after this handler.
            @SuppressWarnings("unused") // TODO: notification when UI approach is decided
            boolean partialRemoval = true;
            combined.removeItemStack(removalStack, false, true);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        // Match all entities — PlaceBlockEvent is only invoked on the placing entity,
        // so the query doesn't need narrowing. Matches the pattern used by
        // BlockHealthModule.PlaceBlockEventSystem.
        return Archetype.empty();
    }
}
