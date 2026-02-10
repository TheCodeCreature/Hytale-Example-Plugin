package com.UnobstructedThirdPerson.debug;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class CameraDebugCommand extends AbstractCommandCollection {

    public CameraDebugCommand() {
        super("cameradebug", "Camera debug commands");
        this.addSubCommand(new CameraDebugOnCommand());
        this.addSubCommand(new CameraDebugOffCommand());
        this.addSubCommand(new CameraDebugDumpCommand());
        this.addSubCommand(new CameraDebugApplyCommand());
        this.addSubCommand(new CameraDebugReloadCommand());
        this.addSubCommand(new CameraDebugTestCommand());
    }
}
