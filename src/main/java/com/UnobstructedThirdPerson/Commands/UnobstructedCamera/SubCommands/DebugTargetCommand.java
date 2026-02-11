package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.fix.InteractionPositionFixer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.logging.Logger;

public class DebugTargetCommand extends AbstractPlayerCommand {

    private static final Logger LOGGER = Logger.getLogger(DebugTargetCommand.class.getName());
    private static final float DEBUG_DURATION = 10.0f;
    private static final float CAMERA_DISTANCE = 6.0f;
    private static final float ARROW_LENGTH = 3.0f;

    // Colors
    private static final Vector3f COLOR_CYAN = new Vector3f(0.137f, 0.867f, 0.882f);
    private static final Vector3f COLOR_RED = new Vector3f(1.0f, 0.2f, 0.2f);
    private static final Vector3f COLOR_GREEN = new Vector3f(0.2f, 1.0f, 0.2f);
    private static final Vector3f COLOR_YELLOW = new Vector3f(1.0f, 1.0f, 0.2f);
    private static final Vector3f COLOR_ORANGE = new Vector3f(1.0f, 0.5f, 0.2f);
    private static final Vector3f COLOR_BLUE = new Vector3f(0.2f, 0.4f, 1.0f);
    private static final Vector3f COLOR_PURPLE = new Vector3f(0.8f, 0.2f, 1.0f);

    public DebugTargetCommand() {
        super("DebugTarget", "Shows client target vs server calculated target positions with visual debug shapes");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        UUID playerId = playerRef.getUuid();
        
        // Get cached positions from InteractionPositionFixer
        BlockPosition clientPos = InteractionPositionFixer.getLastClientPosition(playerId);
        Vector3i serverTarget = InteractionPositionFixer.getCachedServerTarget(playerId);
        boolean isEnabled = InteractionPositionFixer.isEnabledForPlayer(playerId);
        boolean isRedirectMode = InteractionPositionFixer.isRedirectMode();
        
        // Get player transform and head rotation
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
        
        if (transformComponent == null || headRotation == null) {
            playerRef.sendMessage(Message.raw("§cError: Could not get player transform components"));
            return;
        }
        
        // Get player position and eye height
        Vector3d playerPos = transformComponent.getPosition();
        float eyeHeight = 0.0f;
        if (modelComponent != null) {
            eyeHeight = modelComponent.getModel().getEyeHeight(ref, store);
        }
        Vector3d eyePos = new Vector3d(playerPos.x, playerPos.y + eyeHeight, playerPos.z);
        
        // Get look direction from head rotation
        Vector3f headRot = headRotation.getRotation();
        Transform lookTransform = TargetUtil.getLook(ref, store);
        Vector3d lookDir = lookTransform.getDirection();
        
        // Calculate camera position (behind player in 3rd person)
        Vector3d cameraPos = new Vector3d(
            playerPos.x - (lookDir.x * CAMERA_DISTANCE),
            playerPos.y + eyeHeight - (lookDir.y * CAMERA_DISTANCE),
            playerPos.z - (lookDir.z * CAMERA_DISTANCE)
        );
        
        // Calculate body direction from body rotation (yaw only, no pitch)
        Vector3f bodyRot = transformComponent.getTransform().getRotation();
        Vector3d bodyDir = new Vector3d(
            -Math.sin(bodyRot.getYaw()),
            0,
            Math.cos(bodyRot.getYaw())
        );
        
        // === Spawn Debug Shapes ===
        
        // 1. Player position - Cyan sphere at feet
        DebugUtils.addSphere(world, playerPos, COLOR_CYAN, 0.3, DEBUG_DURATION);
        
        // 2. Eye position - Small cyan sphere
        DebugUtils.addSphere(world, eyePos, COLOR_CYAN, 0.15, DEBUG_DURATION);
        
        // 3. Camera position - Yellow sphere
        DebugUtils.addSphere(world, cameraPos, COLOR_YELLOW, 0.25, DEBUG_DURATION);
        
        // 4. Client target block - Red cube
        if (clientPos != null) {
            Vector3d clientBlockCenter = new Vector3d(clientPos.x + 0.5, clientPos.y + 0.5, clientPos.z + 0.5);
            DebugUtils.addCube(world, clientBlockCenter, COLOR_RED, 1.05, DEBUG_DURATION);
        }
        
        // 5. Server target block - Green cube
        if (serverTarget != null) {
            Vector3d serverBlockCenter = new Vector3d(serverTarget.x + 0.5, serverTarget.y + 0.5, serverTarget.z + 0.5);
            DebugUtils.addCube(world, serverBlockCenter, COLOR_GREEN, 1.0, DEBUG_DURATION);
        }
        
        // 6. Look raycast - Orange arrow from eye position
        Vector3d lookArrowDir = new Vector3d(lookDir.x * ARROW_LENGTH, lookDir.y * ARROW_LENGTH, lookDir.z * ARROW_LENGTH);
        DebugUtils.addArrow(world, eyePos, lookArrowDir, COLOR_ORANGE, DEBUG_DURATION, true);
        
        // 7. Camera to server target - Yellow arrow
        if (serverTarget != null) {
            Vector3d targetCenter = new Vector3d(serverTarget.x + 0.5, serverTarget.y + 0.5, serverTarget.z + 0.5);
            Vector3d cameraToTarget = new Vector3d(
                targetCenter.x - cameraPos.x,
                targetCenter.y - cameraPos.y,
                targetCenter.z - cameraPos.z
            );
            DebugUtils.addArrow(world, cameraPos, cameraToTarget, COLOR_YELLOW, DEBUG_DURATION, true);
        }
        
        // 8. Body rotation - Blue arrow
        Vector3d bodyArrowDir = new Vector3d(bodyDir.x * ARROW_LENGTH, bodyDir.y * ARROW_LENGTH, bodyDir.z * ARROW_LENGTH);
        DebugUtils.addArrow(world, playerPos, bodyArrowDir, COLOR_BLUE, DEBUG_DURATION, true);
        
        // 9. Head rotation - Purple arrow from eye position (different angle to show difference)
        Vector3d headArrowDir = new Vector3d(lookDir.x * (ARROW_LENGTH * 0.8), lookDir.y * (ARROW_LENGTH * 0.8), lookDir.z * (ARROW_LENGTH * 0.8));
        DebugUtils.addArrow(world, eyePos, headArrowDir, COLOR_PURPLE, DEBUG_DURATION, true);
        
        // === Build Chat Message and Log Output ===
        StringBuilder sb = new StringBuilder();
        StringBuilder logSb = new StringBuilder();
        
        sb.append("§6=== Interaction Target Debug ===§r\n");
        logSb.append("[DebugTarget] Player: ").append(playerRef.getUsername()).append("\n");
        
        sb.append("§eEnabled: §f").append(isEnabled).append("\n");
        logSb.append("  Enabled: ").append(isEnabled).append("\n");
        
        sb.append("§eRedirect Mode: §f").append(isRedirectMode).append("\n");
        logSb.append("  Redirect Mode: ").append(isRedirectMode).append("\n");
        
        sb.append("§ePlayer Pos: §f").append(fmtPos(playerPos)).append("\n");
        logSb.append("  Player Pos: ").append(fmtPos(playerPos)).append("\n");
        
        sb.append("§eCamera Pos: §f").append(fmtPos(cameraPos)).append("\n");
        logSb.append("  Camera Pos: ").append(fmtPos(cameraPos)).append("\n");
        
        sb.append("§eEye Pos: §f").append(fmtPos(eyePos)).append("\n");
        logSb.append("  Eye Pos: ").append(fmtPos(eyePos)).append("\n");
        
        sb.append("§eLook Dir: §f").append(fmtPos(lookDir)).append("\n");
        logSb.append("  Look Dir: ").append(fmtPos(lookDir)).append("\n");
        
        sb.append("§eBody Dir: §f").append(fmtPos(bodyDir)).append("\n");
        logSb.append("  Body Dir: ").append(fmtPos(bodyDir)).append("\n");
        
        if (clientPos != null) {
            sb.append("§cClient Target: §f").append(clientPos.x).append(", ").append(clientPos.y).append(", ").append(clientPos.z).append("\n");
            logSb.append("  Client Target: ").append(clientPos.x).append(", ").append(clientPos.y).append(", ").append(clientPos.z).append("\n");
        } else {
            sb.append("§cClient Target: §7(no recent interaction)\n");
            logSb.append("  Client Target: (no recent interaction)\n");
        }
        
        if (serverTarget != null) {
            sb.append("§aServer Target: §f").append(serverTarget.x).append(", ").append(serverTarget.y).append(", ").append(serverTarget.z).append("\n");
            logSb.append("  Server Target: ").append(serverTarget.x).append(", ").append(serverTarget.y).append(", ").append(serverTarget.z).append("\n");
        } else {
            sb.append("§aServer Target: §7(not calculated)\n");
            logSb.append("  Server Target: (not calculated)\n");
        }
        
        if (clientPos != null && serverTarget != null) {
            boolean match = clientPos.x == serverTarget.x && clientPos.y == serverTarget.y && clientPos.z == serverTarget.z;
            if (match) {
                sb.append("§aPositions MATCH\n");
                logSb.append("  Status: POSITIONS MATCH\n");
            } else {
                sb.append("§cPositions DIFFER - redirect active\n");
                logSb.append("  Status: POSITIONS DIFFER - redirect active\n");
            }
        }
        
        sb.append("§7Debug shapes spawned for ").append((int)DEBUG_DURATION).append(" seconds");
        logSb.append("  Debug shapes spawned for ").append((int)DEBUG_DURATION).append(" seconds");
        
        // Output to log
        LOGGER.info(logSb.toString());
        
        // Output to chat
        playerRef.sendMessage(Message.raw(sb.toString()));
    }
    
    private static String fmtPos(Vector3d pos) {
        return String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z);
    }
}
