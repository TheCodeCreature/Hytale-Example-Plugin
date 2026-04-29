package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.Collector;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Custom interaction that handles placement of armed PlaceBlock placeholder items.
 *
 * <p>Replaces {@link PlaceBlockPlacementSystem} (event interception approach) with
 * a direct interaction handler. When the player right-clicks with an armed placeholder,
 * the engine dispatches directly to this interaction — no {@code PlaceBlockEvent} is
 * involved, no event cancellation is needed, and no slot restoration is required.</p>
 *
 * <p><b>Contract — Atomic Consumption:</b> When handling an armed PlaceBlock placement:</p>
 * <ol>
 *   <li>Validates the placeholder is armed with a recipe</li>
 *   <li>Resolves the recipe and gets material costs via {@link CraftingManager#getInputMaterials}</li>
 *   <li>Checks affordability via {@link ItemContainer#canRemoveMaterials}</li>
 *   <li>Consumes atomically via {@link ItemContainer#removeMaterials} (allOrNothing=true)</li>
 *   <li>Places the recipe's output block type in the world</li>
 *   <li>Sends feedback to the player</li>
 * </ol>
 *
 * <p><b>Inventory sync:</b> Material consumption triggers inventory change events,
 * which {@link PlaceholderSyncSystem} listeners handle to run affordability checks
 * (Green/Red transitions). No explicit suppress/resume is needed.</p>
 *
 * <p><b>Ghost preview:</b> The client renders the block preview based on the item's
 * {@code BlockType} section, independent of this interaction type. The
 * {@link BlockPreviewReskinManager} reskins the preview to match the armed recipe's
 * output block.</p>
 *
 * <p><b>Registration:</b> In plugin {@code setup()}:
 * {@code getCodecRegistry(Interaction.CODEC).register("PlaceBlockTool",
 * PlaceBlockToolInteraction.class, PlaceBlockToolInteraction.CODEC)}</p>
 *
 * <p><b>JSON usage:</b> {@code {"Type": "PlaceBlockTool"}} — no additional fields.</p>
 *
 * <p><b>Threading:</b> Runs on the interaction dispatch thread (same thread as
 * {@code SimpleBlockInteraction.tick0()}).</p>
 */
public class PlaceBlockToolInteraction extends SimpleBlockInteraction {

    /**
     * CODEC extending {@link SimpleBlockInteraction#CODEC}.
     * No additional JSON fields — all metadata (recipe ID, output block type)
     * is read from {@link ItemStack#getMetadata()} via {@link PlaceBlockMetadata}.
     */
    public static final BuilderCodec<PlaceBlockToolInteraction> CODEC =
            BuilderCodec.builder(PlaceBlockToolInteraction.class,
                    PlaceBlockToolInteraction::new, SimpleBlockInteraction.CODEC)
                    .build();

    protected PlaceBlockToolInteraction() {
    }

    /**
     * Programmatic constructor for asset loading.
     *
     * @param id the interaction asset ID (e.g., {@code "*PlaceBlockTool_Default"})
     */
    public PlaceBlockToolInteraction(@Nonnull String id) {
        super(id);
    }

    /**
     * Handles right-click placement of an armed PlaceBlock placeholder.
     *
     * <p>Called by {@link SimpleBlockInteraction#tick0} after the client sends the
     * target block position. The full logic flow:</p>
     * <ol>
     *   <li>Guard: verify {@code itemInHand} is an armed PlaceBlock</li>
     *   <li>Resolve Player and PlayerRef from InteractionContext</li>
     *   <li>Read armed recipe ID and output block type from item metadata</li>
     *   <li>Resolve recipe, compute material costs</li>
     *   <li>Check affordability against combined inventory</li>
     *   <li>Consume materials atomically</li>
     *   <li>Compute adjacent placement position from targetBlock + blockFace</li>
     *   <li>Place the output block type in the world via WorldChunk.setBlock</li>
     *   <li>Send feedback message</li>
     * </ol>
     *
     * <p>Sets {@code context.getState().state} to {@link InteractionState#Finished}
     * on success, {@link InteractionState#Failed} on any failure.</p>
     *
     * @param world           the world instance
     * @param commandBuffer   ECS command buffer for deferred operations
     * @param type            the interaction type (Secondary)
     * @param context         interaction context — provides entity ref, held item, client state
     * @param itemInHand      the player's held ItemStack (snapshot from tick0)
     * @param targetBlock     the block position the player right-clicked ON (not adjacent)
     * @param cooldownHandler cooldown handler for the interaction
     */
    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler) {

        // 1-3. Guards: itemInHand must be an armed PlaceBlock
        if (itemInHand == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!PlaceBlockMetadata.isPlaceBlock(itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!PlaceBlockMetadata.isArmed(itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // 4. Resolve Player and PlayerRef
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // 5. Read metadata
        String recipeId = PlaceBlockMetadata.getArmedRecipeId(itemInHand);
        String outputBlockTypeId = PlaceBlockMetadata.getOutputBlockTypeId(itemInHand);
        if (recipeId == null || outputBlockTypeId == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // 6. Resolve assets
        BlockType targetBlockType = BlockType.getAssetMap().getAsset(outputBlockTypeId);
        int targetBlockId = BlockType.getAssetMap().getIndex(outputBlockTypeId);
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (targetBlockType == null || recipe == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // 7. Get per-unit materials (recipe cost / output quantity)
        List<MaterialQuantity> materials = PlaceBlockCostUtil.getPerUnitCost(recipe);

        // 8. Get container
        Inventory inventory = player.getInventory();
        ItemContainer container = inventory.getCombinedBackpackStorageHotbar();

        // 9. Affordability check
        if (!container.canRemoveMaterials(materials)) {
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("§c[PlaceBlock] Not enough resources for " + outputBlockTypeId));
            }
            context.getState().state = InteractionState.Failed;
            return;
        }

        // 10. Atomic consumption
        ListTransaction<MaterialTransaction> txn = container.removeMaterials(materials, true, true, true);
        if (!txn.succeeded()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // 11. Compute adjacent placement position from targetBlock + blockFace
        InteractionSyncData clientState = context.getClientState();
        BlockFace face = null;
        if (clientState != null && clientState.blockFace != null) {
            face = BlockFace.fromProtocolFace(clientState.blockFace);
        }
        Vector3i pos;
        if (face != null) {
            pos = targetBlock.clone().add(face.getDirection());
        } else {
            // Fallback: place on top of the clicked block
            pos = targetBlock.clone().add(Vector3i.UP);
        }

        // Compute rotation from player's head yaw (FacingPlayer mode).
        // The ghost preview auto-faces the player — we match this server-side.
        // Block front faces TOWARD the player (opposite the player's look direction).
        int rotationIndex = 0;

        // Try multiple paths to get player yaw
        HeadRotation headRot = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRot == null) {
            // Fallback: try via commandBuffer
            headRot = commandBuffer.getComponent(context.getEntity(), HeadRotation.getComponentType());
        }

        if (headRot != null) {
            float playerYawRad = headRot.getRotation().getYaw();
            float playerYawDeg = (float) Math.toDegrees(playerYawRad);
            Rotation blockYaw = Rotation.closestOfDegrees(playerYawDeg);
            rotationIndex = RotationTuple.of(blockYaw, Rotation.None, Rotation.None).index();
            System.out.println("[PlaceBlockTool] HeadRotation yawRad=" + playerYawRad
                    + " yawDeg=" + playerYawDeg
                    + " blockYaw=" + blockYaw + " rotationIndex=" + rotationIndex);
        } else {
            System.out.println("[PlaceBlockTool] WARNING: No rotation source found — using rotationIndex=0"); 
        }

        // 12. Place block
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        WorldChunk worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        boolean placed = worldChunk.setBlock(pos.x, pos.y, pos.z, targetBlockId, targetBlockType, rotationIndex, 0, 6);

        // 13. Send feedback
        if (placed && playerRef != null) {
            playerRef.sendMessage(Message.raw("§a[PlaceBlock] Placed " + outputBlockTypeId));
        }

        // 14. Set state
        context.getState().state = InteractionState.Finished;
    }

    /**
     * Client-side simulation. No-op for this server-only interaction.
     */
    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        // No client-side simulation needed — placement is server-authoritative
    }

    /**
     * Walk the interaction tree for serialization. Returns false (no children).
     */
    @Override
    public boolean walk(@Nonnull Collector collector, @Nonnull InteractionContext context) {
        return false;
    }

    /**
     * Generates the network packet for this interaction type.
     * Uses {@code SimpleBlockInteraction} protocol packet since this is
     * a subclass with no additional client-side fields.
     */
    @Nonnull
    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.SimpleBlockInteraction();
    }
}
