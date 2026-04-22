package com.UnobstructedThirdPerson.placeblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@link PlaceBlockConfig} from a JSON classpath resource.
 *
 * <p>Expected JSON schema:</p>
 * <pre>{@code
 * {
 *   "chestHorizontalRadius": 8,
 *   "chestVerticalRadius": 4,
 *   "placeholderItemId": "hytale:Block_Placeholder"
 * }
 * }</pre>
 *
 * <p>Follows the same pattern as {@link com.UnobstructedThirdPerson.portablebench.PortableBenchConfigLoader}.</p>
 */
public final class PlaceBlockConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(PlaceBlockConfigLoader.class.getSimpleName());

    @Nullable
    private static PlaceBlockConfig loadedConfig;

    private PlaceBlockConfigLoader() {}

    /**
     * Loads the PlaceBlock config from the given classpath resource.
     * Stores the result for retrieval via {@link #getConfig()}.
     *
     * @param resourcePath classpath path, e.g. {@code "/placeblock_config.json"}
     * @return the loaded config, or null if loading failed
     */
    @Nullable
    public static PlaceBlockConfig loadAndStore(String resourcePath) {
        InputStream stream = PlaceBlockConfigLoader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            LOGGER.warning("PlaceBlock config not found: " + resourcePath);
            return null;
        }

        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse PlaceBlock config: " + resourcePath, e);
            return null;
        }

        int chestHorizontalRadius = root.get("chestHorizontalRadius").getAsInt();
        int chestVerticalRadius = root.get("chestVerticalRadius").getAsInt();
        String placeholderItemId = root.get("placeholderItemId").getAsString();

        loadedConfig = new PlaceBlockConfig(chestHorizontalRadius, chestVerticalRadius, placeholderItemId);
        LOGGER.info("PlaceBlock config loaded — radius=" + chestHorizontalRadius
                + "x" + chestVerticalRadius + ", placeholder=" + placeholderItemId);
        return loadedConfig;
    }

    /**
     * Returns the most recently loaded config, or null if none has been loaded.
     */
    @Nullable
    public static PlaceBlockConfig getConfig() {
        return loadedConfig;
    }
}
