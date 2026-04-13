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
 * ECS EntityEventSystem that intercepts BreakBlockEvent for natural resource blocks.
 * Delegates to {@link NaturalResourceDropListener} for multiplied drops.
 */
public class BreakBlockNaturalSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BreakBlockNaturalSystem() {
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
        NaturalResourceDropListener.onBlockBreak(event, store);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
