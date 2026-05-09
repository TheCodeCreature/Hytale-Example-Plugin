package com.UnobstructedThirdPerson.stencil;

import com.UnobstructedThirdPerson.resourcecollection.BenchRecipeRegistries;
import com.UnobstructedThirdPerson.placeblock.RecipeAffordabilityResolver;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.UnobstructedThirdPerson.util.ShapeAwareRaycast;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BlueprintBookParticleLoop {

    private static final Logger LOGGER = Logger.getLogger("BlueprintBookParticleLoop");
    private static final long UPDATE_INTERVAL_MILLIS = 100;
    private static final String BLUEPRINT_BOOK_ITEM_ID = "BlueprintBook";
    private static final String EFFECT_ID_GREEN = "Drop_Uncommon";
    private static final String EFFECT_ID_RED = "BlockPlaceFail";

    private static final Map<UUID, BlueprintBookParticleLoop> INSTANCES = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final World world;
    private ScheduledFuture<?> updateTask;
    private Vector3i lastTargetBlock;
    private Ref<EntityStore> activeEntity;
    private volatile boolean active = true;

    public BlueprintBookParticleLoop(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        this.playerRef = playerRef;
        this.world = world;
    }

    public static void start(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerId = playerRef.getUuid();
        BlueprintBookParticleLoop existing = INSTANCES.get(playerId);
        if (existing != null) {
            existing.shutdown();
            INSTANCES.remove(playerId);
            LOGGER.info("[BlueprintBookParticle] Replaced stale loop for player: " + playerRef.getUsername());
        }
        BlueprintBookParticleLoop instance = new BlueprintBookParticleLoop(playerRef, world);
        instance.startUpdateLoop();
        INSTANCES.put(playerId, instance);
        LOGGER.info("[BlueprintBookParticle] Created loop for player: " + playerRef.getUsername());
    }

    public static void remove(@Nonnull UUID playerId) {
        BlueprintBookParticleLoop instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.shutdown();
            LOGGER.info("[BlueprintBookParticle] Removed loop for player: " + playerId);
        }
    }

    private void startUpdateLoop() {
        updateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (!active) return;
            try {
                world.execute(() -> {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        removeHighlightEntity(ref != null ? ref.getStore() : null);
                        active = false;
                        return;
                    }

                    Store<EntityStore> store = ref.getStore();
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) {
                        removeHighlightEntity(store);
                        active = false;
                        return;
                    }

                    byte activeSlot = player.getInventory().getActiveHotbarSlot();
                    ItemStack held = player.getInventory().getHotbar().getItemStack(activeSlot);
                    if (held == null || !held.getItemId().equals(BLUEPRINT_BOOK_ITEM_ID)) {
                        removeHighlightEntity(store);
                        active = false;
                        return;
                    }

                    active = true;

                    // Shape-aware raycast — checks actual interaction hitboxes, not full cubes
                    Vector3i target = ShapeAwareRaycast.getTargetBlock(ref, 8.0, store);
                    if (target == null) {
                        removeHighlightEntity(store);
                        return;
                    }

                    BlockType blockType = world.getBlockType(target.x, target.y, target.z);
                    if (blockType == null) {
                        removeHighlightEntity(store);
                        return;
                    }

                    String blockTypeId = blockType.getId();
                    CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(blockTypeId);
                    if (recipe == null) {
                        removeHighlightEntity(store);
                        return;
                    }

                    // Target has a recipe — manage the highlight entity
                    if (target.equals(lastTargetBlock) && activeEntity != null && activeEntity.isValid()) {
                        // Entity already exists at this target — effect is infinite, nothing to do
                        return;
                    }

                    // Target changed or entity missing — replace
                    if (activeEntity != null && activeEntity.isValid()) {
                        store.removeEntity(activeEntity, RemoveReason.REMOVE);
                    }
                    CombinedItemContainer container = player.getInventory().getCombinedBackpackStorageHotbar();
                    boolean affordable = RecipeAffordabilityResolver.isAffordable(recipe, container);
                    activeEntity = spawnHighlightEntity(store, target, blockTypeId, affordable);
                    lastTargetBlock = target;
                });
            } catch (Exception e) {
                LOGGER.warning("[BlueprintBookParticle] Error in update loop: " + e.getMessage());
            }
        }, UPDATE_INTERVAL_MILLIS, UPDATE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private Ref<EntityStore> spawnHighlightEntity(Store<EntityStore> store, Vector3i target, String blockTypeKey, boolean affordable) {
        LOGGER.fine(() -> "[BlueprintBookParticle] Spawning highlight entity at " + target + " for block " + blockTypeKey + " affordable=" + affordable);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        // Position and rotation — match the placed block's orientation
        Vector3d pos = new Vector3d(target.x + 0.5, target.y, target.z + 0.5);
        int rotationIndex = world.getBlockRotationIndex(target.x, target.y, target.z);
        RotationTuple rotationTuple = RotationTuple.get(rotationIndex);
        Vector3f rotation = new Vector3f(
                (float) rotationTuple.pitch().getRadians(),
                (float) rotationTuple.yaw().getRadians()-3.14f, // Rotate 180 degrees to align with player's view
                (float) rotationTuple.roll().getRadians()
        );
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));

        // UUID for identification
        holder.ensureComponent(UUIDComponent.getComponentType());

        // Network ID — required for entity tracker to index and send to clients
        int networkId = store.getExternalData().takeNextNetworkId();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(networkId));

        // Visible component for tracker processing
        holder.ensureComponent(EntityModule.get().getVisibleComponentType());

        // Effect controller — required for EntityEffect application
        holder.ensureComponent(EffectControllerComponent.getComponentType());

        // Block entity — renders as the targeted block's cube model with its textures
        holder.addComponent(BlockEntity.getComponentType(), new BlockEntity(blockTypeKey));
        holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(2.1f));

        // Non-serialized — don't persist this entity to disk
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

        Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
        LOGGER.fine(() -> "[BlueprintBookParticle] Entity spawned with networkId=" + networkId);

        // Apply the highlight effect — green if affordable, red if not
        String effectId = affordable ? EFFECT_ID_GREEN : EFFECT_ID_RED;
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        EffectControllerComponent effectCtrl = store.getComponent(entityRef, EffectControllerComponent.getComponentType());
        effectCtrl.addEffect(entityRef, effect, UPDATE_INTERVAL_MILLIS, OverlapBehavior.OVERWRITE, store);

        return entityRef;
    }

    private void removeHighlightEntity(Store<EntityStore> store) {
        if (activeEntity != null && activeEntity.isValid() && store != null) {
            store.removeEntity(activeEntity, RemoveReason.REMOVE);
        }
        activeEntity = null;
        lastTargetBlock = null;
    }

    private void shutdown() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
        if (activeEntity != null) {
            try {
                world.execute(() -> {
                    if (activeEntity != null && activeEntity.isValid()) {
                        Store<EntityStore> store = activeEntity.getStore();
                        store.removeEntity(activeEntity, RemoveReason.REMOVE);
                    }
                    activeEntity = null;
                });
            } catch (Exception e) {
                LOGGER.warning("[BlueprintBookParticle] Error removing entity on shutdown: " + e.getMessage());
            }
        }
    }
}
