package com.example.exampleplugin.camera;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;

public class MmoCamCommandTest {

    private static final class NoOpPacketHandler extends PacketHandler {
        public NoOpPacketHandler() {
            super(new EmbeddedChannel(), new ProtocolVersion(0));
        }

        @Nonnull
        @Override
        public String getIdentifier() {
            return "test";
        }

        @Override
        public void accept(@Nonnull com.hypixel.hytale.protocol.Packet packet) {
        }

        @Override
        public void writeNoCache(@Nonnull com.hypixel.hytale.protocol.Packet packet) {
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        throw new NoSuchFieldException(fieldName);
    }

    @Test
    void command_existsWithOffSubcommand() throws Exception {
        Class<?> commandClass = Class.forName("com.example.exampleplugin.camera.MmoCamCommand");
        Object command = commandClass.getConstructor().newInstance();

        String name = (String) commandClass.getMethod("getName").invoke(command);
        assertEquals("mmocam", name);

        Field subCommandsField = findField(commandClass, "subCommands");
        subCommandsField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> subCommands = (Map<String, Object>) subCommandsField.get(command);

        assertTrue(subCommands.containsKey("off"), "mmocam must have an off subcommand");

        Object offCommand = subCommands.get("off");
        assertNotNull(offCommand);

        String offName = (String) offCommand.getClass().getMethod("getName").invoke(offCommand);
        assertEquals("off", offName);
    }

    @Test
    void executeSync_doesNotThrowWhenCalledOffWorldThread() throws Exception {
        CountDownLatch storeReady = new CountDownLatch(1);
        CountDownLatch storeShutdown = new CountDownLatch(1);

        AtomicReference<Store<EntityStore>> storeRef = new AtomicReference<>();
        AtomicReference<ComponentType<EntityStore, PlayerRef>> playerRefComponentTypeRef = new AtomicReference<>();
        AtomicReference<Ref<EntityStore>> playerEntityRefRef = new AtomicReference<>();

        Thread worldThread = new Thread(() -> {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                Object unsafeInstance = theUnsafeField.get(null);

                World world = (World) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafeInstance, World.class);
                Field acceptingTasksField = World.class.getDeclaredField("acceptingTasks");
                acceptingTasksField.setAccessible(true);
                acceptingTasksField.set(world, new AtomicBoolean(true));
                Field taskQueueField = World.class.getDeclaredField("taskQueue");
                taskQueueField.setAccessible(true);
                LinkedBlockingDeque<Runnable> taskQueue = new LinkedBlockingDeque<>();
                taskQueueField.set(world, taskQueue);

                EntityStore entityStore = new EntityStore(world);
                entityStore.start(EmptyResourceStorage.get());

                Store<EntityStore> store = entityStore.getStore();
                storeRef.set(store);

                ComponentType<EntityStore, PlayerRef> playerRefComponentType = EntityStore.REGISTRY.registerComponent(PlayerRef.class, () -> {
                    throw new UnsupportedOperationException();
                });
                playerRefComponentTypeRef.set(playerRefComponentType);

                Holder<EntityStore> entityHolder = EntityStore.REGISTRY.newHolder();
                PlayerRef componentPlayerRef = new PlayerRef(
                        EntityStore.REGISTRY.newHolder(),
                        UUID.randomUUID(),
                        "tester",
                        "en_us",
                        new NoOpPacketHandler(),
                        new ChunkTracker()
                );
                entityHolder.addComponent(playerRefComponentType, componentPlayerRef);
                Ref<EntityStore> playerEntityRef = store.addEntity(entityHolder, AddReason.SPAWN);
                if (playerEntityRef == null) {
                    throw new IllegalStateException("Failed to create test player entity");
                }
                playerEntityRefRef.set(playerEntityRef);

                storeReady.countDown();
                while (storeShutdown.getCount() > 0) {
                    Runnable task = taskQueue.poll();
                    if (task != null) {
                        task.run();
                    } else {
                        Thread.onSpinWait();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "WorldThread-Default");

        worldThread.start();
        storeReady.await();

        Store<EntityStore> store = storeRef.get();
        assertNotNull(store);

        Ref<EntityStore> playerEntityRef = playerEntityRefRef.get();
        assertNotNull(playerEntityRef);

        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        Object unsafeInstance = theUnsafeField.get(null);
        Universe universe = (Universe) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafeInstance, Universe.class);

        Field universeInstanceField = Universe.class.getDeclaredField("instance");
        universeInstanceField.setAccessible(true);
        universeInstanceField.set(null, universe);

        Field playerRefComponentTypeField = Universe.class.getDeclaredField("playerRefComponentType");
        playerRefComponentTypeField.setAccessible(true);
        playerRefComponentTypeField.set(universe, playerRefComponentTypeRef.get());

        Player player = new Player();
        player.setReference(playerEntityRef);
        player.init(UUID.randomUUID(), new PlayerRef(
                EntityStore.REGISTRY.newHolder(),
                UUID.randomUUID(),
                "tester",
                "en_us",
                new NoOpPacketHandler(),
                new ChunkTracker()
        ));

        MmoCamCommand command = new MmoCamCommand();
        CommandContext ctx = new CommandContext(command, player, "mmocam");

        assertDoesNotThrow(() -> command.executeSync(ctx));

        storeShutdown.countDown();
        worldThread.join();
    }
}
