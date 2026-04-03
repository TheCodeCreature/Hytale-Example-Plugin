package com.UnobstructedThirdPerson.command.debug.SubCommands;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v1.placeholder.TransparentBlockUtils;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockTextures;
import com.hypixel.hytale.protocol.DrawType;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Renders a client-side textured cube around the player for debugging.
 * Uses a temporary fake block type with Rock_Stone texture.
 *
 * Usage: /Debug DebugTexturedCube
 */
public class DebugTexturedCubeSubCommand extends AbstractPlayerCommand {

    private static final Logger LOGGER = Logger.getLogger(DebugTexturedCubeSubCommand.class.getName());
    private static final String ROCK_STONE_TEXTURE = "BlockTextures/Rock_Stone.png";
    private static final String BASE_BLOCK_ID = "Placeholder_Full";
    private static final int HALF_EXTENT = 2;
    private static final long RESTORE_DELAY_MS = 30_000L;

    public DebugTexturedCubeSubCommand() {
        super("DebugTexturedCube", "Spawns a textured debug cube around you (client-side)");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        BlockType baseType = BlockType.getAssetMap().getAsset(BASE_BLOCK_ID);
        if (baseType == null) {
            baseType = TransparentBlockUtils.findAnyBlockType();
        }
        if (baseType == null) {
            playerRef.sendMessage(Message.raw("Could not resolve a base block type for debug cube."));
            return;
        }

        int fakeBlockId = TransparentBlockUtils.allocateFakeId();
        sendRockStoneType(playerRef, baseType, fakeBlockId);

        Vector3d position = playerRef.getTransform().getPosition();
        int centerX = (int) Math.floor(position.x);
        int centerY = (int) Math.floor(position.y);
        int centerZ = (int) Math.floor(position.z);

        List<BlockSnapshot> snapshots = new ArrayList<>();
        int placedCount = 0;
        for (int x = centerX - HALF_EXTENT; x <= centerX + HALF_EXTENT; x++) {
            for (int y = centerY - HALF_EXTENT; y <= centerY + HALF_EXTENT; y++) {
                for (int z = centerZ - HALF_EXTENT; z <= centerZ + HALF_EXTENT; z++) {
                    BlockSnapshot snapshot = TransparentBlockUtils.readBlock(world.getChunkStore(), x, y, z);
                    if (snapshot == null) {
                        continue;
                    }

                    snapshots.add(snapshot);
                    playerRef.getPacketHandler().writeNoCache(
                            new ServerSetBlock(x, y, z, fakeBlockId, snapshot.filler(), snapshot.rotation())
                    );
                    placedCount++;
                }
            }
        }

        if (placedCount <= 0) {
            playerRef.sendMessage(Message.raw("No loaded blocks were available to draw the debug cube."));
            return;
        }

        List<BlockSnapshot> restoreList = new ArrayList<>(snapshots);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> {
                    for (BlockSnapshot snapshot : restoreList) {
                        playerRef.getPacketHandler().writeNoCache(
                                new ServerSetBlock(
                                        snapshot.x(),
                                        snapshot.y(),
                                        snapshot.z(),
                                        snapshot.blockId(),
                                        snapshot.filler(),
                                        snapshot.rotation()
                                )
                        );
                    }
                });
            } catch (Exception e) {
                LOGGER.warning("[DebugTexturedCube] Failed to restore debug cube: " + e.getMessage());
            }
        }, RESTORE_DELAY_MS, TimeUnit.MILLISECONDS);

        playerRef.sendMessage(
                Message.raw(
                        "Debug textured cube created at "
                                + centerX
                                + ", "
                                + centerY
                                + ", "
                                + centerZ
                                + " ("
                                + placedCount
                                + " blocks, texture="
                                + ROCK_STONE_TEXTURE
                                + "). Restores in "
                                + (RESTORE_DELAY_MS / 1000)
                                + "s."
                )
        );
    }

    private void sendRockStoneType(@Nonnull PlayerRef playerRef, @Nonnull BlockType baseType, int fakeBlockId) {
        com.hypixel.hytale.protocol.BlockType packetBlock = new com.hypixel.hytale.protocol.BlockType(baseType.toPacket());
        packetBlock.drawType = DrawType.Cube;
        packetBlock.model = null;
        packetBlock.modelTexture = null;

        BlockTextures textures = new BlockTextures(
                ROCK_STONE_TEXTURE,
                ROCK_STONE_TEXTURE,
                ROCK_STONE_TEXTURE,
                ROCK_STONE_TEXTURE,
                ROCK_STONE_TEXTURE,
                ROCK_STONE_TEXTURE,
                1.0F
        );
        packetBlock.cubeTextures = new BlockTextures[] { textures };
        packetBlock.requiresAlphaBlending = false;

        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = Math.max(BlockType.getAssetMap().getNextIndex(), fakeBlockId + 1);
        Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
        blockTypes.put(fakeBlockId, packetBlock);
        update.blockTypes = blockTypes;
        update.updateBlockTextures = true;
        update.updateModelTextures = true;
        update.updateModels = true;
        update.updateMapGeometry = true;

        playerRef.getPacketHandler().writeNoCache(update);
    }
}
