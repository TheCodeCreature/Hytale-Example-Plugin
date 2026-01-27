package com.example.exampleplugin.debug;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public final class InteractionPacketLogger {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<String, String> LAST_MESSAGES = new ConcurrentHashMap<>();
    private static final Set<String> IGNORED_PACKET_SIMPLE_NAMES = Set.of(
        "ClientMovement",
        "Ping",
        "Pong"
    );

    public InteractionPacketLogger() {
        PacketAdapters.registerInbound((PacketFilter) (handler, packet) -> {
            logPacket(handler, packet, "Inbound");
            return false;
        });
        PacketAdapters.registerOutbound((PacketFilter) (handler, packet) -> {
            logPacket(handler, packet, "Outbound");
            return false;
        });
        LOGGER.atInfo().log("InteractionPacketLogger registered inbound/outbound packet filters.");
    }

    private static void logPacket(@Nonnull PacketHandler handler, @Nonnull Packet packet, @Nonnull String direction) {
        if (IGNORED_PACKET_SIMPLE_NAMES.contains(packet.getClass().getSimpleName())) {
            return;
        }

        String tag = isGameplayPacket(packet) ? "[Gameplay]" : "[Non-Gameplay]";
        String base = tag + " " + direction + " Packet: " + packet.getClass().getSimpleName() + " (id=" + packet.getId() + ")";
        logIfChanged(handler, base);
        if ("Inbound".equals(direction)) {
            sendChatIfChanged(handler, base);
        }

        if (packet instanceof SyncInteractionChains syncPacket) {
            for (SyncInteractionChain chain : syncPacket.updates) {
                if (!chain.initial) {
                    continue;
                }

                InteractionType type = chain.interactionType;
                String detail = "Interaction: " + type + " (chainId=" + chain.chainId + ")";
                logIfChanged(handler, detail);
                if ("Inbound".equals(direction)) {
                    sendChatIfChanged(handler, detail);
                }
            }
        }
    }

    private static void logIfChanged(@Nonnull PacketHandler handler, @Nonnull String message) {
        String key = handler.getIdentifier();
        String lastMessage = LAST_MESSAGES.get(key);
        if (message.equals(lastMessage)) {
            return;
        }

        LAST_MESSAGES.put(key, message);
        LOGGER.atInfo().log("%s %s", handler.getIdentifier(), message);
    }

    private static void sendChatIfChanged(@Nonnull PacketHandler handler, @Nonnull String message) {
        if (!(handler instanceof com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler gameHandler)) {
            return;
        }

        PlayerRef playerRef = gameHandler.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store.isInThread()) {
            playerRef.sendMessage(Message.raw(message));
            return;
        }

        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (ref.isValid()) {
                playerRef.sendMessage(Message.raw(message));
            }
        });
    }

    private static boolean isGameplayPacket(@Nonnull Packet packet) {
        String name = packet.getClass().getName();
        return name.contains(".packets.player.")
            || name.contains(".packets.entities.")
            || name.contains(".packets.interaction.")
            || name.contains(".packets.world.")
            || name.contains(".packets.camera.")
            || name.contains(".packets.machinima.")
            || name.contains(".packets.mount.")
            || name.contains(".packets.worldmap.");
    }
}
