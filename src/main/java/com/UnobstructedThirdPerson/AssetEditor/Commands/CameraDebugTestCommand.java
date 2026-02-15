package com.UnobstructedThirdPerson.AssetEditor.Commands;

import com.UnobstructedThirdPerson.camera.CameraSettingsApplier;
import com.UnobstructedThirdPerson.camera.ExtendedCameraSettings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class CameraDebugTestCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> fieldArg;
    private final RequiredArg<String> valueArg;

    public CameraDebugTestCommand() {
        super("test", "Test a specific camera field");
        this.fieldArg = withRequiredArg("field", "Field name (distance, positionLerpSpeed, etc.)", ArgTypes.STRING);
        this.valueArg = withRequiredArg("value", "Field value", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context, @NonNull Store<EntityStore> store, 
                          @NonNull Ref<EntityStore> ref, @NonNull PlayerRef playerRef, @NonNull World world) {
        String field = fieldArg.get(context);
        String value = valueArg.get(context);
        
        ExtendedCameraSettings settings = new ExtendedCameraSettings(CameraSettingsApplier.getSettings());
        
        try {
            switch (field.toLowerCase()) {
                case "distance":
                    settings.setDistance(Float.parseFloat(value));
                    break;
                case "positionlerpspeed":
                    settings.setPositionLerpSpeed(Float.parseFloat(value));
                    break;
                case "rotationlerpspeed":
                    settings.setRotationLerpSpeed(Float.parseFloat(value));
                    break;
                case "isfirstperson":
                    settings.setIsFirstPerson(Boolean.parseBoolean(value));
                    break;
                case "eyeoffset":
                    settings.setEyeOffset(Boolean.parseBoolean(value));
                    break;
                case "displayreticle":
                    settings.setDisplayReticle(Boolean.parseBoolean(value));
                    break;
                case "displaycursor":
                    settings.setDisplayCursor(Boolean.parseBoolean(value));
                    break;
                case "speedmodifier":
                    settings.setSpeedModifier(Float.parseFloat(value));
                    break;
                default:
                    playerRef.sendMessage(Message.translation("Unknown field: " + field));
                    return;
            }
            
            CameraDebugManager.log("Testing " + field + "=" + value + " for " + playerRef.getUsername());
            CameraSettingsApplier.applyToPlayer(playerRef, settings);
            playerRef.sendMessage(Message.translation("Applied test: " + field + "=" + value));
            
        } catch (NumberFormatException e) {
            playerRef.sendMessage(Message.translation("Invalid value: " + value));
        }
    }
}
