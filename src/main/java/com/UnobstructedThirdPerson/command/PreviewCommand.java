package com.UnobstructedThirdPerson.command;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.ComposedRegion;
import com.UnobstructedThirdPerson.shape.ShapeCompositor;
import com.UnobstructedThirdPerson.shape.ShapeCompositorPresets;
import com.UnobstructedThirdPerson.shape.placeholder.PlaceholderTransparencyUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.protocol.packets.interface_.EditorBlocksChange;
import com.hypixel.hytale.protocol.packets.interface_.FluidChange;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PreviewCommand extends AbstractPlayerCommand {

    private static final int DEFAULT_RADIUS = 10;
    private static final int MAX_PREVIEW_BLOCKS = 12_000;

    private final OptionalArg<String> modeArg;

    public PreviewCommand() {
        super("preview", "Manage preview blocks");
        this.modeArg = withOptionalArg("Mode", "Mode: shape or clear", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        String mode = this.modeArg.provided(context) ? this.modeArg.get(context) : "shape";

        switch (mode.toLowerCase()) {
            case "shape":
            case "editor":
            case "editorshape":
                sendOneShotEditorShapePreview(playerRef, world);
                break;
            case "clear":
                clearEditorPreview(playerRef);
                break;
            default:
                playerRef.sendMessage(Message.raw("Unknown mode: " + mode + ". Use shape or clear"));
        }
    }

    private void sendOneShotEditorShapePreview(
            @NonNull PlayerRef playerRef,
            @NonNull World world) {
        Vector3d pos = playerRef.getTransform().getPosition();

        Vector3i anchor = new Vector3i(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z)
        );

        ShapeCompositor compositor = new ShapeCompositorPresets(anchor, DEFAULT_RADIUS).Test();
        applyLookRotation(compositor, playerRef);

        ChunkStore chunkStore = world.getChunkStore();
        ComposedRegion region = compositor.compose(chunkStore);

        List<BlockChange> blockChanges = buildPreviewChanges(region, anchor, playerRef);
        if (blockChanges.isEmpty()) {
            playerRef.sendMessage(Message.raw("No preview blocks generated for this shape."));
            return;
        }

        EditorBlocksChange packet = new EditorBlocksChange();
        packet.blocksChange = blockChanges.toArray(BlockChange[]::new);
        packet.fluidsChange = new FluidChange[0];
        packet.blocksCount = blockChanges.size();
        packet.advancedPreview = true;
        packet.selection = null;
        playerRef.getPacketHandler().writeNoCache(packet);

        playerRef.sendMessage(Message.raw("Rendered one-shot editor preview with " + blockChanges.size() + " blocks."));
    }

    private List<BlockChange> buildPreviewChanges(
            @NonNull ComposedRegion region,
            @NonNull Vector3i anchor,
            @NonNull PlayerRef playerRef) {
        List<BlockChange> blockChanges = new ArrayList<>();
        Set<Long> excluded = region.getExcludedPositions();
        Map<Long, Integer> computedBlockIds = region.getComputedBlockIds();

        for (Map.Entry<Long, BlockSnapshot> entry : region.getOriginalBlocks().entrySet()) {
            if (blockChanges.size() >= MAX_PREVIEW_BLOCKS) {
                break;
            }

            Long packedPos = entry.getKey();
            if (excluded.contains(packedPos)) {
                continue;
            }

            Integer replacementId = computedBlockIds.get(packedPos);
            if (replacementId == null || replacementId == 0) {
                continue;
            }

            BlockSnapshot snapshot = entry.getValue();
            replacementId = resolveTransparentPlaceholderId(playerRef, snapshot, replacementId);

            blockChanges.add(new BlockChange(
                    snapshot.x() - anchor.x,
                    snapshot.y() - anchor.y,
                    snapshot.z() - anchor.z,
                    replacementId,
                    snapshot.rotation()
            ));
        }

        return blockChanges;
    }

    private int resolveTransparentPlaceholderId(@NonNull PlayerRef playerRef, @NonNull BlockSnapshot snapshot, int replacementId) {
        BlockType replacementType = BlockType.getAssetMap().getAsset(replacementId);
        if (replacementType == null || replacementType.getId() == null || !replacementType.getId().startsWith("Placeholder_")) {
            return replacementId;
        }

        BlockType baseType = BlockType.getAssetMap().getAsset(snapshot.blockId());
        if (baseType == null) {
            return replacementId;
        }

        String hitboxType = baseType.getHitboxType();
        if (hitboxType == null) {
            return replacementId;
        }

        Integer transparentPlaceholderId = PlaceholderTransparencyUtil.prepareTransparentPlaceholder(
                playerRef,
                snapshot.blockId(),
                hitboxType
        );

        return transparentPlaceholderId != null ? transparentPlaceholderId : replacementId;
    }

    private void applyLookRotation(@NonNull ShapeCompositor compositor, @NonNull PlayerRef playerRef) {
        Vector3d lookDir = playerRef.getTransform().getDirection();
        double cameraYaw = Math.atan2(-lookDir.x, lookDir.z);
        double cameraPitch = Math.asin(lookDir.y);
        compositor.setRotation(cameraYaw, cameraPitch);
    }

    private void clearEditorPreview(@NonNull PlayerRef playerRef) {
        EditorBlocksChange clearPacket = new EditorBlocksChange();
        clearPacket.blocksChange = new BlockChange[0];
        clearPacket.fluidsChange = new FluidChange[0];
        clearPacket.blocksCount = 0;
        clearPacket.advancedPreview = true;
        clearPacket.selection = null;
        playerRef.getPacketHandler().writeNoCache(clearPacket);
        playerRef.sendMessage(Message.raw("Cleared editor preview."));
    }
}
