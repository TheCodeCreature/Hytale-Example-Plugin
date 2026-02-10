package com.UnobstructedThirdPerson.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class CameraDebugOnCommand extends AbstractPlayerCommand {

    public CameraDebugOnCommand() {
        super("on", "Enable camera debug logging");
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, 
                          @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        CameraDebugManager.enableDebug(playerRef);
        playerRef.sendMessage(Message.translation("Camera debug logging enabled"));
    }
}
