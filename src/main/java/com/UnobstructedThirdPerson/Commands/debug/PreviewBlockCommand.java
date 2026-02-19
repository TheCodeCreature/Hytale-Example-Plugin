package com.UnobstructedThirdPerson.Commands.debug;

import com.UnobstructedThirdPerson.camera.BlockSnapshot;
import com.UnobstructedThirdPerson.camera.TransparentBlockUtils;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.BlockTextures;
import com.hypixel.hytale.protocol.Opacity;
import com.hypixel.hytale.protocol.ShaderType;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.protocol.packets.world.UpdateBlockDamage;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Test command that turns the targeted block into a transparent "preview" ghost
 * using packet injection. The original block textures are preserved — only
 * opacity and alpha blending are changed so the block looks like a placement preview.
 *
 * Usage: /PreviewBlock
 * The effect is client-side only and auto-restores after 5 seconds.
 */
public class PreviewBlockCommand extends AbstractPlayerCommand {

    private static final Logger LOGGER = Logger.getLogger(PreviewBlockCommand.class.getName());
    private static final double MAX_DISTANCE = 8.0;
    private static final long RESTORE_DELAY_MS = 5000;

    public PreviewBlockCommand() {
        super("PreviewBlock", "Turns the targeted block into a transparent preview ghost (client-side, 5s)");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext, @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {

        // Raycast to find the block the player is looking at
        Vector3i target = TargetUtil.getTargetBlock(ref, MAX_DISTANCE, store);
        if (target == null) {
            playerRef.sendMessage(Message.raw("§cNo block in range."));
            return;
        }

        // Read the actual block data at that position
        ChunkStore chunkStore = world.getChunkStore();
        BlockSnapshot snapshot = TransparentBlockUtils.readBlock(chunkStore, target.x, target.y, target.z);
        if (snapshot == null || snapshot.blockId() == 0) {
            playerRef.sendMessage(Message.raw("§cTarget is air or unloaded."));
            return;
        }

        int baseId = snapshot.blockId();
        BlockType baseType = BlockType.getAssetMap().getAsset(baseId);
        if (baseType == null) {
            playerRef.sendMessage(Message.raw("§cUnknown block type ID: " + baseId));
            return;
        }

        // Use the hardcoded Debug_Cube block type (ID 2) — always available
        int debugCubeId = BlockType.DEBUG_CUBE_ID;
        BlockType debugCubeType = BlockType.DEBUG_CUBE;

        // Save the original Debug_Cube packet so we can restore it later
        com.hypixel.hytale.protocol.BlockType originalDebugPacket = debugCubeType.toPacket();

        // Clone Debug_Cube and override with the original block's textures + a mask
        com.hypixel.hytale.protocol.BlockType basePacket = baseType.toPacket();
        com.hypixel.hytale.protocol.BlockType modifiedPacket = new com.hypixel.hytale.protocol.BlockType(originalDebugPacket);

        // Copy the original block's cube textures so it looks like the target block
        modifiedPacket.cubeTextures = basePacket.cubeTextures;
        modifiedPacket.requiresAlphaBlending = true;
        modifiedPacket.opacity = Opacity.Semitransparent;

        // Use the Ice shader — real ice/glass blocks use this for semi-transparent rendering
        modifiedPacket.shaderEffect = new ShaderType[] { ShaderType.Ice };

        LOGGER.info("[PreviewBlock] Using ShaderType.Ice + Semitransparent" +
            ", base textures from " + baseType.getId());

        // Step 1: Send modified Debug_Cube type definition to the client
        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = BlockType.getAssetMap().getNextIndex();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
        blockTypes.put(debugCubeId, modifiedPacket);
        update.blockTypes = blockTypes;
        update.updateBlockTextures = true;
        update.updateModelTextures = false;
        update.updateModels = false;
        update.updateMapGeometry = true;
        playerRef.getPacketHandler().writeNoCache(update);

        // Step 2: Set the target block to Debug_Cube
        playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
            target.x, target.y, target.z,
            debugCubeId, snapshot.filler(), snapshot.rotation()
        ));

        // Step 3: Apply a damage overlay on top of the Debug_Cube (alpha decal test)
        UpdateBlockDamage damagePacket = new UpdateBlockDamage();
        damagePacket.blockPosition = new BlockPosition(target.x, target.y, target.z);
        damagePacket.damage = 0.5f;
        damagePacket.delta = 0.0f;
        playerRef.getPacketHandler().writeNoCache(damagePacket);

        String blockName = baseType.getId();
        LOGGER.info("[PreviewBlock] Player " + playerRef.getUsername() +
            " previewing " + blockName + " (id=" + baseId + ") as transparent Debug_Cube at " +
            target.x + "," + target.y + "," + target.z);
        playerRef.sendMessage(Message.raw(
            "§aTransparent preview at §f" + target.x + ", " + target.y + ", " + target.z +
            " §a(§f" + blockName + " §a-> §ftransparent Debug_Cube§a). Restoring in 5s..."
        ));

        // Schedule restoration: restore both block type definition and the block itself
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> {
                    // Restore original Debug_Cube type definition
                    UpdateBlockTypes restore = new UpdateBlockTypes();
                    restore.type = UpdateType.AddOrUpdate;
                    restore.maxId = BlockType.getAssetMap().getNextIndex();
                    Map<Integer, com.hypixel.hytale.protocol.BlockType> restoreTypes = new HashMap<>();
                    restoreTypes.put(debugCubeId, originalDebugPacket);
                    restore.blockTypes = restoreTypes;
                    restore.updateBlockTextures = true;
                    restore.updateModelTextures = false;
                    restore.updateModels = false;
                    restore.updateMapGeometry = true;
                    playerRef.getPacketHandler().writeNoCache(restore);

                    // Clear the damage overlay
                    UpdateBlockDamage clearDamage = new UpdateBlockDamage();
                    clearDamage.blockPosition = new BlockPosition(target.x, target.y, target.z);
                    clearDamage.damage = 0.0f;
                    clearDamage.delta = 0.0f;
                    playerRef.getPacketHandler().writeNoCache(clearDamage);

                    // Restore original block at the target position
                    playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                        snapshot.x(), snapshot.y(), snapshot.z(),
                        snapshot.blockId(), snapshot.filler(), snapshot.rotation()
                    ));
                    LOGGER.info("[PreviewBlock] Restored block and Debug_Cube type at " +
                        snapshot.x() + "," + snapshot.y() + "," + snapshot.z());
                });
            } catch (Exception e) {
                LOGGER.warning("[PreviewBlock] Failed to restore: " + e.getMessage());
            }
        }, RESTORE_DELAY_MS, TimeUnit.MILLISECONDS);
    }
}
