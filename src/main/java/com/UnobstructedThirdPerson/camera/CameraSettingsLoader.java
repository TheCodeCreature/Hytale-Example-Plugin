package com.UnobstructedThirdPerson.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.ApplyMovementType;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.CanMoveType;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputTargetType;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.Vector2f;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CameraSettingsLoader {

    private static final Logger LOGGER = Logger.getLogger(CameraSettingsLoader.class.getName());
    private static final Gson GSON = new GsonBuilder().create();

    private static final String PLAYER_MODEL_PATH = "Server/Models/Human/Player.json";
    private static final String PLAYER_MODEL_ID = "Human/Player";

    @Nullable
    public static ExtendedCameraSettings loadFromPlayerModel() {
        try {
            ModelAsset playerModel = ModelAsset.getAssetMap().getAsset(PLAYER_MODEL_ID);
            if (playerModel == null) {
                LOGGER.log(Level.WARNING, "Player model asset not found: " + PLAYER_MODEL_ID);
                return null;
            }
            return loadFromModelAsset(playerModel);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load camera settings from player model", e);
            return null;
        }
    }

    @Nullable
    public static ExtendedCameraSettings loadFromModelAsset(@Nonnull ModelAsset modelAsset) {
        try {
            // Try to load from resource file directly
            String resourcePath = "/" + PLAYER_MODEL_PATH;
            InputStream inputStream = CameraSettingsLoader.class.getResourceAsStream(resourcePath);
            
            if (inputStream == null) {
                LOGGER.log(Level.WARNING, "Could not find resource: " + resourcePath);
                return createDefaultSettings();
            }

            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (!rootElement.isJsonObject()) {
                    LOGGER.log(Level.WARNING, "Player.json root is not a JSON object");
                    return createDefaultSettings();
                }

                JsonObject rootObject = rootElement.getAsJsonObject();
                JsonElement cameraElement = rootObject.get("Camera");
                
                if (cameraElement == null || !cameraElement.isJsonObject()) {
                    LOGGER.log(Level.INFO, "No Camera section found in Player.json, using defaults");
                    return createDefaultSettings();
                }

                return parseFromJson(cameraElement.getAsJsonObject());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load camera settings from model asset", e);
            return createDefaultSettings();
        }
    }

    @Nonnull
    public static ExtendedCameraSettings parseFromJson(@Nonnull JsonObject cameraJson) {
        ExtendedCameraSettings settings = new ExtendedCameraSettings();

        // Float fields
        settings.positionLerpSpeed = getFloat(cameraJson, "PositionLerpSpeed", 1.0f);
        settings.rotationLerpSpeed = getFloat(cameraJson, "RotationLerpSpeed", 1.0f);
        settings.distance = getFloat(cameraJson, "Distance", 0.0f);
        settings.speedModifier = getFloat(cameraJson, "SpeedModifier", 1.0f);

        // Boolean fields
        settings.allowPitchControls = getBoolean(cameraJson, "AllowPitchControls", false);
        settings.displayCursor = getBoolean(cameraJson, "DisplayCursor", false);
        settings.displayReticle = getBoolean(cameraJson, "DisplayReticle", false);
        settings.sendMouseMotion = getBoolean(cameraJson, "SendMouseMotion", false);
        settings.skipCharacterPhysics = getBoolean(cameraJson, "SkipCharacterPhysics", false);
        settings.isFirstPerson = getBoolean(cameraJson, "IsFirstPerson", true);
        settings.eyeOffset = getBoolean(cameraJson, "EyeOffset", false);

        // Integer field
        settings.attachedToEntityId = getInt(cameraJson, "AttachedToEntityId", 0);

        // Enum fields
        settings.mouseInputTargetType = getEnum(cameraJson, "MouseInputTargetType", MouseInputTargetType.class, MouseInputTargetType.Any);
        settings.movementForceRotationType = getEnum(cameraJson, "MovementForceRotationType", MovementForceRotationType.class, MovementForceRotationType.AttachedToHead);
        settings.attachedToType = getEnum(cameraJson, "AttachedToType", AttachedToType.class, AttachedToType.LocalPlayer);
        settings.positionDistanceOffsetType = getEnum(cameraJson, "PositionDistanceOffsetType", PositionDistanceOffsetType.class, PositionDistanceOffsetType.DistanceOffset);
        settings.positionType = getEnum(cameraJson, "PositionType", PositionType.class, PositionType.AttachedToPlusOffset);
        settings.rotationType = getEnum(cameraJson, "RotationType", RotationType.class, RotationType.AttachedToPlusOffset);
        settings.canMoveType = getEnum(cameraJson, "CanMoveType", CanMoveType.class, CanMoveType.AttachedToLocalPlayer);
        settings.applyMovementType = getEnum(cameraJson, "ApplyMovementType", ApplyMovementType.class, ApplyMovementType.CharacterController);
        settings.applyLookType = getEnum(cameraJson, "ApplyLookType", ApplyLookType.class, ApplyLookType.LocalPlayerLookOrientation);
        settings.mouseInputType = getEnum(cameraJson, "MouseInputType", MouseInputType.class, MouseInputType.LookAtTarget);

        // Direction fields (yaw, pitch, roll)
        settings.movementForceRotation = getDirection(cameraJson, "MovementForceRotation");
        settings.rotationOffset = getDirection(cameraJson, "RotationOffset");
        settings.rotation = getDirection(cameraJson, "Rotation");

        // Position fields (x, y, z doubles)
        settings.positionOffset = getPosition(cameraJson, "PositionOffset");
        settings.position = getPosition(cameraJson, "Position");

        // Vector3f fields
        settings.movementMultiplier = getVector3f(cameraJson, "MovementMultiplier");
        settings.planeNormal = getVector3f(cameraJson, "PlaneNormal");

        // Vector2f field
        settings.lookMultiplier = getVector2f(cameraJson, "LookMultiplier");

        LOGGER.log(Level.INFO, "Loaded extended camera settings: " + settings);
        return settings;
    }

    @Nonnull
    public static ExtendedCameraSettings createDefaultSettings() {
        return new ExtendedCameraSettings();
    }

    private static float getFloat(JsonObject json, String key, float defaultValue) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsFloat();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsBoolean();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsInt();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static <E extends Enum<E>> E getEnum(JsonObject json, String key, Class<E> enumClass, E defaultValue) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                String value = element.getAsString();
                return Enum.valueOf(enumClass, value);
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Nullable
    private static Direction getDirection(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Direction dir = new Direction();
            dir.yaw = getFloat(obj, "Yaw", 0.0f);
            dir.pitch = getFloat(obj, "Pitch", 0.0f);
            dir.roll = getFloat(obj, "Roll", 0.0f);
            return dir;
        }
        return null;
    }

    @Nullable
    private static Position getPosition(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Position pos = new Position();
            pos.x = getDouble(obj, "X", 0.0);
            pos.y = getDouble(obj, "Y", 0.0);
            pos.z = getDouble(obj, "Z", 0.0);
            return pos;
        }
        return null;
    }

    @Nullable
    private static Vector3f getVector3f(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Vector3f vec = new Vector3f();
            vec.x = getFloat(obj, "X", 0.0f);
            vec.y = getFloat(obj, "Y", 0.0f);
            vec.z = getFloat(obj, "Z", 0.0f);
            return vec;
        }
        return null;
    }

    @Nullable
    private static Vector2f getVector2f(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Vector2f vec = new Vector2f();
            vec.x = getFloat(obj, "X", 0.0f);
            vec.y = getFloat(obj, "Y", 0.0f);
            return vec;
        }
        return null;
    }

    private static double getDouble(JsonObject json, String key, double defaultValue) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsDouble();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
