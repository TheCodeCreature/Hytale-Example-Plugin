package com.UnobstructedThirdPerson.fix;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Logger;

public class BlockInteractionEventSystems {
    private static final Logger LOGGER = Logger.getLogger("BlockInteractionEvents");

    public static class PlaceBlockEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        public PlaceBlockEventSystem() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event
        ) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null) {
                return;
            }

            UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return;
            }

            UUID playerUuid = uuidComponent.getUuid();
            if (!InteractionPositionFixer.isEnabledForPlayer(playerUuid)) {
                return;
            }

            Vector3i serverTarget = InteractionPositionFixer.getCachedServerTarget(playerUuid);
            Vector3i originalTarget = event.getTargetBlock();

            if (serverTarget != null && InteractionPositionFixer.isRedirectMode()) {
                // Redirect to server-calculated position
                event.setTargetBlock(serverTarget);
                LOGGER.info("[BlockEvent] REDIRECTED PlaceBlockEvent from " + 
                    originalTarget.x + "," + originalTarget.y + "," + originalTarget.z + " to " +
                    serverTarget.x + "," + serverTarget.y + "," + serverTarget.z);
            } else {
                // Block mode - cancel the event
                event.setCancelled(true);
                LOGGER.info("[BlockEvent] CANCELLED PlaceBlockEvent at " + 
                    originalTarget.x + "," + originalTarget.y + "," + originalTarget.z);
            }
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    public static class BreakBlockEventSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        public BreakBlockEventSystem() {
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
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null) {
                return;
            }

            UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return;
            }

            UUID playerUuid = uuidComponent.getUuid();
            if (!InteractionPositionFixer.isEnabledForPlayer(playerUuid)) {
                return;
            }

            Vector3i serverTarget = InteractionPositionFixer.getCachedServerTarget(playerUuid);
            Vector3i originalTarget = event.getTargetBlock();

            if (serverTarget != null && InteractionPositionFixer.isRedirectMode()) {
                // Redirect to server-calculated position
                event.setTargetBlock(serverTarget);
                LOGGER.info("[BlockEvent] REDIRECTED BreakBlockEvent from " + 
                    originalTarget.x + "," + originalTarget.y + "," + originalTarget.z + " to " +
                    serverTarget.x + "," + serverTarget.y + "," + serverTarget.z);
            } else {
                // Block mode - cancel the event
                event.setCancelled(true);
                LOGGER.info("[BlockEvent] CANCELLED BreakBlockEvent at " + 
                    originalTarget.x + "," + originalTarget.y + "," + originalTarget.z);
            }
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
