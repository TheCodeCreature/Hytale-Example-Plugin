package com.CodeCreature.stencil;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Intercepts stencil items being dropped and prevents them from spawning
 * as world entities. By the time {@code DropItemEvent.Drop} fires, the
 * item has already been removed from inventory — cancelling this event
 * simply prevents the world item spawn, effectively destroying the stencil.
 *
 * <p>This works for both G-key drops and drag-out-of-inventory drops.</p>
 *
 * <p><b>Registration:</b> In plugin {@code setup()}:
 * {@code getEntityStoreRegistry().registerSystem(new StencilDropDestroySystem())}</p>
 */
public class StencilDropDestroySystem extends EntityEventSystem<EntityStore, DropItemEvent.Drop> {

    public StencilDropDestroySystem() {
        super(DropItemEvent.Drop.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull DropItemEvent.Drop event) {
        ItemStack item = event.getItemStack();
        if (StencilMetadata.isStencil(item)) {
            event.setCancelled(true);

            // The G-key drop removes 1 from qty 2, leaving qty 1.
            // StencilSyncSystem restores qty 1→2 before we get here.
            // Clear the matching stencil from the hotbar to complete deletion.
            Player player = archetypeChunk.getComponent(index, Player.getComponentType());
            if (player != null) {
                String droppedRecipeId = StencilMetadata.getRecipeId(item);
                ItemContainer hotbar = player.getInventory().getHotbar();
                if (hotbar == null) return;
                short capacity = hotbar.getCapacity();
                for (short slot = 0; slot < capacity; slot++) {
                    ItemStack stack = hotbar.getItemStack(slot);
                    if (stack == null) continue;
                    if (!StencilMetadata.isStencil(stack)) continue;
                    if (stack.getItemId().equals(item.getItemId())
                            && java.util.Objects.equals(StencilMetadata.getRecipeId(stack), droppedRecipeId)) {
                        hotbar.removeItemStackFromSlot(slot);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
