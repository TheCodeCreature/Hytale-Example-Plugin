package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Logger;

/**
 * ECS event system that handles {@link PlaceBlockEvent} for armed PlaceBlock
 * placeholder items.
 *
 * <p><strong>Contract #12 — Mutual Exclusion:</strong> This system and
 * {@link com.UnobstructedThirdPerson.resourcecollection.PlacementCostScaler}
 * both subscribe to {@code PlaceBlockEvent}. They are mutually exclusive:
 * <ul>
 *   <li>This system handles events where the held item IS an armed PlaceBlock</li>
 *   <li>{@code PlacementCostScaler} handles events where the held item is a
 *       natural resource block</li>
 *   <li>Both early-exit for events that aren't theirs</li>
 * </ul>
 *
 * <p><strong>Contract #11 — Atomic Consumption:</strong> When handling an armed
 * PlaceBlock placement, this system:
 * <ol>
 *   <li>Looks up the armed recipe</li>
 *   <li>Scans available resources (inventory + nearby chests)</li>
 *   <li>Attempts atomic consumption via {@link ResourceScanner#consumeAtomically}</li>
 *   <li>If successful: allows the engine to place the recipe's output block</li>
 *   <li>If unsuccessful: cancels the {@code PlaceBlockEvent}</li>
 * </ol>
 *
 * <p><strong>Contract #14 — Indistinguishable Blocks:</strong> The placed block
 * must be the actual recipe output block type, not the placeholder. This requires
 * overriding the block type that the engine places (RISK R3).
 *
 * <p><strong>Threading:</strong> Runs on the entity store's event dispatch thread,
 * same as {@code PlacementCostScaler}. No external synchronization needed.
 *
 * <p>Registered in {@link com.UnobstructedThirdPersonPlugin#setup()} via
 * {@code getEntityStoreRegistry().registerSystem(new PlaceBlockPlacementSystem())}.
 */
public class PlaceBlockPlacementSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final Logger LOGGER = Logger.getLogger(PlaceBlockPlacementSystem.class.getSimpleName());

    public PlaceBlockPlacementSystem() {
        super(PlaceBlockEvent.class);
    }

    /**
     * Handles a {@code PlaceBlockEvent} for armed PlaceBlock items.
     *
     * <p>Execution within the placement flow:
     * <ol>
     *   <li>Engine creates PlaceBlockEvent</li>
     *   <li>{@code PlacementCostScaler} runs — early-exits for PlaceBlock items</li>
     *   <li>This handler runs — early-exits for non-PlaceBlock items</li>
     *   <li>If armed and affordable: consume resources, override block type, allow placement</li>
     *   <li>If unarmed or unaffordable: cancel the event</li>
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

        // ── Guard: only handle PlaceBlock items ──
        if (!PlaceBlockMetadata.isPlaceBlock(itemInHand)) return;

        // ── Guard: must be armed with a recipe ──
        if (!PlaceBlockMetadata.isArmed(itemInHand)) {
            // Unarmed placeholder — deny placement
            event.setCancelled(true);
            return;
        }

        // TODO Phase 3:
        // 1. Get the armed recipe ID via PlaceBlockMetadata.getArmedRecipeId(itemInHand)
        // 2. Look up the CraftingRecipe asset
        // 3. Get the Player component from archetypeChunk
        // 4. Get the World from the entity store
        // 5. Read PlaceBlockConfig for chest radius
        // 6. Scan resources via ResourceScanner.scanAvailableResources(player, world, hR, vR)
        // 7. Attempt ResourceScanner.consumeAtomically(snapshot, recipe.getInput())
        // 8. If consumption succeeds:
        //    a. Override the placed block type to the recipe's output block (RISK R3)
        //       - May need to cancel event and manually call World.setBlock()
        //    b. Trigger PlaceBlockIndicatorListener.triggerUpdate(player)
        // 9. If consumption fails:
        //    a. Cancel the event: event.setCancelled(true)
        //    b. Trigger PlaceBlockIndicatorListener.triggerUpdate(player)
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        // Match all entities — PlaceBlockEvent only fires on the placing entity.
        // Same pattern as PlacementCostScaler.
        return Archetype.empty();
    }
}
