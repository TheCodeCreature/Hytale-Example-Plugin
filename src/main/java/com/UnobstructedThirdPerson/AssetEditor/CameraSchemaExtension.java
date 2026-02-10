package com.UnobstructedThirdPerson.AssetEditor;

import com.hypixel.hytale.codec.schema.config.BooleanSchema;
import com.hypixel.hytale.codec.schema.config.IntegerSchema;
import com.hypixel.hytale.codec.schema.config.NumberSchema;
import com.hypixel.hytale.codec.schema.config.ObjectSchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.GenerateSchemaEvent;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class CameraSchemaExtension {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void extendCameraSchema(@Nonnull GenerateSchemaEvent event) {
        try {
            Map<String, Schema> schemas = event.getContext().getDefinitions();
            
            Schema cameraSettingsSchema = schemas.get("CameraSettings");
            if (cameraSettingsSchema == null) {
                LOGGER.at(Level.WARNING).log("CameraSettings schema not found in definitions");
                return;
            }

            if (!(cameraSettingsSchema instanceof ObjectSchema)) {
                LOGGER.at(Level.WARNING).log("CameraSettings schema is not an ObjectSchema");
                return;
            }

            ObjectSchema cameraObjectSchema = (ObjectSchema) cameraSettingsSchema;
            Map<String, Schema> props = cameraObjectSchema.getProperties();
            if (props == null) {
                LOGGER.at(Level.WARNING).log("CameraSettings schema has no properties");
                return;
            }

            // === BASIC SETTINGS ===
            props.put("PositionLerpSpeed", createNumberField("Position Lerp Speed", "Camera position interpolation speed", 1.0));
            props.put("RotationLerpSpeed", createNumberField("Rotation Lerp Speed", "Camera rotation interpolation speed", 1.0));
            props.put("Distance", createNumberField("Distance", "Camera distance from target", 0.0));
            props.put("SpeedModifier", createNumberField("Speed Modifier", "Camera speed multiplier", 1.0));
            props.put("IsFirstPerson", createBooleanField("Is First Person", "Enable first person camera mode", true));

            // === DISPLAY OPTIONS ===
            props.put("AllowPitchControls", createBooleanField("Allow Pitch Controls", "Allow pitch control", false));
            props.put("DisplayCursor", createBooleanField("Display Cursor", "Show cursor", false));
            props.put("DisplayReticle", createBooleanField("Display Reticle", "Show reticle/crosshair", false));
            props.put("EyeOffset", createBooleanField("Eye Offset", "Use eye offset for camera position", false));

            // === POSITION & ROTATION ===
            // Note: PositionOffset already exists in base CameraSettings schema, don't override it
            props.put("PositionDistanceOffsetType", createEnumField("Position Distance Offset Type", 
                "Camera position distance offset calculation type",
                "DistanceOffset", "DistanceOffsetRaycast", "None"));
            props.put("PositionType", createEnumField("Position Type",
                "Camera position calculation type",
                "AttachedToPlusOffset", "Custom"));
            props.put("RotationType", createEnumField("Rotation Type",
                "Camera rotation calculation type",
                "AttachedToPlusOffset", "Custom"));
            props.put("RotationOffset", createVector3Field("Rotation Offset", "Camera rotation offset (pitch, yaw, roll)"));
            props.put("Position", createVector3Field("Position", "Absolute camera position"));
            props.put("Rotation", createVector3Field("Rotation", "Absolute camera rotation"));

            // === MOVEMENT & PHYSICS ===
            props.put("SkipCharacterPhysics", createBooleanField("Skip Character Physics", "Skip character physics calculations", false));
            props.put("CanMoveType", createEnumField("Can Move Type",
                "Camera movement permission type",
                "AttachedToLocalPlayer", "Always"));
            props.put("ApplyMovementType", createEnumField("Apply Movement Type",
                "How movement is applied to the camera",
                "CharacterController", "Position"));
            props.put("MovementMultiplier", createVector3Field("Movement Multiplier", "Movement speed multiplier (X, Y, Z)"));
            props.put("MovementForceRotationType", createEnumField("Movement Force Rotation Type",
                "How movement force rotation is determined",
                "AttachedToHead", "CameraRotation", "Custom"));
            props.put("MovementForceRotation", createVector3Field("Movement Force Rotation", "Custom movement force rotation"));

            // === MOUSE & INPUT ===
            props.put("SendMouseMotion", createBooleanField("Send Mouse Motion", "Send mouse motion events", false));
            props.put("MouseInputType", createEnumField("Mouse Input Type",
                "Type of mouse input handling",
                "LookAtTarget", "LookAtTargetBlock", "LookAtTargetEntity", "LookAtPlane"));
            props.put("MouseInputTargetType", createEnumField("Mouse Input Target Type",
                "What the mouse can target",
                "Any", "Block", "Entity", "None"));
            props.put("ApplyLookType", createEnumField("Apply Look Type",
                "How look direction is applied",
                "LocalPlayerLookOrientation", "Rotation"));
            props.put("LookMultiplier", createVector2Field("Look Multiplier", "Look sensitivity multiplier (X, Y)"));
            props.put("PlaneNormal", createVector3Field("Plane Normal", "Plane normal for LookAtPlane mode"));

            // === ADVANCED ===
            props.put("AttachedToType", createEnumField("Attached To Type",
                "What the camera is attached to",
                "LocalPlayer", "EntityId", "None"));
            props.put("AttachedToEntityId", createIntegerField("Attached To Entity ID", "Entity ID to attach camera to", 0));

            LOGGER.at(Level.INFO).log("Successfully extended CameraSettings schema with all 30 ServerCameraSettings fields");

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to extend CameraSettings schema");
        }
    }

    private static NumberSchema createNumberField(String title, String description, double defaultValue) {
        NumberSchema schema = new NumberSchema();
        schema.setTitle(title);
        schema.setDescription(description);
        schema.setDefault(defaultValue);
        return schema;
    }

    private static BooleanSchema createBooleanField(String title, String description, boolean defaultValue) {
        BooleanSchema schema = new BooleanSchema();
        schema.setTitle(title);
        schema.setDescription(description);
        schema.setDefault(defaultValue);
        return schema;
    }

    private static IntegerSchema createIntegerField(String title, String description, int defaultValue) {
        IntegerSchema schema = new IntegerSchema();
        schema.setTitle(title);
        schema.setDescription(description);
        schema.setDefault(defaultValue);
        return schema;
    }

    private static StringSchema createEnumField(String title, String description, String... values) {
        StringSchema schema = new StringSchema();
        schema.setTitle(title);
        schema.setDescription(description);
        schema.setEnum(values);
        return schema;
    }

    private static ObjectSchema createVector3Field(String title, String description) {
        ObjectSchema schema = new ObjectSchema();
        schema.setTitle(title);
        schema.setDescription(description);
        
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("X", createNumberField("X", "X coordinate", 0.0));
        properties.put("Y", createNumberField("Y", "Y coordinate", 0.0));
        properties.put("Z", createNumberField("Z", "Z coordinate", 0.0));
        schema.setProperties(properties);
        
        return schema;
    }

    private static ObjectSchema createVector2Field(String title, String description) {
        ObjectSchema schema = new ObjectSchema();
        schema.setTitle(title);
        schema.setDescription(description);
        
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("X", createNumberField("X", "X coordinate", 0.0));
        properties.put("Y", createNumberField("Y", "Y coordinate", 0.0));
        schema.setProperties(properties);
        
        return schema;
    }
}
