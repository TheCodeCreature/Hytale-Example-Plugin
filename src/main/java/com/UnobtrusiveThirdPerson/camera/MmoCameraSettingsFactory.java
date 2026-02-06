package com.UnobtrusiveThirdPerson.camera;

import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;

public final class MmoCameraSettingsFactory {

    private MmoCameraSettingsFactory() {
    }

    public static ServerCameraSettings create() {
        ServerCameraSettings cameraSettings = new ServerCameraSettings();

        cameraSettings.displayReticle = true;

        cameraSettings.isFirstPerson = false;
        cameraSettings.distance = 5.0F;
        cameraSettings.allowPitchControls = true;

        cameraSettings.attachedToType = AttachedToType.LocalPlayer;
        cameraSettings.positionType = PositionType.AttachedToPlusOffset;
        cameraSettings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;

        cameraSettings.rotationType = RotationType.AttachedToPlusOffset;
        cameraSettings.applyLookType = ApplyLookType.LocalPlayerLookOrientation;

        // Keep player facing the camera's look direction.
        cameraSettings.movementForceRotationType = MovementForceRotationType.AttachedToHead;

        return cameraSettings;
    }

    public static SetServerCamera createPacket() {
        return new SetServerCamera(ClientCameraView.Custom, true, create());
    }
}
