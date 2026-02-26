package com.UnobstructedThirdPerson.command;

import com.UnobstructedThirdPerson.preview.PreviewBlockManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class PreviewCommand extends AbstractPlayerCommand {
    
    private final OptionalArg<String> modeArg;
    
    public PreviewCommand() {
        super("preview", "Manage preview blocks");
        this.modeArg = withOptionalArg("Mode", "Mode: box, line, or clear", ArgTypes.STRING);
    }
    
    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        String mode = this.modeArg.provided(context) ? this.modeArg.get(context) : "box";
        
        PreviewBlockManager manager = PreviewBlockManager.getOrCreate(playerRef, world);
        
        switch (mode.toLowerCase()) {
            case "box":
                showPreviewBox(playerRef, manager, store, ref);
                break;
            case "line":
                showPreviewLine(playerRef, manager, store, ref);
                break;
            case "clear":
                manager.clearAll();
                playerRef.sendMessage(Message.raw("Cleared all preview blocks"));
                break;
            default:
                playerRef.sendMessage(Message.raw("Unknown mode: " + mode + ". Use box, line, or clear"));
        }
    }
    
    private void showPreviewBox(PlayerRef playerRef, PreviewBlockManager manager, Store<EntityStore> store, Ref<EntityStore> ref) {
        Vector3d pos = getPlayerPosition(store, ref);
        if (pos == null) {
            playerRef.sendMessage(Message.raw("Failed to get player position"));
            return;
        }
        
        Vector3i min = new Vector3i((int)pos.x - 2, (int)pos.y, (int)pos.z - 2);
        Vector3i max = new Vector3i((int)pos.x + 2, (int)pos.y + 3, (int)pos.z + 2);
        
        int rockStoneId = getBlockId("Rock_Stone");
        if (rockStoneId == -1) {
            playerRef.sendMessage(Message.raw("Failed to find Rock_Stone block"));
            return;
        }
        
        manager.addPreviewBoxOutline(min, max, rockStoneId);
        playerRef.sendMessage(Message.raw("Created preview box outline (" + manager.getPreviewCount() + " blocks)"));
    }
    
    private void showPreviewLine(PlayerRef playerRef, PreviewBlockManager manager, Store<EntityStore> store, Ref<EntityStore> ref) {
        Vector3d pos = getPlayerPosition(store, ref);
        if (pos == null) {
            playerRef.sendMessage(Message.raw("Failed to get player position"));
            return;
        }
        
        Vector3i center = new Vector3i((int)pos.x, (int)pos.y, (int)pos.z);
        
        int editorBlockId = getBlockId("Editor_Block");
        if (editorBlockId == -1) {
            playerRef.sendMessage(Message.raw("Failed to find Editor_Block block"));
            return;
        }
        
        // Create a line of preview blocks
        for (int i = 0; i < 10; i++) {
            manager.addPreview(new Vector3i(center.x + i, center.y, center.z), editorBlockId);
        }
        playerRef.sendMessage(Message.raw("Created preview line (" + manager.getPreviewCount() + " blocks)"));
    }
    
    private Vector3d getPlayerPosition(Store<EntityStore> store, Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        return transform != null ? transform.getPosition() : null;
    }
    
    private int getBlockId(String blockName) {
        BlockType blockType = BlockType.getAssetMap().getAsset(blockName);
        if (blockType == null) {
            return -1;
        }
        return BlockType.getAssetMap().getIndex(blockName);
    }
}
