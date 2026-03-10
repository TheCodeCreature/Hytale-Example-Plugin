package com.UnobstructedThirdPerson.command;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.placeholder.TransparentBlockUtils;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.function.predicate.BiIntPredicate;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.buildertools.BrushOrigin;
import com.hypixel.hytale.protocol.packets.buildertools.BrushShape;
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

import java.util.Objects;

public class PreviewCommand extends AbstractPlayerCommand {

    private static final int DEFAULT_RADIUS = 10;
    private static final int MAX_TARGET_DISTANCE = 32;
    private static final String FALLBACK_MATERIAL_ID = "Rock_Stone";

    private final OptionalArg<String> modeArg;

    public PreviewCommand() {
        super("preview", "Manage preview blocks");
        this.modeArg = withOptionalArg("Mode", "Mode: sphere or clear", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        String mode = this.modeArg.provided(context) ? this.modeArg.get(context) : "sphere";

        switch (mode.toLowerCase()) {
            case "sphere":
            case "shape":
            case "builder":
                createSphereAtTargetUsingBuilderTools(store, ref, playerRef, world);
                break;
            case "clear":
                playerRef.sendMessage(Message.raw("This command now uses creative builder tools (world edit). Use /undo to revert."));
                break;
            default:
                playerRef.sendMessage(Message.raw("Unknown mode: " + mode + ". Use sphere or clear"));
        }
    }

    private void createSphereAtTargetUsingBuilderTools(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> ref,
            @NonNull PlayerRef playerRef,
            @NonNull World world) {
        Vector3d origin = playerRef.getTransform().getPosition();
        Vector3d direction = playerRef.getTransform().getDirection();
        BiIntPredicate solidBlocksOnly = (blockId, fluidId) -> blockId != 0;
        Vector3i target = TargetUtil.getTargetBlock(
                world,
                solidBlocksOnly,
                origin.x,
                origin.y,
                origin.z,
                direction.x,
                direction.y,
                direction.z,
                MAX_TARGET_DISTANCE
        );
        if (target == null) {
            playerRef.sendMessage(Message.raw("No target block in range."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("Failed to resolve player state for builder tools."));
            return;
        }

        String materialId = resolveTargetMaterialId(world, target);
        BlockPattern material = BlockPattern.parse(Objects.requireNonNull(materialId));
        int diameter = Math.max(1, DEFAULT_RADIUS * 2);

        BuilderToolsPlugin.addToQueue(
                player,
                playerRef,
                (r, state, componentAccessor) -> state.editLine(
                        target.x,
                        target.y,
                        target.z,
                        target.x,
                        target.y,
                        target.z,
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

        playerRef.sendMessage(
                Message.raw(
                        "Queued builder-tools sphere at "
                                + target.x
                                + ", "
                                + target.y
                                + ", "
                                + target.z
                                + " using "
                                + materialId
                                + "."
                )
        );
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
