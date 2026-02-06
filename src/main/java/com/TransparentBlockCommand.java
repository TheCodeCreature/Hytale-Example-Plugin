package com;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.Opacity;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockTypes;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

public class TransparentBlockCommand extends CommandBase {
    private static final int DEFAULT_RAYCAST_DISTANCE = 30;

    public TransparentBlockCommand() {
        super("transparent", "Makes the targeted block type semitransparent for you.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store.isInThread()) {
            apply(store, ref, ctx);
        } else {
            store.getExternalData().getWorld().execute(() -> apply(store, ref, ctx));
        }
    }

    private static void apply(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull CommandContext ctx) {
        if (!ref.isValid()) {
            ctx.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            ctx.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
            return;
        }

        Vector3i target = TargetUtil.getTargetBlock(ref, DEFAULT_RAYCAST_DISTANCE, store);
        if (target == null) {
            ctx.sendMessage(Message.raw("No block in sight."));
            return;
        }

        World world = store.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(target.x, target.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            ctx.sendMessage(Message.raw("Target chunk is not loaded."));
            return;
        }

        BlockChunk blockChunk = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            ctx.sendMessage(Message.raw("Target chunk is missing block data."));
            return;
        }

        int localX = target.x & 31;
        int localZ = target.z & 31;
        int blockId = blockChunk.getBlock(localX, target.y, localZ);
        if (blockId == 0) {
            ctx.sendMessage(Message.raw("Target block is air."));
            return;
        }

        BlockType asset = BlockType.getAssetMap().getAsset(blockId);
        if (asset == null) {
            ctx.sendMessage(Message.raw("Target block type is unknown."));
            return;
        }

        com.hypixel.hytale.protocol.BlockType packetBlock = new com.hypixel.hytale.protocol.BlockType(asset.toPacket());
        packetBlock.opacity = Opacity.Semitransparent;
        packetBlock.requiresAlphaBlending = true;

        UpdateBlockTypes update = new UpdateBlockTypes();
        update.type = UpdateType.AddOrUpdate;
        update.maxId = BlockType.getAssetMap().getNextIndex();
        Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
        blockTypes.put(blockId, packetBlock);
        update.blockTypes = blockTypes;
        update.updateBlockTextures = true;
        update.updateModelTextures = true;
        update.updateModels = true;
        update.updateMapGeometry = true;

        playerRef.getPacketHandler().writeNoCache(update);
        ctx.sendMessage(Message.raw("Set " + asset.getId() + " to semitransparent."));
    }
}
