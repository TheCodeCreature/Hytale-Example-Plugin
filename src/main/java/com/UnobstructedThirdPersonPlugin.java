package com;

import com.UnobstructedThirdPerson.command.UnobstructedCamera.UnobstructedCameraCommand;
import com.UnobstructedThirdPerson.camera.CameraTransparencyVolume;
import com.UnobstructedThirdPerson.command.debug.DebugCommand;
import com.UnobstructedThirdPerson.shape.ShapeCompositorPresets;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class UnobstructedThirdPersonPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public UnobstructedThirdPersonPlugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup(){
        this.getCommandRegistry().registerCommand(new UnobstructedCameraCommand());
        this.getCommandRegistry().registerCommand(new DebugCommand());
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, UnobstructedThirdPersonPlugin::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, UnobstructedThirdPersonPlugin::onPlayerDisconnect);
    }

    private static void onPlayerReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();

        Store<EntityStore> store = ref.getStore();

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();
        CameraTransparencyVolume.StartTransparencyVolumeLoop(playerRef, world, new ShapeCompositorPresets().Test());
    }

    private static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        CameraTransparencyVolume.remove(playerRef.getUuid());
    }
}
