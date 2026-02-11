package com.UnobstructedThirdPerson.Commands.UnobstructedCamera;

import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands.DebugTargetCommand;
import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands.StartUnobstructedCameraCommand;
import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.SubCommands.ResetCameraCommand;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class UnobstructedCameraCommand extends AbstractCommandCollection {

    public UnobstructedCameraCommand() {
        super("UnobstructedCamera", "Access to the Unobstructed Third Person Camera commands");
        this.setPermissionGroup(GameMode.Adventure);
        this.addAliases("NoClipCamera");
//        this.addUsageVariant(new WhoAmICommand.WhoAmIOtherCommand());
        this.addSubCommand(new StartUnobstructedCameraCommand());
        this.addSubCommand(new ResetCameraCommand());
        this.addSubCommand(new DebugTargetCommand());
    }

//    @Override
//    protected @Nullable CompletableFuture<Void> execute(@NonNull CommandContext commandContext) {
//        return null;
//    }
}
