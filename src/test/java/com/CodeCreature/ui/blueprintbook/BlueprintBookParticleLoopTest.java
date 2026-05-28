package com.CodeCreature.ui.blueprintbook;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlueprintBookParticleLoopTest {

    @AfterEach
    void tearDown() {
        Map<UUID, BlueprintBookParticleLoop> instances = instances();
        for (UUID playerId : instances.keySet().toArray(new UUID[0])) {
            BlueprintBookParticleLoop.remove(playerId);
        }
    }

    @Test
    void constructorCreatesLoopInstance() {
        UUID playerId = UUID.randomUUID();
        PlayerRef playerRef = mock(PlayerRef.class);
        World world = mock(World.class);

        when(playerRef.getUuid()).thenReturn(playerId);
        when(playerRef.getUsername()).thenReturn("tester");

        BlueprintBookParticleLoop loop = new BlueprintBookParticleLoop(playerRef, world);

        assertNotNull(loop);
    }

    @Test
    void removeUnregistersKnownLoop() {
        UUID playerId = UUID.randomUUID();
        PlayerRef playerRef = mock(PlayerRef.class);
        World world = mock(World.class);

        when(playerRef.getUuid()).thenReturn(playerId);
        when(playerRef.getUsername()).thenReturn("tester");

        BlueprintBookParticleLoop loop = new BlueprintBookParticleLoop(playerRef, world);
        instances().put(playerId, loop);

        Map<UUID, BlueprintBookParticleLoop> instances = instances();
        assertTrue(instances.containsKey(playerId));

        BlueprintBookParticleLoop.remove(playerId);

        assertFalse(instances.containsKey(playerId), "remove should clear a registered loop");
    }

    @Test
    void removeUnknownPlayerIsNoOp() {
        assertDoesNotThrow(() -> BlueprintBookParticleLoop.remove(UUID.randomUUID()));
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, BlueprintBookParticleLoop> instances() {
        try {
            Field field = BlueprintBookParticleLoop.class.getDeclaredField("INSTANCES");
            field.setAccessible(true);
            return (Map<UUID, BlueprintBookParticleLoop>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not access loop registry", e);
        }
    }
}
