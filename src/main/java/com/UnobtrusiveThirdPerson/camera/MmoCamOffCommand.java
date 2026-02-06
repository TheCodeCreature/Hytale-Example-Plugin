package com.UnobtrusiveThirdPerson.camera;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class MmoCamOffCommand extends CommandBase {

    public MmoCamOffCommand() {
        super("off", "Disables MMO-style camera and returns to default.");
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

        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
        ctx.sendMessage(Message.raw("MMO camera disabled."));
    }
}
