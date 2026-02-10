package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.Settings;

import com.hypixel.hytale.protocol.CameraSettings;
import com.hypixel.hytale.protocol.ServerCameraSettings;

public class CustomCameraSettings extends CameraSettings {
    public final ServerCameraSettings Settings;
    public CustomCameraSettings(){
        Settings = DefaultCameraSettings();
    }

    public ServerCameraSettings DefaultCameraSettings(){
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.isFirstPerson = false;
        settings.positionLerpSpeed = 0.5f;
        settings.rotationLerpSpeed = 0.5f;
        settings.distance = 10f;
        settings.eyeOffset = true;
        settings.displayReticle = true;
//        settings.sendMouseMotion = true;  // Allow client to control camera rotation
//        settings.mouseInputType = MouseInputType.LookAtTargetEntity;
//        settings.mouseInputTargetType = MouseInputTargetType.Any;
//        settings.applyLookType = ApplyLookType.LocalPlayerLookOrientation;
//        settings.applyMovementType = ApplyMovementType.CharacterController;
//        settings.rotationType = RotationType.AttachedToPlusOffset;
//        settings.positionType = PositionType.AttachedToPlusOffset;
        return settings;
    }
}
