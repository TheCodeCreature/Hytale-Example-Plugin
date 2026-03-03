package com.UnobstructedThirdPerson.movement;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NewMovementSystem extends EntityTickingSystem<EntityStore> {

    public static final double DEFAULT_MOON_GRAVITY_FACTOR = 0.165;
    private static final double MIN_GRAVITY_FACTOR = -10.0;
    private static final double MAX_GRAVITY_FACTOR = 10.0;
    private static final double EPSILON = 0.0001;

    private static final ConcurrentHashMap<UUID, Double> MOON_GRAVITY_FACTORS = new ConcurrentHashMap<>();
    private static final Set<UUID> MOON_PROFILE_APPLIED = ConcurrentHashMap.newKeySet();

    @Nonnull
    private final ComponentType<EntityStore, PlayerRef> playerRefComponentType;
    private final ComponentType<EntityStore, MovementManager> movementManagerComponentType;
    @Nonnull
    private final Query<EntityStore> query;

    public NewMovementSystem() {
        this.playerRefComponentType = PlayerRef.getComponentType();
        this.movementManagerComponentType = MovementManager.getComponentType();
        this.query = Query.and(
            this.playerRefComponentType,
            this.movementManagerComponentType
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

        if (playerRef == null || movementManager == null) {
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

        float signedScale = (float) clamp(gravityFactor);
        float gravityScale = Math.max(0.05F, Math.abs(signedScale));
        float moonStrength = 1.0F - Math.min(gravityScale, 1.0F);

        float targetMass = defaults.mass * gravityScale;
        float targetDragCoefficient = defaults.dragCoefficient * (1.0F + moonStrength * 5.0F);
        boolean targetInvertedGravity = signedScale < 0.0F ? !defaults.invertedGravity : defaults.invertedGravity;

        float targetJumpForce = defaults.jumpForce * (1.0F + moonStrength * 0.25F);
        float targetSwimJumpForce = defaults.swimJumpForce * (1.0F + moonStrength * 0.2F);
        float targetFallForce = defaults.variableJumpFallForce * gravityScale;
        float targetFallJumpForce = defaults.fallJumpForce * (0.85F + moonStrength * 0.15F);
        float targetMinRoll = defaults.minFallSpeedToEngageRoll * gravityScale;
        float targetMaxRoll = defaults.maxFallSpeedToEngageRoll * gravityScale;

        // Keep air motion glidey: less friction and less damping while airborne.
        float targetAirDragMin = lerp(defaults.airDragMin, 0.995F, moonStrength);
        float targetAirDragMax = lerp(defaults.airDragMax, 0.999F, moonStrength);
        float targetAirFrictionMin = lerp(defaults.airFrictionMin, defaults.airFrictionMin * 0.25F, moonStrength);
        float targetAirFrictionMax = lerp(defaults.airFrictionMax, defaults.airFrictionMax * 0.25F, moonStrength);
        float targetAirSpeedMultiplier = defaults.airSpeedMultiplier * (1.0F + moonStrength * 0.2F);

        boolean changed = false;
        changed |= assignIfChanged(active.mass, targetMass, value -> active.mass = value);
        changed |= assignIfChanged(active.dragCoefficient, targetDragCoefficient, value -> active.dragCoefficient = value);
        changed |= assignIfChanged(active.jumpForce, targetJumpForce, value -> active.jumpForce = value);
        changed |= assignIfChanged(active.swimJumpForce, targetSwimJumpForce, value -> active.swimJumpForce = value);
        changed |= assignIfChanged(active.variableJumpFallForce, targetFallForce, value -> active.variableJumpFallForce = value);
        changed |= assignIfChanged(active.fallJumpForce, targetFallJumpForce, value -> active.fallJumpForce = value);
        changed |= assignIfChanged(active.minFallSpeedToEngageRoll, targetMinRoll, value -> active.minFallSpeedToEngageRoll = value);
        changed |= assignIfChanged(active.maxFallSpeedToEngageRoll, targetMaxRoll, value -> active.maxFallSpeedToEngageRoll = value);
        changed |= assignIfChanged(active.airDragMin, targetAirDragMin, value -> active.airDragMin = value);
        changed |= assignIfChanged(active.airDragMax, targetAirDragMax, value -> active.airDragMax = value);
        changed |= assignIfChanged(active.airFrictionMin, targetAirFrictionMin, value -> active.airFrictionMin = value);
        changed |= assignIfChanged(active.airFrictionMax, targetAirFrictionMax, value -> active.airFrictionMax = value);
        changed |= assignIfChanged(active.airSpeedMultiplier, targetAirSpeedMultiplier, value -> active.airSpeedMultiplier = value);

        if (active.invertedGravity != targetInvertedGravity) {
            active.invertedGravity = targetInvertedGravity;
            changed = true;
        }

        if (changed || !MOON_PROFILE_APPLIED.contains(playerId)) {
            movementManager.update(playerRef.getPacketHandler());
        }

        MOON_PROFILE_APPLIED.add(playerId);
    }

    private static boolean assignIfChanged(float currentValue, float targetValue, @Nonnull FloatSetter setter) {
        if (isWithinEpsilon(currentValue, targetValue)) {
            return false;
        }
        setter.set(targetValue);
        return true;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
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

    private static boolean isWithinEpsilon(float a, float b) {
        return Math.abs(a - b) <= EPSILON;
    }

    private static double clamp(double value) {
        return Math.max(NewMovementSystem.MIN_GRAVITY_FACTOR, Math.min(NewMovementSystem.MAX_GRAVITY_FACTOR, value));
    }
}
