package com.CodeCreature.stencil;

import com.CodeCreature.crafting.AutoCraftPlan;
import com.CodeCreature.crafting.AutoCraftPlanner;
import com.CodeCreature.crafting.ConsumptionEntry;
import com.CodeCreature.crafting.PlaceBlockCostUtil;
import com.CodeCreature.crafting.RecipeTreeResolver;
import com.CodeCreature.scaling.BenchCategory;
import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger("StencilPlacementSystem");

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
        AutoCraftPlan plan = AutoCraftPlanner.plan(recipe, BenchCategory.BUILDERS_ONLY, container);
        if (!plan.affordable()) {
            event.setCancelled(true);
            sendError(playerRef, "Not enough resources!");
            return;
        }

        // 7. Consume recipe resources atomically
        List<MaterialQuantity> consumptionMaterials;
        if (!plan.requiresAutoCraft()) {
            // Fast path: use original recipe materials directly —
            // engine handles ResourceTypeId matching natively
            consumptionMaterials = materials;
        } else {
            // Slow path: build from auto-craft plan consumption entries
            consumptionMaterials = buildConsumptionMaterials(plan, recipe);
            if (consumptionMaterials == null) {
                event.setCancelled(true);
                sendError(playerRef, "Resource consumption failed — cannot resolve materials");
                return;
            }
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
                playerRef.sendMessage(Message.raw("§a[Stencil] Placed " + outputBlockTypeId + " (auto-crafted from raw materials)"));
            } else {
                playerRef.sendMessage(Message.raw("§a[Stencil] Placed " + outputBlockTypeId));
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
            playerRef.sendMessage(Message.raw("§c[Stencil] " + detail));
        }
    }

    /**
     * Converts an {@link AutoCraftPlan}'s consumption entries to
     * {@link MaterialQuantity} instances for {@code removeMaterials()}.
     *
     * <p>For each consumption entry, finds a matching {@link MaterialQuantity}
     * in the recipe's inputs (or sub-recipe inputs) to clone with the
     * required quantity.
     *
     * @param plan   the auto-craft plan with consumption entries
     * @param recipe the original recipe (for finding cloneable MaterialQuantity instances)
     * @return list of MaterialQuantity for removeMaterials, or null if any entry can't be resolved
     */
    @Nullable
    private static List<MaterialQuantity> buildConsumptionMaterials(
            @Nonnull AutoCraftPlan plan, @Nonnull CraftingRecipe recipe) {
        List<MaterialQuantity> result = new java.util.ArrayList<>();
        MaterialQuantity[] inputs = recipe.getInput();

        for (ConsumptionEntry entry : plan.consumptions()) {
            MaterialQuantity template = findTemplate(entry.itemId(), inputs);
            if (template == null) {
                // Try finding from sub-recipes (auto-crafted raw materials)
                template = findTemplateFromSubRecipes(entry.itemId());
            }
            if (template == null) {
                LOGGER.warning("[StencilPlacement] Cannot find MaterialQuantity template for: " + entry.itemId());
                return null;
            }
            result.add(template.clone(entry.quantity()));
        }
        return result;
    }

    @Nullable
    private static MaterialQuantity findTemplate(@Nonnull String itemId, @Nullable MaterialQuantity[] inputs) {
        if (inputs == null) return null;
        for (MaterialQuantity mq : inputs) {
            if (mq == null) continue;
            // Direct itemId match
            if (itemId.equals(mq.getItemId())) return mq;
            // ResourceTypeId resolution match — raw materials in recipes
            // are often specified via ResourceTypeId (e.g., "Rock_Stone")
            // rather than direct itemId
            if (mq.getResourceTypeId() != null) {
                String resolved = ResourceTypeResolver.resolveInputItemId(mq, BenchCategory.BUILDERS_ONLY);
                if (resolved != null) {
                    resolved = NaturalResourceRegistry.resolveToGatherableForm(resolved);
                    if (itemId.equals(resolved)) return mq;
                }
            }
        }
        return null;
    }

    @Nullable
    private static MaterialQuantity findTemplateFromSubRecipes(@Nonnull String itemId) {
        // Search through all recipes for a MaterialQuantity that references this item
        CraftingRecipe subRecipe = RecipeTreeResolver.findRecipeFor(itemId);
        if (subRecipe != null) {
            MaterialQuantity[] subInputs = subRecipe.getInput();
            MaterialQuantity template = findTemplate(itemId, subInputs);
            if (template != null) return template;
        }
        // Try finding any recipe that uses this item as input
        for (CraftingRecipe r : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (r == null) continue;
            MaterialQuantity template = findTemplate(itemId, r.getInput());
            if (template != null) return template;
        }
        return null;
    }
}
