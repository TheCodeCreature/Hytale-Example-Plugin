package com.UnobstructedThirdPerson.movement;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ChangeVelocityType;
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
    private static final double MAX_FALL_SPEED = 1.0;

    private static final ConcurrentHashMap<UUID, Double> MOON_GRAVITY_FACTORS = new ConcurrentHashMap<>();
    private static final Set<UUID> MOON_PROFILE_APPLIED = ConcurrentHashMap.newKeySet();

    @Nonnull
    private final ComponentType<EntityStore, PlayerRef> playerRefComponentType;
    @Nonnull
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

        applyMoonGravity(playerId, movementManager, playerRef, velocity);
    }

    private static void applyMoonGravity(
        @Nonnull UUID playerId,
        @Nonnull MovementManager movementManager,
        @Nonnull PlayerRef playerRef,
        @Nonnull Velocity velocity
    ) {
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

        active.mass = mass;
        clampVerticalFallVelocity(velocity, active.invertedGravity);
        movementManager.update(playerRef.getPacketHandler());

        if (!isWithinEpsilon(active.jumpForce, targetJumpForce)
            || !isWithinEpsilon(active.swimJumpForce, targetSwimJumpForce)
            || !isWithinEpsilon(active.variableJumpFallForce, targetFallForce)
            || !isWithinEpsilon(active.fallJumpForce, targetFallJumpForce)
            || !isWithinEpsilon(active.minFallSpeedToEngageRoll, targetMinRoll)
            || !isWithinEpsilon(active.maxFallSpeedToEngageRoll, targetMaxRoll)) {
            // Controls vertical takeoff speed on normal jump.
//            active.jumpForce = targetJumpForce;
//            // Controls vertical takeoff speed on swim jump.
//            active.swimJumpForce = targetSwimJumpForce;
//            // Controls airborne downward pull (primary falling-speed dial).
//            active.variableJumpFallForce = targetFallForce;
//            // Controls extra jump force used during fall-transition jump logic.
//            active.fallJumpForce = targetFallJumpForce;
//            // Controls when rolling starts based on fall speed.
//            active.minFallSpeedToEngageRoll = targetMinRoll;
//            // Controls upper roll-engagement speed threshold.
//            active.maxFallSpeedToEngageRoll = targetMaxRoll;

//            active.airDragMin = airDragMin;
//            active.airDragMax = airDragMax;
//            active.airDragMinSpeed = airDragMinSpeed;
//            active.airDragMaxSpeed = airDragMaxSpeed;
//            active.airFrictionMin = airFrictionMin;
//            active.airFrictionMax = airFrictionMax;
//            active.airFrictionMinSpeed = airFrictionMinSpeed;
//            active.airFrictionMaxSpeed = airFrictionMaxSpeed;
//            movementManager.update(playerRef.getPacketHandler());
        }

        MOON_PROFILE_APPLIED.add(playerId);
    }

    private static void clampVerticalFallVelocity(@Nonnull Velocity velocity, boolean invertedGravity) {
        double currentY = velocity.getY();
        double cappedY = currentY;

        if (invertedGravity) {
            if (currentY > MAX_FALL_SPEED) {
                cappedY = MAX_FALL_SPEED;
            }
        } else if (currentY < -MAX_FALL_SPEED) {
            cappedY = -MAX_FALL_SPEED;
        }

        if (cappedY != currentY) {
            velocity.setY(cappedY);
            velocity.addInstruction(new Vector3d(velocity.getX(), cappedY, velocity.getZ()), null, ChangeVelocityType.Set);
        }
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
