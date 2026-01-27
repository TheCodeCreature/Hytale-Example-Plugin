package com.example.exampleplugin.debug;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class InteractionPacketLogger implements PlayerPacketFilter {

    public InteractionPacketLogger() {
        PacketAdapters.registerInbound(this);
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (!(packet instanceof SyncInteractionChains syncPacket)) {
            return false;
        }

        for (SyncInteractionChain chain : syncPacket.updates) {
            if (!chain.initial) {
                continue;
            }

            InteractionType type = chain.interactionType;
            playerRef.sendMessage(Message.raw("Interaction: " + type + " (chainId=" + chain.chainId + ")"));
        }

        return false;
    }
}
