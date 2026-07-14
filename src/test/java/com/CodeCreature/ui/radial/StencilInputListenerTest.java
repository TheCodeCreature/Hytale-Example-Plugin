package com.CodeCreature.ui.radial;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StencilInputListenerTest {

    @Test
    void onOutboundPacketIgnoresNonSyncPackets() {
        PlayerRef playerRef = mock(PlayerRef.class);
        Packet packet = mock(Packet.class);

        assertDoesNotThrow(() -> StencilInputListener.onOutboundPacket(playerRef, packet));
    }

    @Test
    void onOutboundPacketHandlesNullEntityReferenceGuardForSyncPacket() {
        PlayerRef playerRef = mock(PlayerRef.class);
        when(playerRef.getReference()).thenReturn(null);

        Packet packet = createPacketWithInteraction(InteractionType.Pick);

        assertDoesNotThrow(() -> StencilInputListener.onOutboundPacket(playerRef, packet));
    }

    @Test
    void onOutboundPacketSkipsUnsupportedInteractionWithoutEntityLookup() {
        PlayerRef playerRef = mock(PlayerRef.class);
        Packet packet = createPacketWithInteraction(null);

        assertDoesNotThrow(() -> StencilInputListener.onOutboundPacket(playerRef, packet));

        verify(playerRef, never()).getReference();
    }

    @Test
    void privateNullGuardsReturnEarlyWithoutThrowing() {
        assertDoesNotThrow(() -> invokePrivate("openRadialMenu", null, null, null, null));
        assertDoesNotThrow(() -> invokePrivate("pickToSwitch", null, null, null, null));
    }

    private static Packet createPacketWithInteraction(InteractionType interactionType) {
        try {
            SyncInteractionChains packet = instantiate(SyncInteractionChains.class);
            SyncInteractionChain chain = instantiate(SyncInteractionChain.class);

            Field interactionTypeField = SyncInteractionChain.class.getDeclaredField("interactionType");
            interactionTypeField.setAccessible(true);
            interactionTypeField.set(chain, interactionType);

            Field updatesField = SyncInteractionChains.class.getDeclaredField("updates");
            updatesField.setAccessible(true);
            Class<?> updatesType = updatesField.getType();

            if (updatesType.isArray()) {
                Object arr = Array.newInstance(SyncInteractionChain.class, 1);
                Array.set(arr, 0, chain);
                updatesField.set(packet, arr);
            } else if (Iterable.class.isAssignableFrom(updatesType)) {
                List<SyncInteractionChain> updates = new ArrayList<>();
                updates.add(chain);
                updatesField.set(packet, updates);
            } else {
                throw new IllegalStateException("Unsupported updates type: " + updatesType.getName());
            }

            return packet;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not construct SyncInteractionChains packet for test", e);
        }
    }

    private static <T> T instantiate(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void invokePrivate(String methodName, PlayerRef playerRef, Ref<EntityStore> ref,
                                      com.hypixel.hytale.component.Store<EntityStore> store,
                                      com.hypixel.hytale.server.core.entity.entities.Player player) throws Exception {
        Method method = StencilInputListener.class.getDeclaredMethod(methodName,
                PlayerRef.class,
                Ref.class,
                com.hypixel.hytale.component.Store.class,
                com.hypixel.hytale.server.core.entity.entities.Player.class);
        method.setAccessible(true);
        method.invoke(null, playerRef, ref, store, player);
    }
}
