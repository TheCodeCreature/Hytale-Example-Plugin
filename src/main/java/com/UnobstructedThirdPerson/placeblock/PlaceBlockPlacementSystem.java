package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// ── New imports for resource consumption ──
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * ECS event system that handles {@link PlaceBlockEvent} for armed PlaceBlock
 * placeholder items.
 *
 * <p><strong>Contract #12 — Mutual Exclusion:</strong> This system and
 * {@link com.UnobstructedThirdPerson.resourcecollection.PlacementCostScaler}
 * both subscribe to {@code PlaceBlockEvent}. They are mutually exclusive:
 * <ul>
 *   <li>This system handles events where the held item IS an armed PlaceBlock
 *       (checked via {@link PlaceBlockMetadata#isPlaceBlock(ItemStack)})</li>
 *   <li>{@code PlacementCostScaler} handles events where the held item is a
 *       natural resource block (checked via {@code NaturalResourceRegistry.isNaturalBlock(blockKey)})</li>
 *   <li>Placeholder items have no blockKey in NaturalResourceRegistry — overlap is impossible</li>
 *   <li>Both early-exit for events that aren't theirs</li>
 * </ul>
 *
 * <p><strong>Contract #11 — Atomic Consumption:</strong> When handling an armed
 * PlaceBlock placement, this system:
 * <ol>
 *   <li>Looks up the armed recipe via {@link CraftingRecipe#getAssetMap()}</li>
 *   <li>Gets material costs via {@link CraftingManager#getInputMaterials}
 *       (already 12× scaled by BlueprintBenchRecipeMutator)</li>
 *   <li>Checks affordability via {@link ItemContainer#canRemoveMaterials}</li>
 *   <li>Consumes atomically via {@link ItemContainer#removeMaterials} (allOrNothing=true)</li>
 *   <li>If successful: places the recipe's output block type in the world</li>
 *   <li>If unsuccessful: cancels placement and sends feedback to the player</li>
 * </ol>
 *
 * <p><strong>Contract #14 — Indistinguishable Blocks:</strong> The placed block
 * must be the actual recipe output block type, not the placeholder. This requires
 * overriding the block type that the engine places (RISK R3).
 *
 * <p><strong>Threading:</strong> Runs on the entity store's event dispatch thread,
 * same as {@code PlacementCostScaler}. No external synchronization needed.
 *
 * <p>Registered in {@link com.UnobstructedThirdPersonPlugin#setup()} via
 * {@code getEntityStoreRegistry().registerSystem(new PlaceBlockPlacementSystem())}.
 */
public class PlaceBlockPlacementSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public PlaceBlockPlacementSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull PlaceBlockEvent event) {

        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null) return;

        // ── Guard: only handle PlaceBlock items ──
        if (!PlaceBlockMetadata.isPlaceBlock(itemInHand)) return;

        // Resolve Player/Inventory early — needed for restoration in all exit paths.
        // The engine consumes the placeholder from the slot before this handler runs
        // (despite RemoveItemInHand: false), so we must put it back in finally.
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            log("WARNING: Could not resolve Player component.");
            event.setCancelled(true);
            return;
        }
        Inventory inventory = player.getInventory();
        short activeSlot = inventory.getActiveHotbarSlot();

        try {

        // ── Guard: must be armed with a recipe ──
        if (!PlaceBlockMetadata.isArmed(itemInHand)) {
            event.setCancelled(true);
            return;
        }

        // Cancel the event — we do NOT want the placeholder block placed in the world.
        // We'll manually place the target block type instead.
        event.setCancelled(true);

        log("DIAG: PlaceBlockEvent fired. itemInHand=" + itemInHand.getItemId()
                + " qty=" + itemInHand.getQuantity() + " cancelled=true");

        // Snapshot active slot BEFORE any consumption
        {
            ItemStack diagStack = inventory.getHotbar().getItemStack(activeSlot);
            log("DIAG: PRE-consume activeSlot=" + activeSlot
                    + " slotItem=" + (diagStack != null ? diagStack.getItemId() + " qty=" + diagStack.getQuantity() : "NULL"));
        }

        // Resolve the armed recipe's output block type
        String recipeId = PlaceBlockMetadata.getArmedRecipeId(itemInHand);
        String outputBlockTypeId = PlaceBlockMetadata.getOutputBlockTypeId(itemInHand);
        if (outputBlockTypeId == null) {
            log("WARNING: Armed placeholder has no output block type ID.");
            return;
        }

        BlockType targetBlockType = BlockType.getAssetMap().getAsset(outputBlockTypeId);
        if (targetBlockType == null) {
            log("WARNING: Output block type '" + outputBlockTypeId + "' not found in asset map.");
            return;
        }

        int targetBlockId = BlockType.getAssetMap().getIndex(outputBlockTypeId);

        // ──────────────────────────────────────────────────────────────
        // Resource consumption: check affordability → consume atomically
        // Materials are already 12× scaled by BlueprintBenchRecipeMutator.
        // ──────────────────────────────────────────────────────────────

        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) {
            log("WARNING: Recipe '" + recipeId + "' not found in asset map.");
            return;
        }

        List<MaterialQuantity> materials = CraftingManager.getInputMaterials(recipe, 1);

        ItemContainer container = inventory.getCombinedBackpackStorageHotbar();

        // ── Affordability check ──
        if (!container.canRemoveMaterials(materials)) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("§c[PlaceBlock] Not enough resources for " + outputBlockTypeId));
            }
            return;
        }

        // ── Atomic consumption ──
        log("DIAG: About to call removeMaterials for " + materials.size() + " material types");
        ListTransaction<MaterialTransaction> txn = container.removeMaterials(materials, true, true, true);
        if (!txn.succeeded()) {
            log("WARNING: removeMaterials failed after canRemoveMaterials passed for recipe '" + recipeId + "'");
            return;
        }
        log("DIAG: removeMaterials succeeded");

        // POST-consume slot check
        {
            short diagSlot2 = inventory.getActiveHotbarSlot();
            ItemStack diagStack2 = inventory.getHotbar().getItemStack(diagSlot2);
            log("DIAG: POST-consume activeSlot=" + diagSlot2
                    + " slotItem=" + (diagStack2 != null ? diagStack2.getItemId() + " qty=" + diagStack2.getQuantity() : "NULL"));
        }

        // Get placement position and rotation from the event
        Vector3i pos = event.getTargetBlock();
        RotationTuple rotation = event.getRotation();
        int rotationIndex = rotation.index();

        // Place the target block in the world
        World world = store.getExternalData().getWorld();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        WorldChunk worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) {
            log("WARNING: Chunk not loaded at " + pos.x + ", " + pos.z);
            return;
        }

        boolean placed = worldChunk.setBlock(pos.x, pos.y, pos.z, targetBlockId, targetBlockType, rotationIndex, 0, 6);

        // POST-setBlock slot check
        {
            short diagSlot3 = inventory.getActiveHotbarSlot();
            ItemStack diagStack3 = inventory.getHotbar().getItemStack(diagSlot3);
            log("DIAG: POST-setBlock activeSlot=" + diagSlot3
                    + " slotItem=" + (diagStack3 != null ? diagStack3.getItemId() + " qty=" + diagStack3.getQuantity() : "NULL")
                    + " placed=" + placed);
        }

        if (placed) {
            // Send feedback
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("§a[PlaceBlock] Placed " + outputBlockTypeId
                        + " (recipe: " + recipeId + ", cost: " + materials.size() + " material types)"));
            }
        } else {
            log("WARNING: setBlock returned false at " + pos.x + ", " + pos.y + ", " + pos.z);
        }

        } finally {
            // ── Restore the placeholder to the slot ──
            // The engine consumed it from the active slot before this handler ran
            // (PRE-consume shows NULL). We put the original item back; the
            // inventory-change event will trigger syncPlaceholder + checkAffordability
            // to handle Green/Red transitions and reskinning.
            inventory.getHotbar().setItemStackForSlot(activeSlot, itemInHand);
            log("DIAG: RESTORED placeholder to slot=" + activeSlot
                    + " item=" + itemInHand.getItemId());
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    private static void log(String msg) {
        System.out.println("[PlaceBlockPlacement] " + msg);
    }
}
