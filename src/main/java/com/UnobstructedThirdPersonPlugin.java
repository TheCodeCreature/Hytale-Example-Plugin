package com;

import com.UnobstructedThirdPerson.command.UnobstructedCamera.UnobstructedCameraCommand;
import com.UnobstructedThirdPerson.camera.CameraTransparencyVolume;
import com.UnobstructedThirdPerson.camera.v2.CameraTransparencyVolumeV2;
import com.UnobstructedThirdPerson.command.debug.DebugCommand;
import com.UnobstructedThirdPerson.command.PreviewCommand;
import com.UnobstructedThirdPerson.command.placeblock.PlaceBlockCommand;
import com.UnobstructedThirdPerson.movement.NewMovementSystem;
import com.UnobstructedThirdPerson.preview.PreviewBlockManager;
import com.UnobstructedThirdPerson.portablebench.PortableBenchConfigLoader;
import com.UnobstructedThirdPerson.portablebench.PortableBenchInteraction;
import com.UnobstructedThirdPerson.resourcecollection.BreakBlockDiagnostic;
import com.UnobstructedThirdPerson.resourcecollection.DropScaler;
import com.UnobstructedThirdPerson.resourcecollection.PlacementCostScaler;
import com.UnobstructedThirdPerson.stencil.StencilPlacementSystem;
import com.UnobstructedThirdPerson.stencil.StencilSyncSystem;
import com.UnobstructedThirdPerson.stencil.StencilVisualManager;
import com.UnobstructedThirdPerson.placeblock.BlueprintBenchRecipeMutator;
import com.UnobstructedThirdPerson.placeblock.ui.BlueprintBenchOpenUIInteraction;
import com.UnobstructedThirdPerson.placeblock.ui.BlueprintBenchPrefsStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
        this.getCommandRegistry().registerCommand(new PlaceBlockCommand());
//        this.getCommandRegistry().registerCommand(new NewMovementCommand());
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, UnobstructedThirdPersonPlugin::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, UnobstructedThirdPersonPlugin::onPlayerDisconnect);

        // Boost stack sizes after assets are loaded
        this.getEventRegistry().register(LoadAssetEvent.class, UnobstructedThirdPersonPlugin::onAssetsLoaded);
        
        // Consume extra items when placing natural blocks (12x cost to match 12x drops)
        this.getEntityStoreRegistry().registerSystem(new PlacementCostScaler());

        // Blueprint stencil placement — intercepts PlaceBlockEvent for BSON-tagged block items
        this.getEntityStoreRegistry().registerSystem(new StencilPlacementSystem());

        // Diagnostic: log block drop config on break (toggle via /Debug BreakLog)
        this.getEntityStoreRegistry().registerSystem(new BreakBlockDiagnostic());

        // Register portable bench interaction type and configs
        this.getCodecRegistry(Interaction.CODEC)
                .register("Portable_Bench", PortableBenchInteraction.class, PortableBenchInteraction.CODEC);

        PortableBenchConfigLoader.loadAndRegister("/portable_benches.json");

        BlueprintBenchPrefsStore.initialize(this.getDataDirectory());

        // Register Blueprint Bench interaction type (opens custom UI page)
        this.getCodecRegistry(Interaction.CODEC)
                .register("BlueprintBench_OpenUI", BlueprintBenchOpenUIInteraction.class, BlueprintBenchOpenUIInteraction.CODEC);

        // PlaceBlockPlacementSystem — DISABLED: replaced by PlaceBlockToolInteraction (direct dispatch)
        // this.getEntityStoreRegistry().registerSystem(new PlaceBlockPlacementSystem());

        // PlaceBlockBenchInterceptor — DISABLED: replaced by custom UI + /placeblock commands
        // this.getEntityStoreRegistry().registerSystem(new PlaceBlockBenchInterceptor());

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

        // Register per-player stencil sync listeners
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            StencilSyncSystem.register(playerRef, player);
            StencilVisualManager.applyVisuals(playerRef, player);
        }

        CameraTransparencyVolumeV2.StartTransparencyVolumeLoop(playerRef, world, new ShapeCompositorPresetsV2()
                      .DefaultViewField()
        );
    }

    private static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        CameraTransparencyVolume.remove(playerRef.getUuid());
        CameraTransparencyVolumeV2.remove(playerRef.getUuid());
        PreviewBlockManager.remove(playerRef.getUuid());
        StencilSyncSystem.unregister(playerRef.getUuid());
        StencilVisualManager.removePlayer(playerRef.getUuid());
        NewMovementSystem.disableMoonGravity(playerRef.getUuid());
    }

    private static void onAssetsLoaded(LoadAssetEvent event) {
        DropScaler.apply();
        BlueprintBenchRecipeMutator.mutate();
    }
}
