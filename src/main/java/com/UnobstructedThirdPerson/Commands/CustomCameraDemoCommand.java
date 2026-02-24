package com.UnobstructedThirdPerson.Commands;

import com.UnobstructedThirdPerson.camera.CustomCameraDemo;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/**
 * Custom camera demo command that allows setting camera pitch and yaw rotation.
 * 
 * Usage:
 *   /CustomCameraDemo
 *   /CustomCameraDemo pitch:-45
 *   /CustomCameraDemo yaw:90
 *   /CustomCameraDemo pitch:-30 yaw:45
 */
public class CustomCameraDemoCommand extends AbstractPlayerCommand {
    private final OptionalArg<Double> pitchArg;
    private final OptionalArg<Double> yawArg;
    
    private static final double DEFAULT_PITCH_DEGREES = -90.0;
    private static final double DEFAULT_YAW_DEGREES = 0.0;

    public CustomCameraDemoCommand() {
        super("CustomCameraDemo", "Activates camera demo with custom pitch and yaw rotation");
        
        this.pitchArg = withOptionalArg("pitch", "Camera pitch in degrees (vertical tilt, -90 to 90)", ArgTypes.DOUBLE)
            .addValidator(Validators.min(-90.0))
            .addValidator(Validators.max(90.0));
        
        this.yawArg = withOptionalArg("yaw", "Camera yaw in degrees (horizontal rotation, -180 to 180)", ArgTypes.DOUBLE)
            .addValidator(Validators.min(-180.0))
            .addValidator(Validators.max(180.0));
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, 
                          @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        
        double pitchDegrees = this.pitchArg.provided(context) ? this.pitchArg.get(context) : DEFAULT_PITCH_DEGREES;
        double yawDegrees = this.yawArg.provided(context) ? this.yawArg.get(context) : DEFAULT_YAW_DEGREES;
        
        float pitchRadians = (float) Math.toRadians(pitchDegrees);
        float yawRadians = (float) Math.toRadians(yawDegrees);
        
        CustomCameraDemo cameraDemo = new CustomCameraDemo(yawRadians, pitchRadians);
        cameraDemo.activate();
        
        playerRef.sendMessage(Message.raw(
            String.format("§aCustom camera demo activated! Pitch: §f%.1f° §a| Yaw: §f%.1f°", 
                pitchDegrees, yawDegrees)
        ));
    }
}
