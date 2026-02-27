package com.UnobstructedThirdPerson.movement;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Non-teleport walk-on-air floor controller.
 *
 * - Clamps incoming movement updates so target Y never drops below floorY
 * - Directly clamps transform Y to floorY if physics moved player below floor
 * - Cancels downward velocity at/under floor so gravity cannot keep pulling down
 */
public class PlayerCollisionValidationSystem extends EntityTickingSystem<EntityStore> {
    
    private static final Logger LOGGER = Logger.getLogger("PlayerCollisionValidation");
    
    // Lazy-initialized query to avoid null component types at class load time
    private Query<EntityStore> query;
    
    // Run BEFORE PlayerSystems.ProcessPlayerInput to intercept movement
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PlayerSystems.ProcessPlayerInput.class),
        new SystemDependency<>(Order.BEFORE, PlayerProcessMovementSystem.class)
    );
    
    // Player hitbox dimensions (approximate)
    private static final double FLOOR_EPSILON = 0.01;
    private static final long DIAGNOSTIC_LOG_INTERVAL_MS = 1000;

    // Per-player walk-on-air floor lock height (Y coordinate)
    private static final Map<UUID, Double> WALK_ON_AIR_FLOORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DIAGNOSTIC_LOG_MS = new ConcurrentHashMap<>();

    public static void enableWalkOnAir(@Nonnull UUID playerId, double floorY) {
        WALK_ON_AIR_FLOORS.put(playerId, floorY);
    }

    public static void disableWalkOnAir(@Nonnull UUID playerId) {
        WALK_ON_AIR_FLOORS.remove(playerId);
        LAST_DIAGNOSTIC_LOG_MS.remove(playerId);
    }

    public static boolean isWalkOnAirEnabled(@Nonnull UUID playerId) {
        return WALK_ON_AIR_FLOORS.containsKey(playerId);
    }

    @Nullable
    public static Double getWalkOnAirFloor(@Nonnull UUID playerId) {
        return WALK_ON_AIR_FLOORS.get(playerId);
    }
    
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Query.and(
                PlayerInput.getComponentType(),
                PlayerRef.getComponentType(),
                TransformComponent.getComponentType(),
                MovementStatesComponent.getComponentType(),
                Velocity.getComponentType()
            );
        }
        return query;
    }
    
    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
    
    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerInput playerInput = archetypeChunk.getComponent(index, PlayerInput.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        MovementStatesComponent movementStatesComponent = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType());
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        
        if (playerInput == null || transform == null || playerRef == null || movementStatesComponent == null || velocity == null) {
            return;
        }
        
        UUID playerId = playerRef.getUuid();
        Double floorY = WALK_ON_AIR_FLOORS.get(playerId);
        boolean walkOnAirEnabled = floorY != null;
        if (!walkOnAirEnabled || floorY == null) {
            return;
        }

        boolean positionCorrected = false;
        boolean velocitySuppressed = false;

        Vector3d currentPos = transform.getPosition();
        Vector3d playerRefPos = playerRef.getTransform().getPosition();
        List<PlayerInput.InputUpdate> queue = playerInput.getMovementUpdateQueue();

        // Disable gravity path while walk-on-air is enabled.
        movementStatesComponent.getMovementStates().flying = true;

        Vector3d simulatedPos = new Vector3d(currentPos.x, currentPos.y, currentPos.z);
        double minPreClampTargetY = Double.POSITIVE_INFINITY;
        int clampedToFloorCount = 0;

        for (int i = 0; i < queue.size(); i++) {
            PlayerInput.InputUpdate update = queue.get(i);

            Vector3d targetPos = null;
            if (update instanceof PlayerInput.AbsoluteMovement abs) {
                targetPos = new Vector3d(abs.getX(), abs.getY(), abs.getZ());
            } else if (update instanceof PlayerInput.RelativeMovement rel) {
                targetPos = new Vector3d(
                    simulatedPos.x + rel.getX(),
                    simulatedPos.y + rel.getY(),
                    simulatedPos.z + rel.getZ()
                );
            }

            if (targetPos == null) {
                continue;
            }

            minPreClampTargetY = Math.min(minPreClampTargetY, targetPos.y);

            if (targetPos.y < floorY) {
                if (update instanceof PlayerInput.AbsoluteMovement abs) {
                    abs.setY(floorY);
                } else if (update instanceof PlayerInput.RelativeMovement rel) {
                    rel.setY(floorY - simulatedPos.y);
                }
                clampedToFloorCount++;
                targetPos = new Vector3d(targetPos.x, floorY, targetPos.z);
            }

            simulatedPos = targetPos;
        }

        // Direct transform correction without teleportation.
        if (currentPos.y < floorY - FLOOR_EPSILON) {
            currentPos.setY(floorY);
            positionCorrected = true;
        }

        // Disable gravity by forcing vertical velocity to zero each tick.
        if (velocity.getY() != 0.0 || velocity.getClientVelocity().getY() != 0.0) {
            velocity.setY(0.0);
            Vector3d clientVel = velocity.getClientVelocity();
            velocity.setClient(clientVel.getX(), 0.0, clientVel.getZ());
            velocitySuppressed = true;
        }

        logWalkOnAirDiagnostic(playerRef, playerId, currentPos.y, playerRefPos.y, floorY, queue.size(),
                minPreClampTargetY, clampedToFloorCount, positionCorrected, velocitySuppressed, velocity.getY());
    }

    private void logWalkOnAirDiagnostic(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID playerId,
        double currentY,
        double playerRefY,
        @Nullable Double floorY,
        int queueSize,
        double minPreClampTargetY,
        int clampedToFloorCount,
        boolean positionCorrected,
        boolean velocitySuppressed,
        double velocityY
    ) {
        if (floorY == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = LAST_DIAGNOSTIC_LOG_MS.get(playerId);
        if (previous != null && (now - previous) < DIAGNOSTIC_LOG_INTERVAL_MS) {
            return;
        }

        LAST_DIAGNOSTIC_LOG_MS.put(playerId, now);
        LOGGER.info("[CollisionValidation] Walk-on-air state for " + playerRef.getUsername() +
            " (" + playerId + ") currentY=" + currentY +
            " playerRefY=" + playerRefY +
            " floorY=" + floorY +
            " queueSize=" + queueSize +
            " minPreClampTargetY=" + minPreClampTargetY +
            " clampedToFloorCount=" + clampedToFloorCount +
            " positionCorrected=" + positionCorrected +
            " velocitySuppressed=" + velocitySuppressed +
            " velocityY=" + velocityY);
    }
}
