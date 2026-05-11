package com.CodeCreature.stencil;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;


/**
 * POC: Detects middle-click (Pick interaction) while holding a stencil item
 * by intercepting the outbound SyncInteractionChains packet.
 *
 * Registration: PacketAdapters.registerOutbound(StencilRadialInputListener::onOutboundPacket)
 */
public final class StencilRadialInputListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private StencilRadialInputListener() {}

    /**
     * Intercepts outbound packets. When a SyncInteractionChains packet contains
     * an InteractionType.Pick update, checks if the player is holding a stencil
     * and opens the radial menu.
     */
    public static void onOutboundPacket(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof SyncInteractionChains syncPacket)) return;

        for (SyncInteractionChain chain : syncPacket.updates) {
            if (chain.interactionType != InteractionType.Pick) continue;

            // Pick interaction detected — check if player is holding a stencil
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;

            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) continue;

            var itemInHand = player.getInventory().getActiveHotbarItem();
            if (!StencilMetadata.isStencil(itemInHand)) continue;

            // Stencil + Pick confirmed — open radial menu on world thread
            LOGGER.atInfo().log("[Stencil] Pick interaction for player %s — opening radial menu", playerRef.getUuid());
            var world = store.getExternalData().getWorld();
            world.execute(() -> openRadialMenu(playerRef, ref, store, player));
            break; // Only handle first pick chain
        }
    }

    /**
     * Opens the stencil radial menu for the given player.
     * Must be called on the world thread (via world.execute).
     */
    private static void openRadialMenu(PlayerRef playerRef, Ref<EntityStore> ref,
                                       Store<EntityStore> store, Player player) {
        var pageManager = player.getPageManager();
        pageManager.openCustomPage(ref, store, new StencilRadialMenuPage(playerRef, player));
    }
}
