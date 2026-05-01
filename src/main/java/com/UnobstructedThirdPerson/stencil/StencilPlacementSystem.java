package com.UnobstructedThirdPerson.stencil;

import com.UnobstructedThirdPerson.placeblock.PlaceBlockCostUtil;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Intercepts {@link PlaceBlockEvent} for blueprint stencil items and redirects
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

        // 5. Per-unit cost
        List<MaterialQuantity> materials = PlaceBlockCostUtil.getPerUnitCost(recipe);
        if (materials.isEmpty()) {
            event.setCancelled(true);
            sendError(playerRef, "Recipe has no input materials: " + recipeId);
            return;
        }

        // 6. Affordability check
        Inventory inventory = player.getInventory();
        ItemContainer container = inventory.getCombinedBackpackStorageHotbar();
        if (!container.canRemoveMaterials(materials)) {
            event.setCancelled(true);
            sendError(playerRef, "Not enough resources!");
            return;
        }

        // 7. Consume recipe resources atomically BEFORE the engine places the block.
        //    The engine will then consume 1 from the stencil stack (2 → 1).
        //    StencilSyncSystem's inventory change listener will restore it to 2.
        ListTransaction<MaterialTransaction> txn = container.removeMaterials(materials, true, true, true);
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
            playerRef.sendMessage(Message.raw("§a[Stencil] Placed " + outputBlockTypeId));
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
            playerRef.sendMessage(Message.raw("§c[Stencil] " + detail));
        }
    }
}
