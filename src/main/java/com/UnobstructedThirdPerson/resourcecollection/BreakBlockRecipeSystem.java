package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ECS EntityEventSystem that intercepts BreakBlockEvent.
 * Dispatched via entityStore.invoke(ref, event) — this is the correct
 * way to listen for block break events in Hytale's ECS pipeline.
 */
public class BreakBlockRecipeSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BreakBlockRecipeSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event
    ) {
        // Delegate to the existing static handler which has all the logic
        RecipeDropListener.onBlockBreak(event);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        // Match all entities — we filter by block type inside the handler
        return Archetype.empty();
    }
}
