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
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
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
 * Validates player movement against real server-side blocks, preventing players
 * from walking through walls even when blocks are made transparent client-side.
 * 
 * This system runs BEFORE PlayerSystems.ProcessPlayerInput to intercept movement
 * updates and validate them against the actual server-side block state (not the
 * transparent client-side representation).
 */
public class PlayerCollisionValidationSystem extends EntityTickingSystem<EntityStore> {
    
    private static final Logger LOGGER = Logger.getLogger("PlayerCollisionValidation");
    
    // Lazy-initialized query to avoid null component types at class load time
    private Query<EntityStore> query;
    
    // Run BEFORE PlayerSystems.ProcessPlayerInput to intercept movement
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PlayerSystems.ProcessPlayerInput.class)
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
                TransformComponent.getComponentType()
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
        
        if (playerInput == null || transform == null || playerRef == null) {
            return;
        }
        
        UUID playerId = playerRef.getUuid();
        Double floorY = WALK_ON_AIR_FLOORS.get(playerId);
        boolean walkOnAirEnabled = floorY != null;
        boolean correctionInjected = false;

        Vector3d currentPos = playerRef.getTransform().getPosition();
        List<PlayerInput.InputUpdate> queue = playerInput.getMovementUpdateQueue();

        // Gravity/physics may move the player even when there are no movement updates.
        // Inject an absolute movement correction so floor lock still applies.
        if (walkOnAirEnabled && floorY != null && currentPos.y < floorY - FLOOR_EPSILON) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            Teleport teleport = Teleport.createExact(
                new Vector3d(currentPos.x, floorY, currentPos.z),
                transform.getRotation(),
                transform.getRotation()
            ).withoutVelocityReset();
            commandBuffer.addComponent(ref, Teleport.getComponentType(), teleport);

            queue.add(0, new PlayerInput.AbsoluteMovement(currentPos.x, floorY, currentPos.z));
            correctionInjected = true;
            LOGGER.fine("[CollisionValidation] ChecK:" + 0);
            LOGGER.warning("[CollisionValidation] Walk-on-air correction for " + playerRef.getUsername() +
                    " (" + playerId + ") currentY=" + currentPos.y + " floorY=" + floorY);
        }

        if (queue.isEmpty()) {
            maybeLogWalkOnAirDiagnostic(playerRef, playerId, currentPos.y, floorY, 0,
                    Double.NaN, 0, correctionInjected);
            return;
        }

        Vector3d simulatedPos = currentPos;
        double minPreClampTargetY = Double.POSITIVE_INFINITY;
        int clampedToFloorCount = 0;

        // Validate movement updates in queue order
        for (int i = 0; i < queue.size(); i++) {
            PlayerInput.InputUpdate update = queue.get(i);
            boolean wasRelative = update instanceof PlayerInput.RelativeMovement;

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

            if (targetPos != null) {
                minPreClampTargetY = Math.min(minPreClampTargetY, targetPos.y);

                if (walkOnAirEnabled && floorY != null && targetPos.y < floorY) {
                    if (update instanceof PlayerInput.AbsoluteMovement abs) {
                        abs.setY(floorY);
                    } else if (update instanceof PlayerInput.RelativeMovement rel) {
                        rel.setY(floorY - simulatedPos.y);
                    }
                    clampedToFloorCount++;
                    LOGGER.fine("[CollisionValidation] ChecK:" + 1);
                    LOGGER.fine("[CollisionValidation] Walk-on-air clamped movement for " + playerRef.getUsername() +
                            " from targetY=" + targetPos.y + " to floorY=" + floorY);
                    targetPos = new Vector3d(targetPos.x, floorY, targetPos.z);
                }

                simulatedPos = targetPos;
            } else if (!wasRelative) {
                // Non-movement updates don't change simulated position
            }
        }

        if (walkOnAirEnabled && floorY != null && clampedToFloorCount > 0) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            Teleport floorLockTeleport = Teleport.createExact(
                new Vector3d(simulatedPos.x, floorY, simulatedPos.z),
                transform.getRotation(),
                transform.getRotation()
            ).withoutVelocityReset();
            commandBuffer.addComponent(ref, Teleport.getComponentType(), floorLockTeleport);
            LOGGER.warning("[CollisionValidation] Walk-on-air post-clamp correction for " + playerRef.getUsername() +
                    " (" + playerId + ") clampedToFloorCount=" + clampedToFloorCount +
                    " minPreClampTargetY=" + minPreClampTargetY + " floorY=" + floorY);
            correctionInjected = true;
        }

        maybeLogWalkOnAirDiagnostic(playerRef, playerId, currentPos.y, floorY, queue.size(),
                minPreClampTargetY, clampedToFloorCount, correctionInjected);
    }

    private void maybeLogWalkOnAirDiagnostic(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID playerId,
        double currentY,
        @Nullable Double floorY,
        int queueSize,
        double minPreClampTargetY,
        int clampedToFloorCount,
        boolean correctionInjected
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
        LOGGER.fine("[CollisionValidation] ChecK:" + 2);
        LOGGER.info("[CollisionValidation] Walk-on-air state for " + playerRef.getUsername() +
            " (" + playerId + ") currentY=" + currentY +
            " floorY=" + floorY +
            " queueSize=" + queueSize +
            " minPreClampTargetY=" + minPreClampTargetY +
            " clampedToFloorCount=" + clampedToFloorCount +
            " correctionInjected=" + correctionInjected);
    }
}
