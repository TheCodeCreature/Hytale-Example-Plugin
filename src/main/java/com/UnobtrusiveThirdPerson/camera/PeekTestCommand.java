package com.UnobtrusiveThirdPerson.camera;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class PeekTestCommand extends CommandBase {

    public PeekTestCommand() {
        super("peekTest", "Toggles a transparent peek area (forward mode - target block).");
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
            TransparentAreaCommand.applyWithMode(store, ref, ctx, PeekMode.FORWARD);
        } else {
            store.getExternalData().getWorld().execute(() -> 
                TransparentAreaCommand.applyWithMode(store, ref, ctx, PeekMode.FORWARD)
            );
        }
    }
}
