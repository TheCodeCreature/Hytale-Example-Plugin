package com.CodeCreature;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.CodeCreature.command.ParticleCommand;
import com.CodeCreature.command.placeblock.PlaceBlockCommand;
import com.CodeCreature.crafting.BlueprintBenchRecipeMutator;
import com.CodeCreature.ui.bench.BlueprintBenchOpenUIInteraction;
import com.CodeCreature.ui.bench.BlueprintBenchPrefsStore;
import com.CodeCreature.scaling.BreakBlockDiagnostic;
import com.CodeCreature.scaling.DropScaler;
import com.CodeCreature.scaling.PlacementCostScaler;
import com.CodeCreature.ui.blueprintbook.BlueprintBookParticleLoop;
import com.CodeCreature.stencil.StencilDropDestroySystem;
import com.CodeCreature.stencil.StencilPlacementSystem;
import com.CodeCreature.ui.radial.StencilInputListener;
import com.CodeCreature.stencil.StencilSyncSystem;
import com.CodeCreature.stencil.StencilVisualManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class Plugin extends JavaPlugin {
    
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public Plugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup(){
        this.getCommandRegistry().registerCommand(new PlaceBlockCommand());
        this.getCommandRegistry().registerCommand(new ParticleCommand());
//        this.getCommandRegistry().registerCommand(new NewMovementCommand());
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, Plugin::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, Plugin::onPlayerDisconnect);

        // Stencil radial input — detect Pick interaction while holding a stencil (POC)
        PacketAdapters.registerOutbound((PlayerPacketWatcher) StencilInputListener::onOutboundPacket);

        // Boost stack sizes after assets are loaded
        this.getEventRegistry().register(LoadAssetEvent.class, Plugin::onAssetsLoaded);
        
        // Consume extra items when placing natural blocks (12x cost to match 12x drops)
        this.getEntityStoreRegistry().registerSystem(new PlacementCostScaler());

        // Blueprint stencil placement — intercepts PlaceBlockEvent for BSON-tagged block items
        this.getEntityStoreRegistry().registerSystem(new StencilPlacementSystem());

        // Stencil drop destroy — cancel world item spawn when dropping stencils (G key / drag-out)
        this.getEntityStoreRegistry().registerSystem(new StencilDropDestroySystem());

        // Diagnostic: log block drop config on break (toggle via /Debug BreakLog)
        this.getEntityStoreRegistry().registerSystem(new BreakBlockDiagnostic());

        BlueprintBenchPrefsStore.initialize(this.getDataDirectory());

        // Register Blueprint Bench interaction type (opens custom UI page)
        this.getCodecRegistry(Interaction.CODEC)
                .register("BlueprintBench_OpenUI", BlueprintBenchOpenUIInteraction.class, BlueprintBenchOpenUIInteraction.CODEC);

        // Register Blueprint Book pick-stencil interaction type
        this.getCodecRegistry(Interaction.CODEC)
                .register("BlueprintBook_PickStencil",
                        com.CodeCreature.ui.blueprintbook.BlueprintBookPickStencilInteraction.class,
                        com.CodeCreature.ui.blueprintbook.BlueprintBookPickStencilInteraction.CODEC);
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

        BlueprintBookParticleLoop.start(playerRef, world);
    }

    private static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        StencilSyncSystem.unregister(playerRef.getUuid());
        StencilVisualManager.removePlayer(playerRef.getUuid());
        BlueprintBookParticleLoop.remove(playerRef.getUuid());
    }

    private static void onAssetsLoaded(LoadAssetEvent event) {
        DropScaler.apply();
        BlueprintBenchRecipeMutator.mutate();
    }
}
