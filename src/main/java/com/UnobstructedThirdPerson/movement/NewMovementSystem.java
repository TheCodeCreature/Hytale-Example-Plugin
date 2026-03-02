package com.UnobstructedThirdPerson.movement;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NewMovementSystem extends EntityTickingSystem<EntityStore> {

    private static final double DEFAULT_MOON_GRAVITY_FACTOR = 0.165;
    private static final double MIN_GRAVITY_FACTOR = 0.05;
    private static final double MAX_GRAVITY_FACTOR = 1.0;
    private static final double EPSILON = 0.0001;

    private static final ConcurrentHashMap<UUID, Double> MOON_GRAVITY_FACTORS = new ConcurrentHashMap<>();
    private static final Set<UUID> MOON_PROFILE_APPLIED = ConcurrentHashMap.newKeySet();

    @Nonnull
    private final ComponentType<EntityStore, PlayerRef> playerRefComponentType;
    private final ComponentType<EntityStore, MovementManager> movementManagerComponentType;
    @Nonnull
    private final ComponentType<EntityStore, TransformComponent> transformComponentType;
    @Nonnull
    private final ComponentType<EntityStore, Velocity> velocityComponentType;
    @Nonnull
    private final Query<EntityStore> query;

    public NewMovementSystem() {
        this.playerRefComponentType = PlayerRef.getComponentType();
        this.movementManagerComponentType = MovementManager.getComponentType();
        this.transformComponentType = TransformComponent.getComponentType();
        this.velocityComponentType = Velocity.getComponentType();
        this.query = Query.and(
            this.playerRefComponentType,
            this.movementManagerComponentType,
            this.transformComponentType,
            this.velocityComponentType
        );
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerRef playerRef = archetypeChunk.getComponent(index, this.playerRefComponentType);
        MovementManager movementManager = archetypeChunk.getComponent(index, this.movementManagerComponentType);
        TransformComponent transform = archetypeChunk.getComponent(index, this.transformComponentType);
        Velocity velocity = archetypeChunk.getComponent(index, this.velocityComponentType);

        if (playerRef == null || movementManager == null || transform == null || velocity == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        applyMoonGravity(playerId, movementManager, playerRef);
    }

    private static void applyMoonGravity(@Nonnull UUID playerId, @Nonnull MovementManager movementManager, @Nonnull PlayerRef playerRef) {
        Double gravityFactor = MOON_GRAVITY_FACTORS.get(playerId);
        if (gravityFactor == null) {
            if (MOON_PROFILE_APPLIED.remove(playerId)) {
                movementManager.applyDefaultSettings();
                movementManager.update(playerRef.getPacketHandler());
            }
            return;
        }

        MovementSettings defaults = movementManager.getDefaultSettings();
        MovementSettings active = movementManager.getSettings();
        if (defaults == null || active == null) {
            return;
        }

        float scale = (float) clamp(gravityFactor);
        float jumpBoost = (float) (1.0 / Math.sqrt(scale));

        float targetJumpForce = defaults.jumpForce * jumpBoost;
        float targetSwimJumpForce = defaults.swimJumpForce * jumpBoost;
        float targetFallForce = defaults.variableJumpFallForce * scale;
        float targetFallJumpForce = defaults.fallJumpForce * jumpBoost;
        float targetMinRoll = defaults.minFallSpeedToEngageRoll * scale;
        float targetMaxRoll = defaults.maxFallSpeedToEngageRoll * scale;

        if (approximately(active.jumpForce, targetJumpForce)
            || approximately(active.swimJumpForce, targetSwimJumpForce)
            || approximately(active.variableJumpFallForce, targetFallForce)
            || approximately(active.fallJumpForce, targetFallJumpForce)
            || approximately(active.minFallSpeedToEngageRoll, targetMinRoll)
            || approximately(active.maxFallSpeedToEngageRoll, targetMaxRoll)) {
            active.jumpForce = targetJumpForce;
            active.swimJumpForce = targetSwimJumpForce;
            active.variableJumpFallForce = targetFallForce;
            active.fallJumpForce = targetFallJumpForce;
            active.minFallSpeedToEngageRoll = targetMinRoll;
            active.maxFallSpeedToEngageRoll = targetMaxRoll;
            active.variableJumpFallForce = 1;
            movementManager.update(playerRef.getPacketHandler());
        }

        MOON_PROFILE_APPLIED.add(playerId);
    }

    public static void enableMoonGravity(@Nonnull UUID playerId) {
        enableMoonGravity(playerId, DEFAULT_MOON_GRAVITY_FACTOR);
    }

    public static void enableMoonGravity(@Nonnull UUID playerId, double gravityFactor) {
        MOON_GRAVITY_FACTORS.put(playerId, clamp(gravityFactor));
    }

    public static void disableMoonGravity(@Nonnull UUID playerId) {
        MOON_GRAVITY_FACTORS.remove(playerId);
    }

    public static boolean isMoonGravityEnabled(@Nonnull UUID playerId) {
        return MOON_GRAVITY_FACTORS.containsKey(playerId);
    }

    @Nullable
    public static Double getMoonGravityFactor(@Nonnull UUID playerId) {
        return MOON_GRAVITY_FACTORS.get(playerId);
    }

    private static boolean approximately(float a, float b) {
        return !(Math.abs(a - b) <= EPSILON);
    }

    private static double clamp(double value) {
        return Math.max(NewMovementSystem.MIN_GRAVITY_FACTOR, Math.min(NewMovementSystem.MAX_GRAVITY_FACTOR, value));
    }
}
