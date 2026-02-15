package com.UnobstructedThirdPerson.Commands.UnobstructedCamera.Settings;

import com.hypixel.hytale.protocol.*;

public class CustomCameraSettings extends CameraSettings {
    public final ServerCameraSettings Settings;
    public CustomCameraSettings(){
        Settings = DefaultCameraSettings();
    }

    public ServerCameraSettings DefaultCameraSettings(){
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.isFirstPerson = false;
        settings.positionLerpSpeed = 0.9f;
        settings.rotationLerpSpeed = 0.9f;
        settings.distance = 3f;
        settings.eyeOffset = true;
        settings.displayReticle = true;
        settings.positionOffset = new Position(0f,1f,0f);
//        settings.sendMouseMotion = true;  // Allow client to control camera rotation
//        settings.mouseInputType = MouseInputType.LookAtTargetEntity;
//        settings.mouseInputTargetType = MouseInputTargetType.Any;
//        settings.applyLookType = ApplyLookType.LocalPlayerLookOrientation;
//        settings.applyMovementType = ApplyMovementType.CharacterController;
//        settings.rotationType = RotationType.AttachedToPlusOffset;
//        settings.positionType = PositionType.AttachedToPlusOffset;
        // settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;

        return settings;
    }
}
