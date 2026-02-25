package com.UnobstructedThirdPerson.command.debug.SubCommands;

import com.UnobstructedThirdPerson.shape.placeholder.PlaceholderBlockManager;
import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.placeholder.TransparentBlockUtils;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockTextures;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.protocol.ModelTexture;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
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
public class PreviewBlockSubCommand extends AbstractPlayerCommand {

    private static final Logger LOGGER = Logger.getLogger(PreviewBlockSubCommand.class.getName());
    private static final double MAX_DISTANCE = 8.0;
    private static final long RESTORE_DELAY_MS = 5000;

    public PreviewBlockSubCommand() {
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

        // Get the appropriate placeholder based on the target block's hitbox type
        String hitboxType = baseType.getHitboxType();
        String placeholderId = PlaceholderBlockManager.getPlaceholderForHitbox(hitboxType);
        
        BlockType placeholderType = BlockType.getAssetMap().getAsset(placeholderId);
        if (placeholderType == null) {
            playerRef.sendMessage(Message.raw("§cPlaceholder block " + placeholderId + " not found!"));
            LOGGER.severe("[PreviewBlock] Placeholder " + placeholderId + " not loaded for hitbox " + hitboxType);
            return;
        }
        int placeholderNumericId = BlockType.getAssetMap().getIndex(placeholderId);

        // Save the original placeholder packet so we can restore it later
        com.hypixel.hytale.protocol.BlockType originalPlaceholderPacket = placeholderType.toPacket();

        // Clone the TARGET block's full packet (preserves drawType, model, hitbox, etc.)
        com.hypixel.hytale.protocol.BlockType basePacket = baseType.toPacket();
        com.hypixel.hytale.protocol.BlockType modifiedPacket = new com.hypixel.hytale.protocol.BlockType(basePacket);

        // Override textures to Editor_Empty based on drawType
        String editorEmptyTexture = "BlockTextures/Editor_Empty.png";
        DrawType drawType = basePacket.drawType;

        // For cube blocks, set all 6 cube face textures to Editor_Empty
        BlockTextures emptyTextures = new BlockTextures(
            editorEmptyTexture, // top
            editorEmptyTexture, // bottom
            editorEmptyTexture, // front
            editorEmptyTexture, // back
            editorEmptyTexture, // left
            editorEmptyTexture, // right
            1.0f // weight
        );
        modifiedPacket.cubeTextures = new BlockTextures[] { emptyTextures };
        LOGGER.info("[PreviewBlock] Using Cube drawType with Editor_Empty textures");
        // For model blocks, set model texture to Editor_Empty
        ModelTexture emptyModelTexture = new ModelTexture(editorEmptyTexture, 1.0f);
        modifiedPacket.modelTexture = new ModelTexture[] { emptyModelTexture };
        LOGGER.info("[PreviewBlock] Using Model drawType with Editor_Empty texture");

        // Preserve the original drawType so geometry renders correctly
        modifiedPacket.drawType = drawType;
        modifiedPacket.requiresAlphaBlending = true;

        LOGGER.info("[PreviewBlock] Preview block with drawType=" + drawType +
            ", original block=" + baseType.getId() + ", hitbox=" + hitboxType + ", placeholder=" + placeholderId);

        // Step 1: Send modified Debug_Cube type definition to the client
        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = BlockType.getAssetMap().getNextIndex();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
        blockTypes.put(placeholderNumericId, modifiedPacket);
        update.blockTypes = blockTypes;
        update.updateBlockTextures = true;
        update.updateModelTextures = true;
        update.updateModels = true;
        update.updateMapGeometry = true;
        playerRef.getPacketHandler().writeNoCache(update);

        // Step 2: Set the target block to the selected placeholder
        playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
            target.x, target.y, target.z,
            placeholderNumericId, snapshot.filler(), snapshot.rotation()
        ));

        String blockName = baseType.getId();
        LOGGER.info("[PreviewBlock] Player " + playerRef.getUsername() +
            " previewing " + blockName + " (id=" + baseId + ") as transparent " + placeholderId + " at " +
            target.x + "," + target.y + "," + target.z);
        playerRef.sendMessage(Message.raw(
            "§aTransparent preview at §f" + target.x + ", " + target.y + ", " + target.z +
            " §a(§f" + blockName + " §a-> §f" + placeholderId + "§a). Restoring in 5s..."
        ));

        // Schedule restoration: restore block type definition and the block itself
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> {
                    // Restore original placeholder block type definition
                    UpdateBlockTypes restore = new UpdateBlockTypes();
                    restore.type = UpdateType.AddOrUpdate;
                    restore.maxId = BlockType.getAssetMap().getNextIndex();
                    Map<Integer, com.hypixel.hytale.protocol.BlockType> restoreTypes = new HashMap<>();
                    restoreTypes.put(placeholderNumericId, originalPlaceholderPacket);
                    restore.blockTypes = restoreTypes;
                    restore.updateBlockTextures = true;
                    restore.updateModelTextures = true;
                    restore.updateModels = true;
                    restore.updateMapGeometry = true;
                    playerRef.getPacketHandler().writeNoCache(restore);

                    // Restore original block at the target position
                    playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                        snapshot.x(), snapshot.y(), snapshot.z(),
                        snapshot.blockId(), snapshot.filler(), snapshot.rotation()
                    ));
                    LOGGER.info("[PreviewBlock] Restored block and placeholder type at " +
                        snapshot.x() + "," + snapshot.y() + "," + snapshot.z());
                });
            } catch (Exception e) {
                LOGGER.warning("[PreviewBlock] Failed to restore: " + e.getMessage());
            }
        }, RESTORE_DELAY_MS, TimeUnit.MILLISECONDS);
    }
}
