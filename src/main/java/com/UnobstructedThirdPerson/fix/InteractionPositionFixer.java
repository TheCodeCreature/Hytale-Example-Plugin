package com.UnobstructedThirdPerson.fix;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientPlaceBlock;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.InteractionType;


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

    // Redirect mode: true = rewrite positions to server target, false = let packets through unmodified
    private static boolean REDIRECT_MODE = true;

    // ===== END CONFIGURABLE FILTERS =====

    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, BlockPosition> lastClientPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPosition> lastServerPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3i> cachedServerTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3i> cachedPlacementPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3d> cachedHitLocations = new ConcurrentHashMap<>();
    private static final Map<UUID, String> cachedHitFaces = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockFace> cachedBlockFaces = new ConcurrentHashMap<>();
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

    public static void disableForPlayer(@Nonnull UUID playerId) {
        ENABLED_PLAYERS.remove(playerId);
        lastClientPositions.remove(playerId);
        lastServerPositions.remove(playerId);
        cachedServerTargets.remove(playerId);
        cachedPlacementPositions.remove(playerId);
        cachedHitLocations.remove(playerId);
        cachedHitFaces.remove(playerId);
        cachedBlockFaces.remove(playerId);
        
        LOGGER.info("[InteractionFix] Disabled for player: " + playerId);
    }
    
    /**
     * Compute and cache the server target + placement position on-demand.
     * Must be called on the world thread.
     */
    public static void computeAndCacheTarget(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        
        Store<EntityStore> store = ref.getStore();
        
        try {
            Vector3i target = TargetUtil.getTargetBlock(ref, RAYCAST_DISTANCE, store);
            if (target != null) {
                cachedServerTargets.put(playerId, target);
                
                World world = store.getExternalData().getWorld();
                Vector3i placementPos = calculatePlacementPosition(ref, target, world, store, playerId);
                if (placementPos != null) {
                    cachedPlacementPositions.put(playerId, placementPos);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[InteractionFix] Error computing target: " + e.getMessage());
        }
    }

    public static boolean isEnabledForPlayer(@Nonnull UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    public static boolean isRedirectMode() {
        return REDIRECT_MODE;
    }

    public static void setRedirectMode(boolean redirectMode) {
        REDIRECT_MODE = redirectMode;
        LOGGER.info("[InteractionFix] Redirect mode set to: " + redirectMode);
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
     * Calculate the placement position (adjacent air block) based on which face of the target block
     * the ray hits. This uses the precise hit location to determine the face.
     */
    @Nullable
    private static Vector3i calculatePlacementPosition(Ref<EntityStore> ref, Vector3i targetBlock, 
                                                        World world, Store<EntityStore> store, UUID playerId) {
        // Get the precise hit location on the block surface
        Vector3d hitLocation = TargetUtil.getTargetLocation(ref, RAYCAST_DISTANCE, store);
        if (hitLocation == null) {
            LOGGER.info("[PlacementCalc] No hit location from TargetUtil");
            return null;
        }
        
        // Cache hit location for debug display
        cachedHitLocations.put(playerId, hitLocation);
        
        // Calculate which face was hit by checking which coordinate is closest to block boundary
        double dx = hitLocation.x - targetBlock.x;
        double dy = hitLocation.y - targetBlock.y;
        double dz = hitLocation.z - targetBlock.z;
        
        LOGGER.info("[PlacementCalc] Target: " + targetBlock.x + "," + targetBlock.y + "," + targetBlock.z +
            " HitLoc: " + String.format("%.3f,%.3f,%.3f", hitLocation.x, hitLocation.y, hitLocation.z) +
            " Delta: " + String.format("%.3f,%.3f,%.3f", dx, dy, dz));
        
        // Determine offset direction based on which face was hit
        int offsetX = 0, offsetY = 0, offsetZ = 0;
        String hitFace = "unknown";
        BlockFace blockFace = BlockFace.None;
        
        // Check X faces (West = 0.0, East = 1.0)
        if (Math.abs(dx) < 0.001) {
            offsetX = -1; hitFace = "WEST (-X)"; blockFace = BlockFace.West;
        } else if (Math.abs(dx - 1.0) < 0.001) {
            offsetX = 1; hitFace = "EAST (+X)"; blockFace = BlockFace.East;
        }
        // Check Y faces (Bottom = 0.0, Top = 1.0)
        else if (Math.abs(dy) < 0.001) {
            offsetY = -1; hitFace = "BOTTOM (-Y)"; blockFace = BlockFace.Down;
        } else if (Math.abs(dy - 1.0) < 0.001) {
            offsetY = 1; hitFace = "TOP (+Y)"; blockFace = BlockFace.Up;
        }
        // Check Z faces (North = 0.0, South = 1.0)
        else if (Math.abs(dz) < 0.001) {
            offsetZ = -1; hitFace = "NORTH (-Z)"; blockFace = BlockFace.North;
        } else if (Math.abs(dz - 1.0) < 0.001) {
            offsetZ = 1; hitFace = "SOUTH (+Z)"; blockFace = BlockFace.South;
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
            
            if (minDist == distWest) { offsetX = -1; hitFace = "WEST (-X) [fallback]"; blockFace = BlockFace.West; }
            else if (minDist == distEast) { offsetX = 1; hitFace = "EAST (+X) [fallback]"; blockFace = BlockFace.East; }
            else if (minDist == distBottom) { offsetY = -1; hitFace = "BOTTOM (-Y) [fallback]"; blockFace = BlockFace.Down; }
            else if (minDist == distTop) { offsetY = 1; hitFace = "TOP (+Y) [fallback]"; blockFace = BlockFace.Up; }
            else if (minDist == distNorth) { offsetZ = -1; hitFace = "NORTH (-Z) [fallback]"; blockFace = BlockFace.North; }
            else { offsetZ = 1; hitFace = "SOUTH (+Z) [fallback]"; blockFace = BlockFace.South; }
        }
        
        cachedHitFaces.put(playerId, hitFace);
        cachedBlockFaces.put(playerId, blockFace);
        Vector3i placementPos = new Vector3i(targetBlock.x + offsetX, targetBlock.y + offsetY, targetBlock.z + offsetZ);
        
        LOGGER.info("[PlacementCalc] Hit face: " + hitFace + " -> Placement: " + 
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

        if (!REDIRECT_MODE) {
            return false;
        }

        // Always kill the original client packet and re-inject a new one
        // with corrected coordinates to avoid duplicate placement
        Vector3i placementPos = cachedPlacementPositions.get(playerId);
        if (placementPos != null && cpb.position != null) {
            ClientPlaceBlock corrected = cpb.clone();
            corrected.position = new BlockPosition(placementPos.x, placementPos.y, placementPos.z);

            if (LOG_CLIENT_PLACE_BLOCK) {
                LOGGER.info("[ClientPlaceBlock] Blocked original, re-injecting with position " +
                    cpb.position.x + "," + cpb.position.y + "," + cpb.position.z +
                    " -> " + placementPos.x + "," + placementPos.y + "," + placementPos.z);
            }

            gph.handle(corrected);
        } else if (LOG_CLIENT_PLACE_BLOCK) {
            LOGGER.fine("[ClientPlaceBlock] No cached placement position, dropping packet");
        }

        return true; // Block the original packet
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

        if (!REDIRECT_MODE) {
            return false; // Let packet through unmodified
        }

        // Always kill the original client packet and re-inject a new one
        // with corrected coordinates to avoid duplicate interactions.
        // Deep-clone preserves: block rotations, survival damage, placement validation,
        // inventory management, events, and all other engine behavior.
        SyncInteractionChains corrected = sic.clone();

        for (SyncInteractionChain chain : corrected.updates) {
            if (chain.interactionData == null) {
                continue;
            }
            for (InteractionSyncData data : chain.interactionData) {
                if (data == null || data.blockPosition == null) {
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
                            " (chain=" + chain.chainId + ", state=" + chain.state + ")");
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
        LOGGER.info("[InteractionFix] Shutdown complete");
    }
}
