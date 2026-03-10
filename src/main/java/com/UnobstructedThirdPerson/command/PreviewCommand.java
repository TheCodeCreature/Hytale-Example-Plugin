package com.UnobstructedThirdPerson.command;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.placeholder.TransparentBlockUtils;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.buildertools.BrushOrigin;
import com.hypixel.hytale.protocol.packets.buildertools.BrushShape;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.protocol.packets.interface_.EditorBlocksChange;
import com.hypixel.hytale.protocol.packets.interface_.FluidChange;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.selection.mask.BlockPattern;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PreviewCommand extends AbstractPlayerCommand {

    private static final int DEFAULT_RADIUS = 10;
    private static final double MAX_TARGET_DISTANCE = 64.0;
    private static final int MAX_PREVIEW_BLOCKS = 12_000;
    private static final String FALLBACK_MATERIAL_ID = "Rock_Stone";
    private static final Map<UUID, PendingPreview> PENDING_PREVIEWS = new ConcurrentHashMap<>();

    private final OptionalArg<String> modeArg;

    private static final class PendingPreview {
        private final Vector3i center;
        private final String materialId;

        private PendingPreview(@NonNull Vector3i center, @NonNull String materialId) {
            this.center = center;
            this.materialId = materialId;
        }
    }

    public PreviewCommand() {
        super("preview", "Manage preview blocks");
        this.modeArg = withOptionalArg("Mode", "Mode: sphere/show, confirm, reject", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        String mode = this.modeArg.provided(context) ? this.modeArg.get(context) : "sphere";

        switch (mode.toLowerCase()) {
            case "sphere":
            case "shape":
            case "builder":
            case "show":
                showSpherePreview(store, ref, playerRef, world);
                break;
            case "confirm":
                confirmPreview(store, ref, playerRef);
                break;
            case "clear":
            case "reject":
            case "cancel":
                rejectPreview(playerRef);
                break;
            default:
                playerRef.sendMessage(Message.raw("Unknown mode: " + mode + ". Use sphere, confirm, or reject"));
        }
    }

    private void showSpherePreview(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> ref,
            @NonNull PlayerRef playerRef,
            @NonNull World world) {
        Vector3i target = TargetUtil.getTargetBlock(ref, MAX_TARGET_DISTANCE, store);
        if (target == null) {
            Vector3d origin = playerRef.getTransform().getPosition();
            Vector3d direction = playerRef.getTransform().getDirection();
            int rayEndX = (int) Math.floor(origin.x + (direction.x * MAX_TARGET_DISTANCE));
            int rayEndY = (int) Math.floor(origin.y + (direction.y * MAX_TARGET_DISTANCE));
            int rayEndZ = (int) Math.floor(origin.z + (direction.z * MAX_TARGET_DISTANCE));
            playerRef.sendMessage(
                    Message.raw(
                            "No target block in range ("
                                    + (int) MAX_TARGET_DISTANCE
                                    + " blocks). Ray end ~ "
                                    + rayEndX
                                    + ", "
                                    + rayEndY
                                    + ", "
                                    + rayEndZ
                    )
            );
            return;
        }

        String materialId = resolveKnownMaterialId(resolveTargetMaterialId(world, target));
        int previewBlockId = resolveMaterialBlockId(materialId);
        EditorBlocksChange previewPacket = buildSpherePreviewPacket(target, previewBlockId);
        int previewCount = previewPacket.blocksCount;
        if (previewCount <= 0) {
            playerRef.sendMessage(Message.raw("Failed to build preview blocks at target."));
            return;
        }

        clearEditorPreview(playerRef);
        playerRef.getPacketHandler().writeNoCache(previewPacket);

        PENDING_PREVIEWS.put(
                playerRef.getUuid(),
                new PendingPreview(new Vector3i(target.x, target.y, target.z), materialId)
        );

        playerRef.sendMessage(
                Message.raw(
                        "Preview ready at "
                                + target.x
                                + ", "
                                + target.y
                                + ", "
                                + target.z
                                + " using "
                                + materialId
                                + " ("
                                + previewCount
                                + " ghost blocks). Use /preview confirm or /preview reject."
                )
        );
    }

    private void confirmPreview(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> ref,
            @NonNull PlayerRef playerRef) {
        PendingPreview pending = PENDING_PREVIEWS.remove(playerRef.getUuid());
        if (pending == null) {
            playerRef.sendMessage(Message.raw("No pending preview. Use /preview first."));
            return;
        }

        clearEditorPreview(playerRef);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("Failed to resolve player state for builder tools."));
            return;
        }

        int diameter = Math.max(1, DEFAULT_RADIUS * 2);
        BlockPattern material = BlockPattern.parse(Objects.requireNonNull(pending.materialId));
        Vector3i center = pending.center;

        BuilderToolsPlugin.addToQueue(
                player,
                playerRef,
                (r, state, componentAccessor) -> state.editLine(
                        center.x,
                        center.y,
                        center.z,
                        center.x,
                        center.y,
                        center.z,
                        material,
                        diameter,
                        diameter,
                        0,
                        BrushShape.Sphere,
                        BrushOrigin.Center,
                        1,
                        100,
                        state.getGlobalMask(),
                        componentAccessor
                )
        );

        playerRef.sendMessage(Message.raw("Preview confirmed. Sphere build queued."));
    }

    private void rejectPreview(@NonNull PlayerRef playerRef) {
        PendingPreview pending = PENDING_PREVIEWS.remove(playerRef.getUuid());
        clearEditorPreview(playerRef);
        if (pending != null) {
            playerRef.sendMessage(Message.raw("Preview rejected and cleared."));
            return;
        }

        playerRef.sendMessage(Message.raw("No pending preview. Cleared active preview visuals."));
    }

    @NonNull
    private EditorBlocksChange buildSpherePreviewPacket(
            @NonNull Vector3i center,
            int previewBlockId) {
        List<BlockChange> changes = addSpherePreviewChanges(center, previewBlockId);
        EditorBlocksChange packet = new EditorBlocksChange();
        packet.selection = null;
        packet.blocksChange = changes.toArray(BlockChange[]::new);
        packet.fluidsChange = new FluidChange[0];
        packet.blocksCount = changes.size();
        packet.advancedPreview = true;
        return packet;
    }

    @NonNull
    private List<BlockChange> addSpherePreviewChanges(
            @NonNull Vector3i center,
            int previewBlockId) {
        int radius = DEFAULT_RADIUS;
        double halfWidth = (radius + 0.41F);
        double halfHeight = (radius + 0.41F);
        double widthSq = halfWidth * halfWidth;
        double heightSq = halfHeight * halfHeight;
        List<BlockChange> changes = new ArrayList<>();

        for (int sx = -radius; sx <= radius; sx++) {
            for (int sz = -radius; sz <= radius; sz++) {
                for (int sy = -radius; sy <= radius; sy++) {
                    double outerDist = (sx * sx) / widthSq + (sy * sy) / heightSq + (sz * sz) / widthSq;
                    if (outerDist > 1.0) {
                        continue;
                    }

                    if (changes.size() >= MAX_PREVIEW_BLOCKS) {
                        return changes;
                    }

                    int relativeX = (center.x + sx) - center.x;
                    int relativeY = (center.y + sy) - center.y;
                    int relativeZ = (center.z + sz) - center.z;
                    changes.add(new BlockChange(relativeX, relativeY, relativeZ, previewBlockId, (byte) 0));
                }
            }
        }

        return changes;
    }

    private void clearEditorPreview(@NonNull PlayerRef playerRef) {
        EditorBlocksChange clearPacket = new EditorBlocksChange();
        clearPacket.selection = null;
        clearPacket.blocksChange = new BlockChange[0];
        clearPacket.fluidsChange = new FluidChange[0];
        clearPacket.blocksCount = 0;
        clearPacket.advancedPreview = true;
        playerRef.getPacketHandler().writeNoCache(clearPacket);
    }

    private int resolveMaterialBlockId(@NonNull String materialId) {
        int blockId = BlockType.getAssetMap().getIndex(materialId);
        if (blockId == Integer.MIN_VALUE) {
            blockId = BlockType.getAssetMap().getIndex(FALLBACK_MATERIAL_ID);
        }
        return blockId;
    }

    @NonNull
    private String resolveKnownMaterialId(@NonNull String materialId) {
        int blockId = BlockType.getAssetMap().getIndex(materialId);
        if (blockId == Integer.MIN_VALUE) {
            return FALLBACK_MATERIAL_ID;
        }
        return materialId;
    }

    @NonNull
    private String resolveTargetMaterialId(@NonNull World world, @NonNull Vector3i target) {
        BlockSnapshot snapshot = TransparentBlockUtils.readBlock(world.getChunkStore(), target.x, target.y, target.z);
        if (snapshot == null || snapshot.blockId() == 0) {
            return FALLBACK_MATERIAL_ID;
        }

        BlockType type = BlockType.getAssetMap().getAsset(snapshot.blockId());
        if (type == null || type.getId() == null || type.getId().isEmpty()) {
            return FALLBACK_MATERIAL_ID;
        }

        return type.getId();
    }
}
