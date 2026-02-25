package com.UnobstructedThirdPerson.command.UnobstructedCamera;

import com.UnobstructedThirdPerson.command.UnobstructedCamera.SubCommands.StartCommand;
import com.UnobstructedThirdPerson.command.UnobstructedCamera.SubCommands.StopCommand;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class UnobstructedCameraCommand extends AbstractCommandCollection {

    public UnobstructedCameraCommand() {
        super("UnobstructedCamera", "Access to the Unobstructed Third Person Camera commands");
        this.setPermissionGroup(GameMode.Adventure);
        this.addAliases("NoClipCamera", "UCamera", "UC");
        this.addSubCommand(new StartCommand());
        this.addSubCommand(new StopCommand());
    }
}
