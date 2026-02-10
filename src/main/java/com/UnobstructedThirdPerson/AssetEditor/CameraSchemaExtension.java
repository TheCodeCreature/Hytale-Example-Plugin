package com.UnobstructedThirdPerson.AssetEditor;

import com.hypixel.hytale.codec.schema.config.ObjectSchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.GenerateSchemaEvent;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.logging.Level;

public class CameraSchemaExtension {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void extendCameraSchema(@Nonnull GenerateSchemaEvent event) {
        try {
            // Get the schemas map from the event
            Map<String, Schema> schemas = event.getContext().getDefinitions();
            
            // Find the CameraSettings schema in definitions
            Schema cameraSettingsSchema = schemas.get("CameraSettings");
            if (cameraSettingsSchema == null) {
                LOGGER.at(Level.WARNING).log("CameraSettings schema not found in definitions, cannot extend Camera schema");
                return;
            }

            // Verify it's an ObjectSchema
            if (!(cameraSettingsSchema instanceof ObjectSchema)) {
                LOGGER.at(Level.WARNING).log("CameraSettings schema is not an ObjectSchema");
                return;
            }

            ObjectSchema cameraObjectSchema = (ObjectSchema) cameraSettingsSchema;
            Map<String, Schema> cameraProperties = cameraObjectSchema.getProperties();
            if (cameraProperties == null) {
                LOGGER.at(Level.WARNING).log("CameraSettings schema has no properties");
                return;
            }

            // Create the PositionDistanceOffsetType enum schema using StringSchema
            StringSchema positionDistanceOffsetTypeSchema = new StringSchema();
            positionDistanceOffsetTypeSchema.setEnum(new String[]{
                "DistanceOffset",
                "DistanceOffsetRaycast",
                "None"
            });
            positionDistanceOffsetTypeSchema.setTitle("PositionDistanceOffsetType");
            positionDistanceOffsetTypeSchema.setDescription("Camera position distance offset calculation type");

            // Add the field to the CameraSettings schema
            cameraProperties.put("PositionDistanceOffsetType", positionDistanceOffsetTypeSchema);

            LOGGER.at(Level.INFO).log("Successfully extended CameraSettings schema with PositionDistanceOffsetType field");

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to extend CameraSettings schema");
        }
    }
}
