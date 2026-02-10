package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class ApplyCameraOnSpawnSystem extends RefSystem<EntityStore> {

    private static final Logger LOGGER = Logger.getLogger(ApplyCameraOnSpawnSystem.class.getName());

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType()
        );
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, PlayerSystems.PlayerSpawnedSystem.class)
        );
    }

    @Override
    public void onEntityAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        try {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                LOGGER.log(Level.INFO, "Applying camera settings on player spawn");
                CameraSettingsApplier.applyToPlayer(playerRef);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply camera settings on player spawn", e);
        }
    }

    @Override
    public void onEntityRemove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // No action needed on remove
    }
}
