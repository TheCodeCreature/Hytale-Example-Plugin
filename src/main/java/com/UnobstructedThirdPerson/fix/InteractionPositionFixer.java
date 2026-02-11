package com.UnobstructedThirdPerson.fix;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import com.hypixel.hytale.server.core.HytaleServer;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class InteractionPositionFixer {
    private static final Logger LOGGER = Logger.getLogger("InteractionFix");

    // ===== CONFIGURABLE PACKET FILTERS =====
    // Set to true to log packets of each type
    private static final boolean LOG_MOUSE_INTERACTION = true;
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
    private static final boolean ENABLE_POSITION_FIX = true;  // Enable the actual fix
    private static final int RAYCAST_DISTANCE = 30;           // Max distance for server raycast
    private static final long CACHE_UPDATE_INTERVAL_MS = 50;  // How often to update cached target (ms)

    // ===== END CONFIGURABLE FILTERS =====

    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, BlockPosition> lastClientPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPosition> lastServerPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3i> cachedServerTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();
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
        
        // Start scheduled task to update cached target on world thread
        startCacheUpdateTask(playerId, playerRef);
        
        LOGGER.info("[InteractionFix] Enabled for player: " + playerId);
    }

    public static void disableForPlayer(@Nonnull UUID playerId) {
        ENABLED_PLAYERS.remove(playerId);
        lastClientPositions.remove(playerId);
        lastServerPositions.remove(playerId);
        cachedServerTargets.remove(playerId);
        
        // Cancel scheduled task
        ScheduledFuture<?> task = activeTasks.remove(playerId);
        if (task != null) {
            task.cancel(false);
        }
        
        LOGGER.info("[InteractionFix] Disabled for player: " + playerId);
    }
    
    private static void startCacheUpdateTask(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            LOGGER.warning("[InteractionFix] Cannot start cache task - invalid player reference");
            return;
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        
        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            // Execute on world thread
            world.execute(() -> updateCachedTarget(playerId, playerRef));
        }, 0L, CACHE_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        activeTasks.put(playerId, task);
        LOGGER.info("[InteractionFix] Started cache update task for player: " + playerId);
    }
    
    private static void updateCachedTarget(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef) {
        // Check if still enabled
        if (!ENABLED_PLAYERS.contains(playerId)) {
            return;
        }
        
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        
        Store<EntityStore> store = ref.getStore();
        
        try {
            Vector3i target = TargetUtil.getTargetBlock(ref, RAYCAST_DISTANCE, store);
            if (target != null) {
                cachedServerTargets.put(playerId, target);
            }
        } catch (Exception e) {
            // Silently ignore - player may have disconnected
        }
    }

    public static boolean isEnabledForPlayer(@Nonnull UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
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

        // Handle MouseInteraction specifically
        if (packet instanceof MouseInteraction mi) {
            handleMouseInteraction(handler, mi);
        }

        // Handle SyncInteractionChains - this is where block positions for placement/breaking are sent
        if (packet instanceof SyncInteractionChains sic) {
            handleSyncInteractionChains(handler, sic);
        }

        // Handle ClientPlaceBlock - BLOCK this packet for enabled players
        // The SyncInteractionChains path will handle placement instead
        if (packet instanceof ClientPlaceBlock cpb) {
            return shouldBlockClientPlaceBlock(handler, cpb);
        }
        
        return false; // Let other packets continue
    }

    private static boolean shouldBlockClientPlaceBlock(PacketHandler handler, ClientPlaceBlock cpb) {
        if (!(handler instanceof GamePacketHandler gph)) {
            return false; // Not a game packet handler, let it through
        }

        PlayerRef playerRef = gph.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        // Only block for enabled players
        if (!ENABLED_PLAYERS.contains(playerId)) {
            return false; // Not enabled, let packet through
        }

        // Block this packet - SyncInteractionChains will handle placement
        if (LOG_CLIENT_PLACE_BLOCK) {
            StringBuilder sb = new StringBuilder();
            sb.append("[ClientPlaceBlock] Player: ").append(playerRef.getUsername()).append("\n");
            if (cpb.position != null) {
                sb.append("  Client position: ").append(cpb.position.x).append(",").append(cpb.position.y).append(",").append(cpb.position.z).append("\n");
            }
            sb.append("  Action: BLOCKED (SyncInteractionChains will handle placement)");
            LOGGER.info(sb.toString());
        }

        return true; // Block the packet
    }

    private static void handleSyncInteractionChains(PacketHandler handler, SyncInteractionChains sic) {
        if (!(handler instanceof GamePacketHandler gph)) {
            return;
        }

        PlayerRef playerRef = gph.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        // Only process for enabled players
        if (!ENABLED_PLAYERS.contains(playerId)) {
            return;
        }

        // Get cached server target (updated on world thread)
        Vector3i serverTarget = cachedServerTargets.get(playerId);
        if (serverTarget == null) {
            // No cached target yet
            return;
        }

        // Process each chain
        for (SyncInteractionChain chain : sic.updates) {
            if (chain.interactionData != null) {
                for (InteractionSyncData data : chain.interactionData) {
                    if (data != null && data.blockPosition != null) {
                        BlockPosition clientPos = data.blockPosition;

                        // Log the desync
                        boolean desynced = clientPos.x != serverTarget.x ||
                                          clientPos.y != serverTarget.y ||
                                          clientPos.z != serverTarget.z;

                        if (LOG_SYNC_INTERACTION_CHAINS) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("[SyncInteractionChains] Player: ").append(playerRef.getUsername()).append("\n");
                            sb.append("  Chain ID: ").append(chain.chainId);
                            sb.append(", State: ").append(chain.state);
                            sb.append(", Type: ").append(chain.interactionType).append("\n");
                            sb.append("  Client blockPosition: ").append(clientPos.x).append(",").append(clientPos.y).append(",").append(clientPos.z).append("\n");
                            sb.append("  Server calculated:    ").append(serverTarget.x).append(",").append(serverTarget.y).append(",").append(serverTarget.z).append("\n");
                            sb.append("  Status: ").append(desynced ? "DESYNC DETECTED" : "In sync");
                            if (ENABLE_POSITION_FIX) {
                                sb.append(" -> INVALIDATING (y=-1)");
                            }
                            LOGGER.info(sb.toString());
                        }

                        // Invalidate the block position to prevent client-side place/break
                        // Setting y=-1 causes PlaceBlockInteraction and BreakBlockInteraction to return early
                        if (ENABLE_POSITION_FIX) {
                            data.blockPosition = new BlockPosition(
                                clientPos.x, -1, clientPos.z
                            );
                        }
                    }
                }
            }
        }
    }

    private static void handleOutboundPacket(PacketHandler handler, Packet packet) {
        // Log outbound packets (like SetServerCamera)
        logPacketDiscovery(handler, packet, "Outbound");
    }

    private static void handleMouseInteraction(PacketHandler handler, MouseInteraction mi) {
        if (!(handler instanceof GamePacketHandler gph)) {
            return;
        }

        PlayerRef playerRef = gph.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        // Only process for enabled players
        if (!ENABLED_PLAYERS.contains(playerId)) {
            return;
        }

        // Get cached server target (updated on world thread)
        Vector3i serverTarget = cachedServerTargets.get(playerId);

        // Get client position for logging
        BlockPosition clientPos = null;
        if (mi.worldInteraction != null) {
            clientPos = mi.worldInteraction.blockPosition;
        }

        // Log the interaction details
        logMouseInteractionDetails(playerRef, mi, clientPos, serverTarget);

        // Apply the fix if enabled
        if (ENABLE_POSITION_FIX && serverTarget != null && mi.worldInteraction != null) {
            mi.worldInteraction.blockPosition = new BlockPosition(
                serverTarget.x, serverTarget.y, serverTarget.z
            );
        }
    }

    private static void logPacketDiscovery(PacketHandler handler, Packet packet, String direction) {
        String packetName = packet.getClass().getName();

        // Filter by package
        boolean shouldLog = false;
        if (LOG_ALL_PLAYER_PACKETS && packetName.contains(".packets.player.")) shouldLog = true;
        if (LOG_ALL_CAMERA_PACKETS && packetName.contains(".packets.camera.")) shouldLog = true;
        if (LOG_ALL_INTERACTION_PACKETS && packetName.contains(".packets.interaction.")) shouldLog = true;

        // Filter by specific packet type
        if (LOG_MOUSE_INTERACTION && packet instanceof MouseInteraction) shouldLog = true;
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

    private static void logMouseInteractionDetails(PlayerRef playerRef, MouseInteraction mi,
                                                    BlockPosition clientPos, Vector3i serverTarget) {
        if (!LOG_MOUSE_INTERACTION) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        BlockPosition lastClient = lastClientPositions.get(playerId);

        // Skip if LOG_ONLY_CHANGES and nothing changed
        if (LOG_ONLY_CHANGES && lastClient != null && clientPos != null) {
            if (lastClient.x == clientPos.x && lastClient.y == clientPos.y && lastClient.z == clientPos.z) {
                return; // No change, skip logging
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[MouseInteraction] Player: ").append(playerRef.getUsername()).append("\n");

        // Client data
        sb.append("  Client blockPosition: ");
        if (clientPos != null) {
            sb.append(clientPos.x).append(",").append(clientPos.y).append(",").append(clientPos.z);
        } else {
            sb.append("null");
        }
        sb.append("\n");

        // Server calculated
        sb.append("  Server calculated:    ");
        if (serverTarget != null) {
            sb.append(serverTarget.x).append(",").append(serverTarget.y).append(",").append(serverTarget.z);
        } else {
            sb.append("null");
        }
        sb.append("\n");

        // Desync detection
        if (clientPos != null && serverTarget != null) {
            boolean desynced = clientPos.x != serverTarget.x ||
                              clientPos.y != serverTarget.y ||
                              clientPos.z != serverTarget.z;
            sb.append("  Status: ").append(desynced ? "DESYNC DETECTED" : "In sync");
            if (desynced && ENABLE_POSITION_FIX) {
                sb.append(" -> FIXED");
            }
        }

        LOGGER.info(sb.toString());

        if (LOG_TO_CHAT) {
            playerRef.sendMessage(Message.raw(sb.toString()));
        }

        // Update last known positions
        if (clientPos != null) {
            lastClientPositions.put(playerId, clientPos);
        }
        if (serverTarget != null) {
            lastServerPositions.put(playerId, new BlockPosition(
                serverTarget.x, serverTarget.y, serverTarget.z));
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
        // Cancel all scheduled tasks
        for (ScheduledFuture<?> task : activeTasks.values()) {
            task.cancel(false);
        }
        activeTasks.clear();
        
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
        LOGGER.info("[InteractionFix] Shutdown complete");
    }
}
