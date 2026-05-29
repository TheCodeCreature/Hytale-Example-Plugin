package com.CodeCreature.ui.blueprintbook;

import com.CodeCreature.crafting.RecipeAffordabilityResolver;
import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.util.BoundingBoxRayCast;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
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
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
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

import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

public class BlueprintBookParticleLoop {

    private static final long UPDATE_INTERVAL_MILLIS = 100;
    private static final long EFFECT_DURATION_MILLIS = 500;
    private static final String BLUEPRINT_BOOK_ITEM_ID = "BlueprintBook";
    private static final String EFFECT_ID_DEFAULT = "Drop_Rare";
    private static final String EFFECT_ID_GREEN = "Drop_Uncommon";
    private static final String EFFECT_ID_RED = "BlockPlaceFail";

    // Feature flag: when false, skips affordability check and always shows green.
    // Set to true once auto-craft affordability performance is optimized.
    private static final boolean ENABLE_AFFORDABILITY_CHECK = false;

    private static final Map<UUID, BlueprintBookParticleLoop> INSTANCES = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final World world;
    private ScheduledFuture<?> updateTask;
    private Vector3i lastTargetBlock;
    private Ref<EntityStore> activeEntity;
    private boolean lastAffordable;
    private volatile boolean active = true;
    private final AtomicBoolean pending = new AtomicBoolean(false);

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
            DebugLogger.log(BLUEPRINT_BOOK, Level.INFO, "[BlueprintBookParticle] Replaced stale loop for player: " + playerRef.getUsername());
        }
        BlueprintBookParticleLoop instance = new BlueprintBookParticleLoop(playerRef, world);
        instance.startUpdateLoop();
        INSTANCES.put(playerId, instance);
        DebugLogger.log(BLUEPRINT_BOOK, Level.INFO, "[BlueprintBookParticle] Created loop for player: " + playerRef.getUsername());
    }

    public static void remove(@Nonnull UUID playerId) {
        BlueprintBookParticleLoop instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.shutdown();
            DebugLogger.log(BLUEPRINT_BOOK, Level.INFO, "[BlueprintBookParticle] Removed loop for player: " + playerId);
        }
    }

    private void startUpdateLoop() {
        updateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (!active) { return; }
            if (!pending.compareAndSet(false, true)) { return; }
            try {
                world.execute(this::executeTick);
            } catch (Exception e) {
                pending.set(false);
                DebugLogger.log(BLUEPRINT_BOOK, Level.WARNING, "[BlueprintBookParticle] Error in update loop: " + e.getMessage());
            }
        }, UPDATE_INTERVAL_MILLIS, UPDATE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void executeTick() {
        pending.set(false);
        if (!active) { return; }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            removeHighlightEntity(ref != null ? ref.getStore() : (activeEntity != null ? activeEntity.getStore() : null));
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
        var hotbar = player.getInventory().getHotbar();
        if (hotbar == null) {
            removeHighlightEntity(store);
            return;
        }
        ItemStack held = hotbar.getItemStack(activeSlot);
        boolean holdingBook = held != null && held.getItemId().equals(BLUEPRINT_BOOK_ITEM_ID);
        if (!holdingBook) {
            removeHighlightEntity(store);
            return;
        }

        // Shape-aware raycast — checks actual interaction hitboxes, not full cubes
        Vector3i target = BoundingBoxRayCast.getTargetBlock(ref, 8.0, store);
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

        // Target has a recipe — resolve affordability
        boolean affordable;
        if (ENABLE_AFFORDABILITY_CHECK) {
            CombinedItemContainer container = player.getInventory().getCombinedBackpackStorageHotbar();
            affordable = RecipeAffordabilityResolver.isAffordable(recipe, container);
        } else {
            affordable = true;
        }

        // Same target, entity still alive, affordability unchanged — nothing to do
        if (target.equals(lastTargetBlock) && activeEntity != null && activeEntity.isValid()
                && affordable == lastAffordable) {
            return;
        }

        // Target changed, affordability changed, or entity missing — replace
        if (activeEntity != null && activeEntity.isValid()) {
            store.removeEntity(activeEntity, RemoveReason.REMOVE);
        }
        activeEntity = spawnHighlightEntity(store, target, blockTypeId, affordable);
        lastTargetBlock = target;
        lastAffordable = affordable;
    }

    private Ref<EntityStore> spawnHighlightEntity(Store<EntityStore> store, Vector3i target, String blockTypeKey,
            boolean affordable) {
        DebugLogger.log(BLUEPRINT_BOOK, Level.FINE, () -> "[BlueprintBookParticle] Spawning highlight entity at " + target + " for block "
                + blockTypeKey + " affordable=" + affordable);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        // Position and rotation — match the placed block's orientation
        Vector3d pos = new Vector3d(target.x + 0.5, target.y, target.z + 0.5);
        int rotationIndex = world.getBlockRotationIndex(target.x, target.y, target.z);
        RotationTuple rotationTuple = RotationTuple.get(rotationIndex);
        Rotation3f rotation = new Rotation3f(
            (float) rotationTuple.pitch().getRadians(),
            (float) rotationTuple.yaw().getRadians() - 3.14f, // Rotate 180 degrees to align with player's view
            (float) rotationTuple.roll().getRadians());
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

        // Intangible — exclude from spatial indices so client raycasts pass through
        // to the underlying block. Without this, the entity's auto-injected BoundingBox
        // intercepts all interactions (break, use, place).
        holder.ensureComponent(Intangible.getComponentType());

        // Non-serialized — don't persist this entity to disk
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

        Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
        if (entityRef == null) {
            DebugLogger.log(BLUEPRINT_BOOK, Level.WARNING, "[BlueprintBookParticle] Failed to spawn highlight entity");
            return null;
        }
        DebugLogger.log(BLUEPRINT_BOOK, Level.FINE, () -> "[BlueprintBookParticle] Entity spawned with networkId=" + networkId);

        // Apply the highlight effect — green if affordable, red if not
        String effectId = EFFECT_ID_DEFAULT;
        if (ENABLE_AFFORDABILITY_CHECK) {
            effectId = affordable ? EFFECT_ID_GREEN : EFFECT_ID_RED;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        EffectControllerComponent effectCtrl = store.getComponent(entityRef,
                EffectControllerComponent.getComponentType());
        if (effect == null || effectCtrl == null) {
            DebugLogger.log(BLUEPRINT_BOOK, Level.WARNING, "[BlueprintBookParticle] Missing effect or controller for highlight entity");
            return entityRef;
        }
        effectCtrl.addEffect(entityRef, effect, EFFECT_DURATION_MILLIS, OverlapBehavior.EXTEND, store);

        return entityRef;
    }

    private void removeHighlightEntity(Store<EntityStore> store) {
        if (activeEntity != null && activeEntity.isValid() && store != null) {
            store.removeEntity(activeEntity, RemoveReason.REMOVE);
        }
        activeEntity = null;
        lastTargetBlock = null;
        lastAffordable = false;
    }

    private void shutdown() {
        active = false;
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
        try {
            world.execute(() -> {
                if (activeEntity != null && activeEntity.isValid()) {
                    Store<EntityStore> store = activeEntity.getStore();
                    store.removeEntity(activeEntity, RemoveReason.REMOVE);
                }
                activeEntity = null;
            });
        } catch (Exception e) {
            DebugLogger.log(BLUEPRINT_BOOK, Level.WARNING,
                    "[BlueprintBookParticle] Error queueing entity cleanup: " + e.getMessage());
        }
    }
}
