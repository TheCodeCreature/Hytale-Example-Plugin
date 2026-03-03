package com.UnobstructedThirdPerson.movement;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
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
    private static final double FALL_SPEED_EPSILON = 0.1;
    private static final float BASE_FLIP_INTERVAL_SECONDS = 0.35F;
    private static final float MIN_FLIP_INTERVAL_SECONDS = 0.06F;
    private static final float FLIP_SPEED_SCALAR = 4.0F;

    private static final ConcurrentHashMap<UUID, Double> MOON_GRAVITY_FACTORS = new ConcurrentHashMap<>();
    private static final Set<UUID> MOON_PROFILE_APPLIED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Float> GRAVITY_FLIP_ELAPSED = new ConcurrentHashMap<>();
    private static final Set<UUID> GRAVITY_FLIP_PHASE = ConcurrentHashMap.newKeySet();

    @Nonnull
    private final ComponentType<EntityStore, PlayerRef> playerRefComponentType;
    private final ComponentType<EntityStore, MovementManager> movementManagerComponentType;
    @Nonnull
    private final ComponentType<EntityStore, Velocity> velocityComponentType;
    @Nonnull
    private final Query<EntityStore> query;

    public NewMovementSystem() {
        this.playerRefComponentType = PlayerRef.getComponentType();
        this.movementManagerComponentType = MovementManager.getComponentType();
        this.velocityComponentType = Velocity.getComponentType();
        this.query = Query.and(
            this.playerRefComponentType,
            this.movementManagerComponentType,
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
        Velocity velocity = archetypeChunk.getComponent(index, this.velocityComponentType);

        if (playerRef == null || movementManager == null || velocity == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        applyMoonGravity(playerId, movementManager, playerRef, velocity, dt);
    }

    private static void applyMoonGravity(
        @Nonnull UUID playerId,
        @Nonnull MovementManager movementManager,
        @Nonnull PlayerRef playerRef,
        @Nonnull Velocity velocity,
        float dt
    ) {
        Double gravityFactor = MOON_GRAVITY_FACTORS.get(playerId);
        if (gravityFactor == null) {
            if (MOON_PROFILE_APPLIED.remove(playerId)) {
                movementManager.applyDefaultSettings();
                movementManager.update(playerRef.getPacketHandler());
            }
            GRAVITY_FLIP_ELAPSED.remove(playerId);
            GRAVITY_FLIP_PHASE.remove(playerId);
            return;
        }

        MovementSettings defaults = movementManager.getDefaultSettings();
        MovementSettings active = movementManager.getSettings();
        if (defaults == null || active == null) {
            return;
        }

        float scale = (float) clamp(gravityFactor);

        // Vertical launch impulse from a grounded jump. Higher => faster takeoff and taller jump.
        float targetJumpForce = defaults.jumpForce * scale;
        // Vertical launch impulse when swimming. Keep aligned with jump-force feel in water.
        float targetSwimJumpForce = defaults.swimJumpForce * scale;
        // Additional gravity-like downward force while airborne. Lower => slower fall.
        float targetFallForce = defaults.variableJumpFallForce * scale;
        // Extra jump force used in fall-related jump transitions. Higher => snappier rebound behavior.
        float targetFallJumpForce = defaults.fallJumpForce * scale;
        // Minimum vertical speed before roll logic engages. Scale with gravity profile.
        float targetMinRoll = defaults.minFallSpeedToEngageRoll * scale;
        // Maximum vertical speed for roll engagement window. Scale with gravity profile.
        float targetMaxRoll = defaults.maxFallSpeedToEngageRoll * scale;

        float airDragMin = defaults.airDragMin * scale;
        float airDragMax = defaults.airDragMax * scale;
        float airDragMinSpeed = defaults.airDragMinSpeed * scale;
        float airDragMaxSpeed = defaults.airDragMaxSpeed * scale;
        float airFrictionMin = defaults.airFrictionMin * scale;
        float airFrictionMax = defaults.airFrictionMax * scale;
        float airFrictionMinSpeed = defaults.airFrictionMinSpeed * scale;
        float airFrictionMaxSpeed = defaults.airFrictionMaxSpeed * scale;

        float mass = defaults.mass * scale;
//        float comboAirSpeedMultiplier = defaults.comboAirSpeedMultiplier * scale;

        boolean changed = false;
        changed |= assignIfChanged(active.mass, mass, value -> active.mass = value);
        changed |= assignIfChanged(active.jumpForce, targetJumpForce, value -> active.jumpForce = value);
        changed |= assignIfChanged(active.swimJumpForce, targetSwimJumpForce, value -> active.swimJumpForce = value);
        changed |= assignIfChanged(active.variableJumpFallForce, targetFallForce, value -> active.variableJumpFallForce = value);
        changed |= assignIfChanged(active.fallJumpForce, targetFallJumpForce, value -> active.fallJumpForce = value);
        changed |= assignIfChanged(active.minFallSpeedToEngageRoll, targetMinRoll, value -> active.minFallSpeedToEngageRoll = value);
        changed |= assignIfChanged(active.maxFallSpeedToEngageRoll, targetMaxRoll, value -> active.maxFallSpeedToEngageRoll = value);
        changed |= assignIfChanged(active.airDragMin, airDragMin, value -> active.airDragMin = value);
        changed |= assignIfChanged(active.airDragMax, airDragMax, value -> active.airDragMax = value);
        changed |= assignIfChanged(active.airDragMinSpeed, airDragMinSpeed, value -> active.airDragMinSpeed = value);
        changed |= assignIfChanged(active.airDragMaxSpeed, airDragMaxSpeed, value -> active.airDragMaxSpeed = value);
        changed |= assignIfChanged(active.airFrictionMin, airFrictionMin, value -> active.airFrictionMin = value);
        changed |= assignIfChanged(active.airFrictionMax, airFrictionMax, value -> active.airFrictionMax = value);
        changed |= assignIfChanged(active.airFrictionMinSpeed, airFrictionMinSpeed, value -> active.airFrictionMinSpeed = value);
        changed |= assignIfChanged(active.airFrictionMaxSpeed, airFrictionMaxSpeed, value -> active.airFrictionMaxSpeed = value);

        changed |= applyInvertedGravityOscillation(playerId, defaults, active, velocity, dt);

        if (changed || !MOON_PROFILE_APPLIED.contains(playerId)) {
            movementManager.update(playerRef.getPacketHandler());
        }

        MOON_PROFILE_APPLIED.add(playerId);
    }

    private static boolean applyInvertedGravityOscillation(
        @Nonnull UUID playerId,
        @Nonnull MovementSettings defaults,
        @Nonnull MovementSettings active,
        @Nonnull Velocity velocity,
        float dt
    ) {
        int naturalFallDirection = defaults.invertedGravity ? 1 : -1;
        double verticalSpeed = velocity.getY();
        boolean isFallingNaturally = verticalSpeed * naturalFallDirection < -FALL_SPEED_EPSILON;

        boolean phaseFlipped = false;
        if (isFallingNaturally) {
            float speed = (float) Math.abs(verticalSpeed);
            float interval = computeFlipInterval(speed);
            float elapsed = GRAVITY_FLIP_ELAPSED.getOrDefault(playerId, 0.0F) + Math.max(0.0F, dt);
            if (elapsed >= interval) {
                if (!GRAVITY_FLIP_PHASE.add(playerId)) {
                    GRAVITY_FLIP_PHASE.remove(playerId);
                }
                elapsed = 0.0F;
                phaseFlipped = true;
            }
            GRAVITY_FLIP_ELAPSED.put(playerId, elapsed);
        } else {
            GRAVITY_FLIP_ELAPSED.remove(playerId);
            GRAVITY_FLIP_PHASE.remove(playerId);
        }

        boolean targetInverted = defaults.invertedGravity ^ GRAVITY_FLIP_PHASE.contains(playerId);
        if (active.invertedGravity != targetInverted) {
            active.invertedGravity = targetInverted;
            return true;
        }
        return phaseFlipped;
    }

    private static float computeFlipInterval(float speed) {
        double divisor = 1.0 + Math.log1p(Math.max(0.0, speed * FLIP_SPEED_SCALAR));
        return (float) Math.max(MIN_FLIP_INTERVAL_SECONDS, BASE_FLIP_INTERVAL_SECONDS / divisor);
    }

    private static boolean assignIfChanged(float currentValue, float targetValue, @Nonnull FloatSetter setter) {
        if (isWithinEpsilon(currentValue, targetValue)) {
            return false;
        }
        setter.set(targetValue);
        return true;
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
