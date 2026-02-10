package com;

import com.UnobstructedThirdPerson.Commands.UnobstructedCamera.UnobstructedCameraCommand;
import com.UnobstructedThirdPerson.camera.PeekTestCommand;
import com.UnobstructedThirdPerson.camera.TransparentAreaCommand;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jspecify.annotations.NonNull;

public class UnobstructedThirdPersonPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public UnobstructedThirdPersonPlugin(@NonNull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup(){
        LOGGER.atInfo().log("Hello from %s version %s SETUP", this.getName(), this.getManifest().getVersion().toString());
        this.getCommandRegistry().registerCommand(new UnobstructedCameraCommand());
        this.getCommandRegistry().registerCommand(new PeekTestCommand());
        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName(), this.getManifest().getVersion().toString()));
        this.getCommandRegistry().registerCommand(new TransparentAreaCommand());
    }
}
