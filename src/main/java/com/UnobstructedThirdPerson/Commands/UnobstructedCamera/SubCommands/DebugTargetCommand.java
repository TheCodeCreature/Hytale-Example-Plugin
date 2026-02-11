package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands;

import com.UnobstructedThirdPerson.fix.InteractionPositionFixer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class DebugTargetCommand extends AbstractPlayerCommand {

    public DebugTargetCommand() {
        super("DebugTarget", "Shows client target vs server calculated target positions");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        UUID playerId = playerRef.getUuid();
        
        // Get cached positions
        BlockPosition clientPos = InteractionPositionFixer.getLastClientPosition(playerId);
        Vector3i serverTarget = InteractionPositionFixer.getCachedServerTarget(playerId);
        boolean isEnabled = InteractionPositionFixer.isEnabledForPlayer(playerId);
        boolean isRedirectMode = InteractionPositionFixer.isRedirectMode();
        
        // Build message
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== Interaction Target Debug ===§r\n");
        sb.append("§eEnabled: §f").append(isEnabled).append("\n");
        sb.append("§eRedirect Mode: §f").append(isRedirectMode).append("\n");
        
        if (clientPos != null) {
            sb.append("§eClient Target: §f").append(clientPos.x).append(", ").append(clientPos.y).append(", ").append(clientPos.z).append("\n");
        } else {
            sb.append("§eClient Target: §7(no recent interaction)\n");
        }
        
        if (serverTarget != null) {
            sb.append("§eServer Target: §f").append(serverTarget.x).append(", ").append(serverTarget.y).append(", ").append(serverTarget.z).append("\n");
        } else {
            sb.append("§eServer Target: §7(not calculated)\n");
        }
        
        if (clientPos != null && serverTarget != null) {
            boolean match = clientPos.x == serverTarget.x && clientPos.y == serverTarget.y && clientPos.z == serverTarget.z;
            if (match) {
                sb.append("§aPositions MATCH");
            } else {
                sb.append("§cPositions DIFFER - redirect active");
            }
        }
        
        playerRef.sendMessage(Message.raw(sb.toString()));
    }
}
