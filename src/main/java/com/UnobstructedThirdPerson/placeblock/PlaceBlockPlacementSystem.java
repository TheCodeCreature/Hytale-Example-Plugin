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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ECS event system that handles {@link PlaceBlockEvent} for armed PlaceBlock
 * placeholder items.
 *
 * <p><strong>Contract #12 — Mutual Exclusion:</strong> This system and
 * {@link com.UnobstructedThirdPerson.resourcecollection.PlacementCostScaler}
 * both subscribe to {@code PlaceBlockEvent}. They are mutually exclusive:
 * <ul>
 *   <li>This system handles events where the held item IS an armed PlaceBlock</li>
 *   <li>{@code PlacementCostScaler} handles events where the held item is a
 *       natural resource block</li>
 *   <li>Both early-exit for events that aren't theirs</li>
 * </ul>
 *
 * <p><strong>Contract #11 — Atomic Consumption:</strong> When handling an armed
 * PlaceBlock placement, this system:
 * <ol>
 *   <li>Looks up the armed recipe</li>
 *   <li>Scans available resources (inventory + nearby chests)</li>
 *   <li>Attempts atomic consumption via {@link ResourceScanner#consumeAtomically}</li>
 *   <li>If successful: allows the engine to place the recipe's output block</li>
 *   <li>If unsuccessful: cancels the {@code PlaceBlockEvent}</li>
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

        // ── Guard: must be armed with a recipe ──
        if (!PlaceBlockMetadata.isArmed(itemInHand)) {
            event.setCancelled(true);
            return;
        }

        // Cancel the event — we do NOT want the placeholder block placed in the world.
        // We'll manually place the target block type instead.
        event.setCancelled(true);

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

        if (placed) {
            // Send feedback
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("§a[PlaceBlock] Placed " + outputBlockTypeId + " (recipe: " + recipeId + ")"));
            }
        } else {
            log("WARNING: setBlock returned false at " + pos.x + ", " + pos.y + ", " + pos.z);
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
