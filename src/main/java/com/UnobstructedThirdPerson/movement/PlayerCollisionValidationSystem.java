package com.UnobstructedThirdPerson.movement;

import com.UnobstructedThirdPerson.camera.CameraTransparencyVolume;
import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Validates player movement against real server-side blocks, preventing players
 * from walking through walls even when blocks are made transparent client-side.
 * 
 * This system runs BEFORE PlayerSystems.ProcessPlayerInput to intercept movement
 * updates and validate them against the actual server-side block state (not the
 * transparent client-side representation).
 */
public class PlayerCollisionValidationSystem extends EntityTickingSystem<EntityStore> {
    
    private static final Logger LOGGER = Logger.getLogger("PlayerCollisionValidation");
    
    private static final Query<EntityStore> QUERY = Query.and(
        PlayerInput.getComponentType(),
        PlayerRef.getComponentType(),
        TransformComponent.getComponentType()
    );
    
    // Run BEFORE PlayerSystems.ProcessPlayerInput to intercept movement
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PlayerSystems.ProcessPlayerInput.class)
    );
    
    // Player hitbox dimensions (approximate)
    private static final double PLAYER_WIDTH = 0.6;
    private static final double PLAYER_HEIGHT = 1.8;
    
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
    
    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
    
    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerInput playerInput = archetypeChunk.getComponent(index, PlayerInput.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        
        if (playerInput == null || transform == null || playerRef == null) {
            return;
        }
        
        List<PlayerInput.InputUpdate> queue = playerInput.getMovementUpdateQueue();
        if (queue.isEmpty()) {
            return;
        }
        
        // Get the transparency volume for this player
        CameraTransparencyVolume volume = CameraTransparencyVolume.get(playerRef.getUuid());
        if (volume == null) {
            return; // No transparency active, no need to validate
        }
        
        World world = commandBuffer.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        Vector3d currentPos = transform.getPosition();
        
        // Check each movement update for collisions with real blocks
        for (int i = 0; i < queue.size(); i++) {
            PlayerInput.InputUpdate update = queue.get(i);
            
            Vector3d targetPos = null;
            
            if (update instanceof PlayerInput.AbsoluteMovement abs) {
                targetPos = new Vector3d(abs.getX(), abs.getY(), abs.getZ());
            } else if (update instanceof PlayerInput.RelativeMovement rel) {
                targetPos = new Vector3d(
                    currentPos.x + rel.getX(),
                    currentPos.y + rel.getY(),
                    currentPos.z + rel.getZ()
                );
            }
            
            if (targetPos != null && wouldCollideWithRealBlock(targetPos, volume, chunkStore)) {
                // Cancel this movement by teleporting player back to current position
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                Teleport teleport = Teleport.createExact(
                    currentPos,
                    transform.getRotation(),
                    transform.getRotation()
                ).withoutVelocityReset();
                
                commandBuffer.addComponent(ref, Teleport.getComponentType(), teleport);
                
                // Remove the invalid movement from queue
                queue.remove(i);
                i--;
                
                LOGGER.fine("[CollisionValidation] Prevented player " + playerRef.getUsername() + 
                    " from walking through transparent wall at " + targetPos);
            }
        }
    }
    
    /**
     * Check if the target position would collide with a real server-side block
     * (not the transparent client-side version).
     */
    private boolean wouldCollideWithRealBlock(Vector3d targetPos, CameraTransparencyVolume volume, ChunkStore chunkStore) {
        Map<Long, BlockSnapshot> activeBlocks = volume.getActiveBlocks();
        
        // Check blocks in the player's hitbox at the target position
        int minX = (int) Math.floor(targetPos.x - PLAYER_WIDTH / 2);
        int maxX = (int) Math.ceil(targetPos.x + PLAYER_WIDTH / 2);
        int minY = (int) Math.floor(targetPos.y);
        int maxY = (int) Math.ceil(targetPos.y + PLAYER_HEIGHT);
        int minZ = (int) Math.floor(targetPos.z - PLAYER_WIDTH / 2);
        int maxZ = (int) Math.ceil(targetPos.z + PLAYER_WIDTH / 2);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long packedPos = BlockUtil.packUnchecked(x, y, z);
                    
                    // Check if this position is transparent client-side
                    BlockSnapshot snapshot = activeBlocks.get(packedPos);
                    if (snapshot != null) {
                        // This block is transparent client-side, check if it's solid server-side
                        BlockType blockType = BlockType.getAssetMap().getAsset(snapshot.blockId());
                        
                        if (blockType != null && isSolidBlock(blockType)) {
                            return true; // Would collide with real block
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if a block type is solid (blocks player movement).
     * A block is considered solid if it has collision and is not climbable/passable.
     */
    private boolean isSolidBlock(BlockType blockType) {
        // Air blocks are never solid
        if (blockType.getId() == null || blockType.getId().isEmpty() || blockType.getId().equals("Air")) {
            return false;
        }
        
        // Climbable blocks (ladders, vines) don't block movement
        if (blockType.getMovementSettings() != null && blockType.getMovementSettings().isClimbable()) {
            return false;
        }
        
        // Check if block has a hitbox type (collision)
        // Blocks without a hitbox type typically have no collision
        String hitboxType = blockType.getHitboxType();
        if (hitboxType == null || hitboxType.isEmpty() || hitboxType.equals("None")) {
            return false;
        }
        
        // Default: blocks with hitboxes are solid
        return true;
    }
}
