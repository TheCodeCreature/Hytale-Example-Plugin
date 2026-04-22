package com;

import com.UnobstructedThirdPerson.command.UnobstructedCamera.UnobstructedCameraCommand;
import com.UnobstructedThirdPerson.camera.CameraTransparencyVolume;
import com.UnobstructedThirdPerson.camera.v2.CameraTransparencyVolumeV2;
import com.UnobstructedThirdPerson.command.debug.DebugCommand;
import com.UnobstructedThirdPerson.command.PreviewCommand;
import com.UnobstructedThirdPerson.movement.NewMovementSystem;
import com.UnobstructedThirdPerson.preview.PreviewBlockManager;
import com.UnobstructedThirdPerson.portablebench.PortableBenchConfigLoader;
import com.UnobstructedThirdPerson.portablebench.PortableBenchInteraction;
import com.UnobstructedThirdPerson.resourcecollection.DropScaler;
import com.UnobstructedThirdPerson.resourcecollection.PlacementCostScaler;
import com.UnobstructedThirdPerson.placeblock.BlueprintBenchRecipeMutator;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockConfigLoader;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockMenuInteraction;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockPlacementSystem;
// import com.UnobstructedThirdPerson.assignbench.AssignBenchInteraction;
import com.UnobstructedThirdPerson.shape.v2.ShapeCompositorPresetsV2;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
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
        this.getCommandRegistry().registerCommand(new PreviewCommand());
//        this.getCommandRegistry().registerCommand(new NewMovementCommand());
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, UnobstructedThirdPersonPlugin::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, UnobstructedThirdPersonPlugin::onPlayerDisconnect);

        // Boost stack sizes after assets are loaded
        this.getEventRegistry().register(LoadAssetEvent.class, UnobstructedThirdPersonPlugin::onAssetsLoaded);
        
        // Consume extra items when placing natural blocks (12x cost to match 12x drops)
        this.getEntityStoreRegistry().registerSystem(new PlacementCostScaler());

        // Register portable bench interaction type and configs
        this.getCodecRegistry(Interaction.CODEC)
                .register("Portable_Bench", PortableBenchInteraction.class, PortableBenchInteraction.CODEC);

        PortableBenchConfigLoader.loadAndRegister("/portable_benches.json");

        PlaceBlockConfigLoader.loadAndStore("/placeblock_config.json");

        // Register PlaceBlock interaction types
        this.getCodecRegistry(Interaction.CODEC)
                .register("PlaceBlock_Menu", PlaceBlockMenuInteraction.class, PlaceBlockMenuInteraction.CODEC);
        // this.getCodecRegistry(Interaction.CODEC)
        //         .register("Assign_Bench", AssignBenchInteraction.class, AssignBenchInteraction.CODEC);

        // Register PlaceBlock placement system (intercepts PlaceBlockEvent for armed PlaceBlocks)
        this.getEntityStoreRegistry().registerSystem(new PlaceBlockPlacementSystem());

        // Register server-side collision validation system
        // Now works because we declared dependency on EntityModule in MANIFEST
//        this.getEntityStoreRegistry().registerSystem(new NewMovementSystem());
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

        playerRef.sendMessage(Message.raw("§a[Plugin] Resource scaling active."));

        CameraTransparencyVolumeV2.StartTransparencyVolumeLoop(playerRef, world, new ShapeCompositorPresetsV2()
                      .DefaultViewField()
        );
    }

    private static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        CameraTransparencyVolume.remove(playerRef.getUuid());
        CameraTransparencyVolumeV2.remove(playerRef.getUuid());
        PreviewBlockManager.remove(playerRef.getUuid());
        NewMovementSystem.disableMoonGravity(playerRef.getUuid());
    }

    private static void onAssetsLoaded(LoadAssetEvent event) {
        DropScaler.apply();
        BlueprintBenchRecipeMutator.mutate();
    }
}
