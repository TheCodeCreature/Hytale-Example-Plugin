package com.example.exampleplugin.camera;

import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MmoCameraSettingsFactoryTest {

    @Test
    void create_buildsExpectedMmoCameraSettings() throws Exception {
        Class<?> factoryClass = Class.forName("com.example.exampleplugin.camera.MmoCameraSettingsFactory");
        Object result = factoryClass.getMethod("create").invoke(null);

        assertNotNull(result, "Factory must return a settings instance");
        assertInstanceOf(ServerCameraSettings.class, result, "Factory must return ServerCameraSettings");

        ServerCameraSettings settings = (ServerCameraSettings) result;

        assertFalse(settings.isFirstPerson, "MMO camera must be third-person");
        assertEquals(5.0F, settings.distance, 0.0001F, "MMO camera distance should default to 5");
        assertTrue(settings.allowPitchControls, "MMO camera should allow pitch controls");

        assertEquals(AttachedToType.LocalPlayer, settings.attachedToType, "Camera must be attached to the local player");
        assertEquals(PositionType.AttachedToPlusOffset, settings.positionType, "Camera position must follow player position");
        assertEquals(PositionDistanceOffsetType.DistanceOffset, settings.positionDistanceOffsetType, "Camera should use distance offset");

        assertEquals(RotationType.Custom, settings.rotationType, "Camera rotation must be custom (orbit)");
        assertEquals(ApplyLookType.Rotation, settings.applyLookType, "Look should apply to camera rotation, not player look orientation");

        assertEquals(MovementForceRotationType.CameraRotation, settings.movementForceRotationType,
                "Option A movement should be relative to camera rotation");
    }

    @Test
    void createPacket_buildsExpectedSetServerCameraPacket() throws Exception {
        Class<?> factoryClass = Class.forName("com.example.exampleplugin.camera.MmoCameraSettingsFactory");
        Object result = factoryClass.getMethod("createPacket").invoke(null);

        assertNotNull(result, "Factory must return a packet instance");

        // Avoid compile-time dependency on packet classes beyond what's already on classpath.
        assertEquals("com.hypixel.hytale.protocol.packets.camera.SetServerCamera", result.getClass().getName());

        Object view = result.getClass().getField("clientCameraView").get(result);
        Object locked = result.getClass().getField("isLocked").get(result);
        Object cameraSettings = result.getClass().getField("cameraSettings").get(result);

        assertEquals(ClientCameraView.Custom, view, "MMO camera must use Custom camera view");
        assertEquals(Boolean.TRUE, locked, "MMO camera should be locked (server-controlled)");
        assertNotNull(cameraSettings, "MMO camera packet must include settings");
    }
}
