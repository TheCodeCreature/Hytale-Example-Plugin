package com.CodeCreature.stencil;

import com.CodeCreature.crafting.AutoCraftPlan;
import com.CodeCreature.crafting.AutoCraftPlanner;
import com.CodeCreature.crafting.ConsumptionEntry;
import com.CodeCreature.crafting.PlaceBlockCostUtil;
import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Intercepts {@link PlaceBlockEvent} for stencil stencil items and redirects
 * resource consumption from the item stack to recipe materials.
 *
 * <p><b>Strategy — "Bump and Let Through":</b> Instead of cancelling the event
 * (which breaks the client's ghost preview and rotation state), this handler
 * lets the engine handle placement natively. To prevent the stencil from being
 * consumed, it temporarily bumps the stencil's stack count from 1 to 2. The
 * engine then consumes 1 (2 → 1), and the stencil survives.</p>
 *
 * <p><b>Flow:</b></p>
 * <ol>
 *   <li>Detect stencil via BSON metadata — non-stencils pass through untouched</li>
 *   <li>Resolve recipe and compute material costs</li>
 *   <li>If not affordable: cancel event, send error (preview breaks, but placement
 *       must be blocked)</li>
 *   <li>If affordable: bump stencil quantity to 2, consume recipe resources atomically</li>
 *   <li>Return without cancelling — engine places the block natively and consumes 1
 *       from the bumped stack (2 → 1), preserving preview, rotation, and connected
 *       block rules</li>
 * </ol>
 *
 * <p><b>Why not cancel?</b> Cancelling PlaceBlockEvent triggers {@code invalidateBlock()},
 * which terminates the client's {@code PlaceBlockInteraction}. This kills the ghost
 * preview and rotation state, breaking continuous placement.</p>
 *
 * <p><b>Registration:</b> In plugin {@code setup()}:
 * {@code getEntityStoreRegistry().registerSystem(new StencilPlacementSystem())}</p>
 */
public class StencilPlacementSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public StencilPlacementSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull PlaceBlockEvent event) {

        // 1. Detection — non-stencil items pass through untouched
        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null) return;
        if (!StencilMetadata.isStencil(itemInHand)) return;

        // 2. Resolve player
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) return;
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());

        // 3. Recipe resolution
        String recipeId = StencilMetadata.getRecipeId(itemInHand);
        if (recipeId == null) {
            event.setCancelled(true);
            sendError(playerRef, "Stencil has no recipe ID in metadata");
            return;
        }
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) {
            event.setCancelled(true);
            sendError(playerRef, "Recipe not found: " + recipeId);
            return;
        }

        // 4. Resolve output block type (for feedback message)
        String outputBlockTypeId = resolveOutputBlockTypeId(recipe);
        if (outputBlockTypeId == null) {
            event.setCancelled(true);
            sendError(playerRef, "Recipe has no placeable block output: " + recipeId);
            return;
        }

        // 5. Per-unit cost (still needed for empty-materials check)
        List<MaterialQuantity> materials = PlaceBlockCostUtil.getPerUnitCost(recipe);
        if (materials.isEmpty()) {
            event.setCancelled(true);
            sendError(playerRef, "Recipe has no input materials: " + recipeId);
            return;
        }

        // 6. Auto-craft planning — computes what to consume (direct intermediates + raw materials for deficit)
        Inventory inventory = player.getInventory();
        CombinedItemContainer container = inventory.getCombinedBackpackStorageHotbar();
        AutoCraftPlan plan = AutoCraftPlanner.plan(recipe, false, container);
        if (!plan.affordable()) {
            event.setCancelled(true);
            sendError(playerRef, "Not enough resources!");
            return;
        }

        // 7. Consume recipe resources atomically
        //    Always use ItemId-based MaterialQuantities built from the plan's
        //    resolved consumption entries. This ensures the engine uses
        //    isStackableWith/isEquivalentType for matching (which checks metadata),
        //    preventing stencil items from being consumed as raw materials.
        //    ResourceTypeId-based MQs match stencils because stencils share
        //    ResourceTypes with regular items — ItemId-based MQs do not.
        List<MaterialQuantity> consumptionMaterials = new java.util.ArrayList<>();
        for (ConsumptionEntry entry : plan.consumptions()) {
            consumptionMaterials.add(new MaterialQuantity(
                    entry.itemId(), null, null, entry.quantity(), null));
        }
        if (consumptionMaterials.isEmpty()) {
            event.setCancelled(true);
            sendError(playerRef, "Resource consumption failed — empty consumption list");
            return;
        }

        ListTransaction<MaterialTransaction> txn = container.removeMaterials(consumptionMaterials, true, true, true);
        if (!txn.succeeded()) {
            event.setCancelled(true);
            sendError(playerRef, "Resource consumption failed");
            return;
        }

        // 8. Success — DO NOT cancel the event.
        //    Engine will natively:
        //    a) Consume 1 from the stencil stack (2 → 1)
        //    b) Place the block with correct rotation, preview, and connected block rules
        //    c) StencilSyncSystem restores stencil back to qty 2 on inventory change
        if (playerRef != null) {
            if (plan.requiresAutoCraft()) {
                DebugLogger.chat(playerRef, STENCIL, "§a[Stencil] Placed " + outputBlockTypeId + " (auto-crafted from raw materials)");
            } else {
                DebugLogger.chat(playerRef, STENCIL, "§a[Stencil] Placed " + outputBlockTypeId);
            }
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Nullable
    private static String resolveOutputBlockTypeId(@Nonnull CraftingRecipe recipe) {
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null || primaryOutput.getItemId() == null) return null;
        Item outputItem = Item.getAssetMap().getAsset(primaryOutput.getItemId());
        if (outputItem == null) return null;
        return outputItem.getBlockId();
    }

    private static void sendError(@Nullable PlayerRef playerRef, String detail) {
        if (playerRef != null) {
            DebugLogger.chat(playerRef, STENCIL, "§c[Stencil] " + detail);
        }
    }

}
