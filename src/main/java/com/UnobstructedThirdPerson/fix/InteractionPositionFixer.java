package com.UnobstructedThirdPerson.fix;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientPlaceBlock;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.server.core.modules.collision.CollisionMath;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class InteractionPositionFixer {
    private static final Logger LOGGER = Logger.getLogger("InteractionFix");

    // ===== CONFIGURABLE PACKET FILTERS =====
    // Set to true to log packets of each type
    private static final boolean LOG_CLIENT_MOVEMENT = false;  // Reserved for future use
    private static final boolean LOG_SET_SERVER_CAMERA = true;
    private static final boolean LOG_SYNC_INTERACTION_CHAINS = true;  // Log interaction chains with block positions
    private static final boolean LOG_CLIENT_PLACE_BLOCK = true;
    private static final boolean LOG_ALL_PLAYER_PACKETS = false;  // Verbose - logs all packets in player package
    private static final boolean LOG_ALL_CAMERA_PACKETS = false;  // Logs all packets in camera package
    private static final boolean LOG_ALL_INTERACTION_PACKETS = false;  // Logs all packets in interaction package

    // Logging detail levels
    private static final boolean LOG_PACKET_CONTENTS = true;  // Log full packet data
    private static final boolean LOG_ONLY_CHANGES = false;    // Log ALL packets, not just changes
    private static final boolean LOG_TO_CHAT = false;         // Also send logs to player chat

    // Fix behavior
    private static final int RAYCAST_DISTANCE = 30;           // Max distance for server raycast
    private static final double FIRST_PERSON_PITCH_DOWN = -55.0;  // Degrees; switch to first person when looking below this
    private static final double FIRST_PERSON_PITCH_UP   =  55.0;  // Degrees; switch to first person when looking above this

    // ===== END CONFIGURABLE FILTERS =====

    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, BlockPosition> lastClientPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPosition> lastServerPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3i> cachedServerTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3i> cachedPlacementPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3d> cachedHitLocations = new ConcurrentHashMap<>();
    private static final Map<UUID, String> cachedHitFaces = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockFace> cachedBlockFaces = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> cachedEntityTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerCameraSettings> activeCameraSettings = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> currentFirstPersonState = new ConcurrentHashMap<>();
    private static PacketFilter registeredInboundFilter;
    private static PacketFilter registeredOutboundFilter;

    public static void enableForPlayer(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        
        // Already enabled
        if (ENABLED_PLAYERS.contains(playerId)) {
            return;
        }
        
        ENABLED_PLAYERS.add(playerId);
        ensureFiltersRegistered();
        
        LOGGER.info("[InteractionFix] Enabled for player: " + playerId);
    }

    /**
     * Store the active camera settings for a player so that server-side raycasting
     * uses the same offset/distance as the client camera.
     */
    public static void setActiveCameraSettings(@Nonnull UUID playerId, @Nullable ServerCameraSettings settings) {
        if (settings != null) {
            activeCameraSettings.put(playerId, settings);
        } else {
            activeCameraSettings.remove(playerId);
        }
    }

    @Nullable
    public static ServerCameraSettings getActiveCameraSettings(@Nonnull UUID playerId) {
        return activeCameraSettings.get(playerId);
    }

    public static void disableForPlayer(@Nonnull UUID playerId) {
        ENABLED_PLAYERS.remove(playerId);
        lastClientPositions.remove(playerId);
        lastServerPositions.remove(playerId);
        cachedServerTargets.remove(playerId);
        cachedPlacementPositions.remove(playerId);
        cachedHitLocations.remove(playerId);
        cachedHitFaces.remove(playerId);
        cachedBlockFaces.remove(playerId);
        cachedEntityTargets.remove(playerId);
        activeCameraSettings.remove(playerId);
        currentFirstPersonState.remove(playerId);
        
        LOGGER.info("[InteractionFix] Disabled for player: " + playerId);
    }
    
    /**
     * Compute and cache the server target + placement position on-demand.
     * Raycasts from the camera position (accounting for positionOffset and distance)
     * to match the client's reticle targeting.
     * Must be called on the world thread.
     */
    public static void computeAndCacheTarget(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        
        try {
            // Get the player's look transform (eye position + head rotation direction)
            Transform lookTransform = TargetUtil.getLook(ref, store);
            Vector3d eyePos = lookTransform.getPosition();
            Vector3d lookDir = lookTransform.getDirection();
            
            // Check pitch for first-person toggle
            // lookDir.y = sin(pitch), so pitch in degrees = asin(lookDir.y) * (180/PI)
            double pitchDegrees = Math.asin(lookDir.y) * (180.0 / Math.PI);
            updateFirstPersonState(playerId, playerRef, pitchDegrees);
            
            // Compute the camera origin using the active camera settings
            Vector3d cameraOrigin = computeCameraOrigin(playerId, eyePos, lookDir);
            // Raycast from camera origin along look direction for block targeting
            Vector3i target = TargetUtil.getTargetBlock(
                world, (blockId, fluidId) -> blockId != 0,
                cameraOrigin.x, cameraOrigin.y, cameraOrigin.z,
                lookDir.x, lookDir.y, lookDir.z,
                RAYCAST_DISTANCE
            );
            if (target != null) {
                cachedServerTargets.put(playerId, target);
                
                Vector3i placementPos = calculatePlacementPosition(
                    cameraOrigin, lookDir, target, world, store, playerId);
                if (placementPos != null) {
                    cachedPlacementPositions.put(playerId, placementPos);
                }
            }
            
            // Entity targeting from camera origin along look direction
            Ref<EntityStore> targetEntity = getTargetEntityFromCamera(
                ref, cameraOrigin, lookDir, (float) RAYCAST_DISTANCE, store);
            if (targetEntity != null && targetEntity.isValid()) {
                NetworkId networkId = store.getComponent(targetEntity, NetworkId.getComponentType());
                if (networkId != null) {
                    cachedEntityTargets.put(playerId, networkId.getId());
                } else {
                    cachedEntityTargets.remove(playerId);
                }
            } else {
                cachedEntityTargets.remove(playerId);
            }
        } catch (Exception e) {
            LOGGER.warning("[InteractionFix] Error computing target: " + e.getMessage());
        }
    }
    
    /**
     * Toggle isFirstPerson on the active camera settings when pitch crosses the threshold.
     * Below -45° pitch → first person; above → third person.
     * Only sends a packet when the state actually changes.
     */
    private static void updateFirstPersonState(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef, double pitchDegrees) {
        ServerCameraSettings settings = activeCameraSettings.get(playerId);
        if (settings == null) return;

        boolean shouldBeFirstPerson = pitchDegrees <= FIRST_PERSON_PITCH_DOWN || pitchDegrees >= FIRST_PERSON_PITCH_UP;
        Boolean wasFirstPerson = currentFirstPersonState.get(playerId);

        // Only send a packet when state changes (or on first check)
        if (wasFirstPerson == null || wasFirstPerson != shouldBeFirstPerson) {
            settings.isFirstPerson = shouldBeFirstPerson;
            currentFirstPersonState.put(playerId, shouldBeFirstPerson);

            playerRef.getPacketHandler().writeNoCache(
                new SetServerCamera(ClientCameraView.Custom, false, settings));

            LOGGER.info("[InteractionFix] Camera switched to " +
                (shouldBeFirstPerson ? "FIRST PERSON" : "THIRD PERSON") +
                " (pitch=" + String.format("%.1f", pitchDegrees) + "°)");
        }
    }

    /**
     * Compute the camera origin position based on the active camera settings.
     * This mirrors what the client does: start at the eye position, apply the
     * positionOffset in the camera's local coordinate frame (accounting for pitch),
     * then move backward along the look direction by the camera distance.
     */
    @Nonnull
    private static Vector3d computeCameraOrigin(
        @Nonnull UUID playerId, @Nonnull Vector3d eyePos, @Nonnull Vector3d lookDir
    ) {
        ServerCameraSettings settings = activeCameraSettings.get(playerId);
        if (settings == null) {
            // No custom camera for this player — fall back to eye position
            return eyePos;
        }
        
        // Start from eye position (eyeOffset is already accounted for in getLook)
        double ox = eyePos.x;
        double oy = eyePos.y;
        double oz = eyePos.z;
        
        // Apply positionOffset in the camera's local coordinate frame
        // so that the offset rotates with the camera's pitch and yaw.
        // (0, 1, 0) means "1 unit up from the camera's perspective", not world Y.
        Position posOffset = settings.positionOffset;
        if (posOffset != null && (posOffset.x != 0 || posOffset.y != 0 || posOffset.z != 0)) {
            // Compute camera local axes from look direction
            // right = normalize(worldUp × lookDir)
            // up    = lookDir × right
            double worldUpX = 0, worldUpY = 1, worldUpZ = 0;
            
            // right = worldUp × lookDir
            double rx = worldUpY * lookDir.z - worldUpZ * lookDir.y; //  lookDir.z
            double ry = worldUpZ * lookDir.x - worldUpX * lookDir.z; //  0
            double rz = worldUpX * lookDir.y - worldUpY * lookDir.x; // -lookDir.x
            double rLen = Math.sqrt(rx * rx + ry * ry + rz * rz);
            
            if (rLen < 0.001) {
                // Degenerate case: looking straight up or down, cross product is zero.
                // Fall back to world-space offset since the local frame is undefined.
                ox += posOffset.x;
                oy += posOffset.y;
                oz += posOffset.z;
            } else {
                // Normalize right vector
                rx /= rLen; ry /= rLen; rz /= rLen;
                
                // up = lookDir × right
                double ux = lookDir.y * rz - lookDir.z * ry;
                double uy = lookDir.z * rx - lookDir.x * rz;
                double uz = lookDir.x * ry - lookDir.y * rx;
                // up is already unit length (cross of two unit perpendicular vectors)
                
                // Transform offset from camera-local to world space:
                //   world = right * offset.x + up * offset.y + lookDir * offset.z
                ox += rx * posOffset.x + ux * posOffset.y + lookDir.x * posOffset.z;
                oy += ry * posOffset.x + uy * posOffset.y + lookDir.y * posOffset.z;
                oz += rz * posOffset.x + uz * posOffset.y + lookDir.z * posOffset.z;
            }
        }
        
        // NOTE: The camera distance moves the camera behind the player for rendering,
        // but the reticle ray should originate from the player's anchor point (eye + offset),
        // not from behind the player. Distance is NOT applied here.
        
        return new Vector3d(ox, oy, oz);
    }
    
    /**
     * Find the closest entity hit by a ray from the camera origin.
     * Mirrors TargetUtil.getTargetEntity but using a custom ray origin/direction.
     */
    @Nullable
    private static Ref<EntityStore> getTargetEntityFromCamera(
        @Nonnull Ref<EntityStore> selfRef,
        @Nonnull Vector3d cameraOrigin, @Nonnull Vector3d lookDir,
        float radius, @Nonnull Store<EntityStore> store
    ) {
        java.util.List<Ref<EntityStore>> candidates = TargetUtil.getAllEntitiesInSphere(cameraOrigin, radius, store);
        
        // Remove self and entities not hit by the camera ray
        candidates.removeIf(candidate -> {
            if (candidate == null || !candidate.isValid() || candidate.equals(selfRef)) return true;
            
            BoundingBox bb = store.getComponent(candidate, BoundingBox.getComponentType());
            TransformComponent tc = store.getComponent(candidate, TransformComponent.getComponentType());
            if (bb == null || tc == null) return true;
            
            Box box = bb.getBoundingBox();
            Vector3d pos = tc.getPosition();
            Vector2d minMax = new Vector2d();
            return !CollisionMath.intersectRayAABB(cameraOrigin, lookDir, pos.getX(), pos.getY(), pos.getZ(), box, minMax);
        });
        
        if (candidates.isEmpty()) return null;
        
        // Return closest entity along the ray (smallest parametric t from intersectRayAABB)
        Ref<EntityStore> closest = null;
        double minT = Double.MAX_VALUE;
        for (Ref<EntityStore> candidate : candidates) {
            if (candidate != null && candidate.isValid()) {
                BoundingBox bb = store.getComponent(candidate, BoundingBox.getComponentType());
                TransformComponent tc = store.getComponent(candidate, TransformComponent.getComponentType());
                if (bb != null && tc != null) {
                    Vector2d tRange = new Vector2d();
                    Vector3d pos = tc.getPosition();
                    CollisionMath.intersectRayAABB(cameraOrigin, lookDir, pos.getX(), pos.getY(), pos.getZ(), bb.getBoundingBox(), tRange);
                    // tRange.x is the near intersection parameter along the ray
                    if (tRange.x < minT) {
                        minT = tRange.x;
                        closest = candidate;
                    }
                }
            }
        }
        return closest;
    }

    public static boolean isEnabledForPlayer(@Nonnull UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    @Nullable
    public static Vector3i getCachedServerTarget(@Nonnull UUID playerId) {
        return cachedServerTargets.get(playerId);
    }
    
    @Nullable
    public static Vector3i getCachedPlacementPosition(@Nonnull UUID playerId) {
        return cachedPlacementPositions.get(playerId);
    }
    
    /**
     * Pure result of placement face detection and position calculation.
     * Package-private for testability.
     */
    static final class PlacementResult {
        final int x, y, z;
        final String face;
        final String blockFaceName;

        PlacementResult(int x, int y, int z, String face, String blockFaceName) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
            this.blockFaceName = blockFaceName;
        }
    }

    /**
     * Pure placement calculation: given a target block and hit location, determine
     * which face was hit and compute the adjacent placement position.
     * No engine dependencies — testable with primitives only.
     * Package-private for testability.
     */
    static PlacementResult calculatePlacementFromHit(
            int targetX, int targetY, int targetZ,
            double hitX, double hitY, double hitZ) {

        double dx = hitX - targetX;
        double dy = hitY - targetY;
        double dz = hitZ - targetZ;

        int offsetX = 0, offsetY = 0, offsetZ = 0;
        String hitFace = "unknown";
        String blockFaceName = "None";

        // Check X faces (West = 0.0, East = 1.0)
        if (Math.abs(dx) < 0.001) {
            offsetX = -1; hitFace = "WEST (-X)"; blockFaceName = "West";
        } else if (Math.abs(dx - 1.0) < 0.001) {
            offsetX = 1; hitFace = "EAST (+X)"; blockFaceName = "East";
        }
        // Check Y faces (Bottom = 0.0, Top = 1.0)
        else if (Math.abs(dy) < 0.001) {
            offsetY = -1; hitFace = "BOTTOM (-Y)"; blockFaceName = "Down";
        } else if (Math.abs(dy - 1.0) < 0.001) {
            offsetY = 1; hitFace = "TOP (+Y)"; blockFaceName = "Up";
        }
        // Check Z faces (North = 0.0, South = 1.0)
        else if (Math.abs(dz) < 0.001) {
            offsetZ = -1; hitFace = "NORTH (-Z)"; blockFaceName = "North";
        } else if (Math.abs(dz - 1.0) < 0.001) {
            offsetZ = 1; hitFace = "SOUTH (+Z)"; blockFaceName = "South";
        }
        // Fallback: use closest face based on which delta is closest to a boundary
        else {
            double distWest = Math.abs(dx);
            double distEast = Math.abs(dx - 1.0);
            double distBottom = Math.abs(dy);
            double distTop = Math.abs(dy - 1.0);
            double distNorth = Math.abs(dz);
            double distSouth = Math.abs(dz - 1.0);

            double minDist = Math.min(distWest, Math.min(distEast, Math.min(distBottom,
                             Math.min(distTop, Math.min(distNorth, distSouth)))));

            if (minDist == distWest) { offsetX = -1; hitFace = "WEST (-X) [fallback]"; blockFaceName = "West"; }
            else if (minDist == distEast) { offsetX = 1; hitFace = "EAST (+X) [fallback]"; blockFaceName = "East"; }
            else if (minDist == distBottom) { offsetY = -1; hitFace = "BOTTOM (-Y) [fallback]"; blockFaceName = "Down"; }
            else if (minDist == distTop) { offsetY = 1; hitFace = "TOP (+Y) [fallback]"; blockFaceName = "Up"; }
            else if (minDist == distNorth) { offsetZ = -1; hitFace = "NORTH (-Z) [fallback]"; blockFaceName = "North"; }
            else { offsetZ = 1; hitFace = "SOUTH (+Z) [fallback]"; blockFaceName = "South"; }
        }

        return new PlacementResult(
            targetX + offsetX, targetY + offsetY, targetZ + offsetZ,
            hitFace, blockFaceName
        );
    }

    /**
     * Calculate the placement position (adjacent air block) based on which face of the target block
     * the ray hits. Uses the camera origin to get the precise hit location on the block surface.
     */
    @Nullable
    private static Vector3i calculatePlacementPosition(Vector3d cameraOrigin, Vector3d lookDir,
        Vector3i targetBlock, World world, Store<EntityStore> store, UUID playerId) {
        // Get the precise hit location on the block surface from camera origin
        Vector3d hitLocation = TargetUtil.getTargetLocation(
            world, blockId -> blockId != 0,
            cameraOrigin.x, cameraOrigin.y, cameraOrigin.z,
            lookDir.x, lookDir.y, lookDir.z,
            RAYCAST_DISTANCE
        );
        if (hitLocation == null) {
            LOGGER.info("[PlacementCalc] No hit location from TargetUtil");
            return null;
        }
        
        // Cache hit location for debug display
        cachedHitLocations.put(playerId, hitLocation);
        
        LOGGER.info("[PlacementCalc] Target: " + targetBlock.x + "," + targetBlock.y + "," + targetBlock.z +
            " HitLoc: " + String.format("%.3f,%.3f,%.3f", hitLocation.x, hitLocation.y, hitLocation.z));
        
        // Delegate to the pure, testable calculation method
        PlacementResult result = calculatePlacementFromHit(
            targetBlock.x, targetBlock.y, targetBlock.z,
            hitLocation.x, hitLocation.y, hitLocation.z
        );
        
        cachedHitFaces.put(playerId, result.face);
        // Map blockFaceName back to BlockFace enum
        BlockFace blockFace = switch (result.blockFaceName) {
            case "West" -> BlockFace.West;
            case "East" -> BlockFace.East;
            case "Down" -> BlockFace.Down;
            case "Up" -> BlockFace.Up;
            case "North" -> BlockFace.North;
            case "South" -> BlockFace.South;
            default -> BlockFace.None;
        };
        cachedBlockFaces.put(playerId, blockFace);
        Vector3i placementPos = new Vector3i(result.x, result.y, result.z);
        
        LOGGER.info("[PlacementCalc] Hit face: " + result.face + " -> Placement: " + 
            placementPos.x + "," + placementPos.y + "," + placementPos.z);
        
        return placementPos;
    }
    
    @Nullable
    public static Vector3d getCachedHitLocation(@Nonnull UUID playerId) {
        return cachedHitLocations.get(playerId);
    }
    
    @Nullable
    public static String getCachedHitFace(@Nonnull UUID playerId) {
        return cachedHitFaces.get(playerId);
    }

    @Nullable
    public static BlockPosition getLastClientPosition(@Nonnull UUID playerId) {
        return lastClientPositions.get(playerId);
    }

    private static void ensureFiltersRegistered() {
        if (registeredInboundFilter == null) {
            PacketFilter inboundFilter = (PacketHandler handler, Packet packet) -> {
                return handleInboundPacket(handler, packet); // Returns true to block packet
            };
            PacketAdapters.registerInbound(inboundFilter);
            registeredInboundFilter = inboundFilter;
            LOGGER.info("[InteractionFix] Registered inbound packet filter");
        }

        if (registeredOutboundFilter == null) {
            PacketFilter outboundFilter = (PacketHandler handler, Packet packet) -> {
                handleOutboundPacket(handler, packet);
                return false; // Let packet continue
            };
            PacketAdapters.registerOutbound(outboundFilter);
            registeredOutboundFilter = outboundFilter;
            LOGGER.info("[InteractionFix] Registered outbound packet filter");
        }
    }

    private static boolean handleInboundPacket(PacketHandler handler, Packet packet) {
        // Log packet discovery if enabled
        logPacketDiscovery(handler, packet, "Inbound");

        // Handle SyncInteractionChains - rewrite block positions to server-computed targets
        if (packet instanceof SyncInteractionChains sic) {
            return handleSyncInteractionChains(handler, sic);
        }

        // Handle ClientPlaceBlock - rewrite position to server-computed placement target
        if (packet instanceof ClientPlaceBlock cpb) {
            return shouldBlockClientPlaceBlock(handler, cpb);
        }
        
        return false; // Let other packets continue
    }

    private static boolean shouldBlockClientPlaceBlock(PacketHandler handler, ClientPlaceBlock cpb) {
        if (!(handler instanceof GamePacketHandler gph)) {
            return false;
        }

        PlayerRef playerRef = gph.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        if (!ENABLED_PLAYERS.contains(playerId)) {
            return false;
        }

        // Kill the ClientPlaceBlock packet entirely — do NOT re-inject.
        // SyncInteractionChains (PlaceBlockInteraction) already handles placement
        // through the interaction chain system. Re-injecting ClientPlaceBlock would
        // cause duplicate placement via BlockPlaceUtils.placeBlock().
        BlockPosition pos = cpb.position;
        if (LOG_CLIENT_PLACE_BLOCK) {
            LOGGER.info("[ClientPlaceBlock] Killed packet (position=" +
                (pos != null ? pos.x + "," + pos.y + "," + pos.z : "null") +
                ", blockId=" + cpb.placedBlockId + ") — placement handled by SyncInteractionChains");
        }

        // Force-resync the block at the client's predicted position so the client
        // undoes its local prediction (ghost block) at the wrong position.
        if (pos != null) {
            scheduleBlockInvalidation(playerRef, pos.x, pos.y, pos.z);
        }

        return true; // Block the original packet, do not re-inject
    }

    private static boolean handleSyncInteractionChains(PacketHandler handler, SyncInteractionChains sic) {
        if (!(handler instanceof GamePacketHandler gph)) {
            return false;
        }

        PlayerRef playerRef = gph.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        if (!ENABLED_PLAYERS.contains(playerId)) {
            return false;
        }

        // Always kill the original client packet and re-inject a new one
        // with corrected coordinates to avoid duplicate interactions.
        // Deep-clone preserves: block rotations, survival damage, placement validation,
        // inventory management, events, and all other engine behavior.
        SyncInteractionChains corrected = sic.clone();

        // Correct entity targeting in chain data (InteractionChainData)
        Integer cachedEntityId = cachedEntityTargets.get(playerId);
        for (SyncInteractionChain chain : corrected.updates) {
            // Correct chain.data entity and block targeting
            var chainData = chain.data;
            if (chainData != null) {
                if (cachedEntityId != null) {
                    chainData.entityId = cachedEntityId;
                }
                // Correct chain.data.blockPosition with server target
                Vector3i chainBlockTarget = cachedServerTargets.get(playerId);
                if (chainBlockTarget != null) {
                    chainData.blockPosition = new BlockPosition(chainBlockTarget.x, chainBlockTarget.y, chainBlockTarget.z);
                }
            }

            if (chain.interactionData == null) {
                continue;
            }
            for (InteractionSyncData data : chain.interactionData) {
                if (data == null) {
                    continue;
                }

                // Correct entity targeting in InteractionSyncData
                if (cachedEntityId != null && data.entityId >= 0) {
                    data.entityId = cachedEntityId;
                }

                if (data.blockPosition == null) {
                    continue;
                }

                BlockPosition clientPos = data.blockPosition;
                lastClientPositions.put(playerId, clientPos);

                // Choose the correct server position based on interaction type
                Vector3i serverPos = null;
                boolean isPlacement = false;
                if (chain.interactionType == InteractionType.Secondary) {
                    // Secondary (right-click) = place: use the adjacent placement position
                    serverPos = cachedPlacementPositions.get(playerId);
                    isPlacement = true;
                } else {
                    // Primary (break), Use (doors/chests), Pick (middle-click),
                    // and any other type: target the existing block
                    serverPos = cachedServerTargets.get(playerId);
                }

                if (serverPos != null) {
                    if (LOG_SYNC_INTERACTION_CHAINS) {
                        LOGGER.info("[SyncInteractionChains] Blocked original, re-injecting " + chain.interactionType +
                            " blockPosition " + clientPos.x + "," + clientPos.y + "," + clientPos.z +
                            " -> " + serverPos.x + "," + serverPos.y + "," + serverPos.z +
                            " (chain=" + chain.chainId + ", state=" + chain.state +
                            ", entityId=" + (cachedEntityId != null ? cachedEntityId : "none") + ")");
                    }

                    // Force-resync the block at the client's ORIGINAL (wrong) position
                    // to undo client-side prediction ghosts before correcting to server position
                    if (clientPos.x != serverPos.x || clientPos.y != serverPos.y || clientPos.z != serverPos.z) {
                        scheduleBlockInvalidation(playerRef, clientPos.x, clientPos.y, clientPos.z);
                    }

                    data.blockPosition = new BlockPosition(serverPos.x, serverPos.y, serverPos.z);

                    // For placement, also correct the block face so the engine
                    // computes the right placement normal for connected blocks
                    if (isPlacement) {
                        BlockFace correctedFace = cachedBlockFaces.get(playerId);
                        if (correctedFace != null) {
                            data.blockFace = correctedFace;
                        }
                    }
                } else if (LOG_SYNC_INTERACTION_CHAINS) {
                    LOGGER.fine("[SyncInteractionChains] No cached server position for " +
                        chain.interactionType + " - keeping client position in clone");
                }
            }
        }

        gph.handle(corrected);
        return true; // Block the original packet
    }

    private static void handleOutboundPacket(PacketHandler handler, Packet packet) {
        // Log outbound packets (like SetServerCamera)
        logPacketDiscovery(handler, packet, "Outbound");
    }

    private static void logPacketDiscovery(PacketHandler handler, Packet packet, String direction) {
        String packetName = packet.getClass().getName();

        // Filter by package
        boolean shouldLog = false;
        if (LOG_ALL_PLAYER_PACKETS && packetName.contains(".packets.player.")) shouldLog = true;
        if (LOG_ALL_CAMERA_PACKETS && packetName.contains(".packets.camera.")) shouldLog = true;
        if (LOG_ALL_INTERACTION_PACKETS && packetName.contains(".packets.interaction.")) shouldLog = true;

        // Filter by specific packet type
        if (LOG_SET_SERVER_CAMERA && packet instanceof SetServerCamera) shouldLog = true;
        if (LOG_SYNC_INTERACTION_CHAINS && packet instanceof SyncInteractionChains) shouldLog = true;
        if (LOG_CLIENT_PLACE_BLOCK && packet instanceof ClientPlaceBlock) shouldLog = true;

        // Only log for enabled players if it's a game packet handler
        if (handler instanceof GamePacketHandler gph) {
            PlayerRef playerRef = gph.getPlayerRef();
            if (!ENABLED_PLAYERS.contains(playerRef.getUuid())) {
                return;
            }
        }

        if (shouldLog) {
            logPacket(handler, packet, direction);
        }
    }

    private static void logPacket(PacketHandler handler, Packet packet, String direction) {
        StringBuilder sb = new StringBuilder();
        sb.append("[PacketLog] ").append(direction).append(" ");
        sb.append(packet.getClass().getSimpleName());
        sb.append(" (id=").append(packet.getId()).append(")");

        if (LOG_PACKET_CONTENTS) {
            sb.append("\n").append(formatPacketContents(packet));
        }

        LOGGER.info(sb.toString());

        if (LOG_TO_CHAT && handler instanceof GamePacketHandler gph) {
            PlayerRef playerRef = gph.getPlayerRef();
            playerRef.sendMessage(Message.raw(sb.toString()));
        }
    }

    private static String formatPacketContents(Packet packet) {
        if (packet instanceof MouseInteraction mi) {
            return formatMouseInteraction(mi);
        } else if (packet instanceof ClientPlaceBlock cpb) {
            return "  position: " + cpb.position + ", rotation: " + cpb.rotation + ", placedBlockId: " + cpb.placedBlockId;
        } else if (packet instanceof SyncInteractionChains sic) {
            return "  chainCount: " + (sic.updates != null ? sic.updates.length : 0);
        } else if (packet instanceof SetServerCamera ssc) {
            return "  view: " + ssc.clientCameraView + ", locked: " + ssc.isLocked +
                   ", hasSettings: " + (ssc.cameraSettings != null);
        }
        return "  (no detailed format available)";
    }

    private static String formatMouseInteraction(MouseInteraction mi) {
        StringBuilder sb = new StringBuilder();
        sb.append("  timestamp: ").append(mi.clientTimestamp).append("\n");
        sb.append("  activeSlot: ").append(mi.activeSlot).append("\n");
        sb.append("  screenPoint: ").append(mi.screenPoint).append("\n");
        sb.append("  mouseButton: ").append(mi.mouseButton).append("\n");
        sb.append("  mouseMotion: ").append(mi.mouseMotion != null ? "present" : "null").append("\n");
        if (mi.worldInteraction != null) {
            sb.append("  worldInteraction:\n");
            sb.append("    entityId: ").append(mi.worldInteraction.entityId).append("\n");
            sb.append("    blockPosition: ").append(mi.worldInteraction.blockPosition).append("\n");
            sb.append("    blockRotation: ").append(mi.worldInteraction.blockRotation);
        } else {
            sb.append("  worldInteraction: null");
        }
        return sb.toString();
    }

    /**
     * Force-resync a block at the given position to the client by marking it dirty
     * in the chunk's changed-positions set. This undoes any client-side prediction
     * ghost that the server disagrees with.
     * Must be called on the world thread (use world.execute() if needed).
     */
    private static void invalidateBlockAt(Ref<EntityStore> ref, Store<EntityStore> store, int x, int y, int z) {
        try {
            World world = store.getExternalData().getWorld();
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            Ref<ChunkStore> chunkRef = chunkStore.getExternalData().getChunkReference(chunkIndex);
            if (chunkRef != null && chunkRef.isValid()) {
                BlockChunk blockChunk = chunkStore.getComponent(chunkRef, BlockChunk.getComponentType());
                if (blockChunk != null) {
                    BlockSection section = blockChunk.getSectionAtBlockY(y);
                    if (section != null) {
                        section.invalidateBlock(x, y, z);
                        LOGGER.info("[InteractionFix] Invalidated block at " + x + "," + y + "," + z +
                            " to resync client prediction");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[InteractionFix] Failed to invalidate block at " +
                x + "," + y + "," + z + ": " + e.getMessage());
        }
    }

    /**
     * Schedule a block invalidation on the world thread for the given position.
     * This is safe to call from the Netty IO thread (packet filter context).
     */
    private static void scheduleBlockInvalidation(PlayerRef playerRef, int x, int y, int z) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> invalidateBlockAt(ref, store, x, y, z));
    }

    public static void shutdown() {
        if (registeredInboundFilter != null) {
            try {
                PacketAdapters.deregisterInbound(registeredInboundFilter);
            } catch (Exception e) {
                LOGGER.warning("[InteractionFix] Failed to deregister inbound filter: " + e.getMessage());
            }
            registeredInboundFilter = null;
        }
        if (registeredOutboundFilter != null) {
            try {
                PacketAdapters.deregisterOutbound(registeredOutboundFilter);
            } catch (Exception e) {
                LOGGER.warning("[InteractionFix] Failed to deregister outbound filter: " + e.getMessage());
            }
            registeredOutboundFilter = null;
        }
        ENABLED_PLAYERS.clear();
        lastClientPositions.clear();
        lastServerPositions.clear();
        cachedServerTargets.clear();
        cachedPlacementPositions.clear();
        cachedHitLocations.clear();
        cachedHitFaces.clear();
        cachedBlockFaces.clear();
        cachedEntityTargets.clear();
        LOGGER.info("[InteractionFix] Shutdown complete");
    }
}
