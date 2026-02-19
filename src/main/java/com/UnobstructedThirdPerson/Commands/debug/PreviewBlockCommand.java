package com.UnobstructedThirdPerson.Commands.debug;

import com.UnobstructedThirdPerson.camera.TransparentBlockUtils;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
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
import com.UnobstructedThirdPerson.camera.BlockSnapshot;
import org.jspecify.annotations.NonNull;

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

        LOGGER.info("[PreviewBlock] Replacing block at " + target.x + "," + target.y + "," + target.z +
            " with Debug_Cube (id=" + debugCubeId + ") client-side");

        // Set the block to the real Debug_Cube ID — client already has its textures
        playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
            target.x, target.y, target.z,
            debugCubeId, snapshot.filler(), snapshot.rotation()
        ));

        String blockName = baseType.getId();
        playerRef.sendMessage(Message.raw(
            "§aPreview block placed at §f" + target.x + ", " + target.y + ", " + target.z +
            " §a(§f" + blockName + " §a-> §fDebug_Cube§a). Restoring in 5s..."
        ));
        LOGGER.info("[PreviewBlock] Player " + playerRef.getUsername() +
            " replaced " + blockName + " (id=" + baseId + ") with Debug_Cube (id=" + debugCubeId +
            ") at " + target.x + "," + target.y + "," + target.z);

        // Schedule restoration of the original block after the delay
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> {
                    playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
                        snapshot.x(), snapshot.y(), snapshot.z(),
                        snapshot.blockId(), snapshot.filler(), snapshot.rotation()
                    ));
                    LOGGER.info("[PreviewBlock] Restored block at " +
                        snapshot.x() + "," + snapshot.y() + "," + snapshot.z());
                });
            } catch (Exception e) {
                LOGGER.warning("[PreviewBlock] Failed to restore block: " + e.getMessage());
            }
        }, RESTORE_DELAY_MS, TimeUnit.MILLISECONDS);
    }
}
