package com.example.exampleplugin.debug;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public final class InteractionPacketLogger implements PlayerPacketFilter {
    private static final Map<UUID, String> LAST_MESSAGES = new ConcurrentHashMap<>();
    private static final Set<String> IGNORED_PACKET_SIMPLE_NAMES = Set.of(
        "ClientMovement"
    );

    public InteractionPacketLogger() {
        PacketAdapters.registerInbound(this);
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (IGNORED_PACKET_SIMPLE_NAMES.contains(packet.getClass().getSimpleName())) {
            return false;
        }

        String tag = isGameplayPacket(packet) ? "[Gameplay]" : "[Non-Gameplay]";
        sendIfChanged(
            playerRef,
            tag + " Packet: " + packet.getClass().getSimpleName() + " (id=" + packet.getId() + ")"
        );

        if (packet instanceof SyncInteractionChains syncPacket) {
            for (SyncInteractionChain chain : syncPacket.updates) {
                if (!chain.initial) {
                    continue;
                }

                InteractionType type = chain.interactionType;
                sendIfChanged(playerRef, "Interaction: " + type + " (chainId=" + chain.chainId + ")");
            }
        }

        return false;
    }

    private static void sendIfChanged(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        UUID playerId = playerRef.getUuid();
        String lastMessage = LAST_MESSAGES.get(playerId);
        if (message.equals(lastMessage)) {
            return;
        }

        LAST_MESSAGES.put(playerId, message);
        playerRef.sendMessage(Message.raw(message));
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
