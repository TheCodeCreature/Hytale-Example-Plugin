package com;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class CameraOverrideSetup extends JavaPlugin {

    public CameraOverrideSetup(@NonNullDecl JavaPluginInit init){
        super(init);
    }

    @Override
    protected void setup() {
//        super.setup();

        var registry = getEventRegistry();
    }
}
