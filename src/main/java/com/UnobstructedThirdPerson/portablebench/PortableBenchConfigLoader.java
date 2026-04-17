package com.UnobstructedThirdPerson.portablebench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads {@code portable_benches.json} from the classpath and produces
 * {@link PortableBenchConfig} instances keyed by item ID.
 *
 * <p>Expected JSON schema:</p>
 * <pre>{@code
 * {
 *   "benches": {
 *     "<itemId>": {
 *       "benchId": "Builders",
 *       "benchName": "server.items.Bench_Builders.name",
 *       "categories": [
 *         {
 *           "id": "Blocks",
 *           "name": "Blocks",
 *           "icon": "",
 *           "recipeCategoryIds": ["WoodPlanks", "Bricks"]
 *         }
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 */
public final class PortableBenchConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(PortableBenchConfigLoader.class.getSimpleName());

    private PortableBenchConfigLoader() {
    }

    /**
     * Loads bench configs from the given classpath resource and registers them
     * in {@link PortableBenchRegistry}.
     *
     * @param resourcePath classpath path, e.g. {@code "/portable_benches.json"}
     * @return the number of configs registered
     */
    public static int loadAndRegister(String resourcePath) {
        InputStream stream = PortableBenchConfigLoader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            LOGGER.warning("Portable bench config not found: " + resourcePath);
            return 0;
        }

        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse portable bench config: " + resourcePath, e);
            return 0;
        }

        JsonObject benches = root.getAsJsonObject("benches");
        if (benches == null) {
            LOGGER.warning("No 'benches' object in " + resourcePath);
            return 0;
        }

        int count = 0;
        for (Map.Entry<String, JsonElement> entry : benches.entrySet()) {
            String itemId = entry.getKey();
            try {
                JsonObject benchObj = entry.getValue().getAsJsonObject();
                String benchId = benchObj.get("benchId").getAsString();
                String benchName = benchObj.get("benchName").getAsString();

                JsonArray categoriesArr = benchObj.getAsJsonArray("categories");
                PortableBenchConfig.CategoryDef[] categories =
                        new PortableBenchConfig.CategoryDef[categoriesArr.size()];

                for (int i = 0; i < categoriesArr.size(); i++) {
                    JsonObject catObj = categoriesArr.get(i).getAsJsonObject();
                    String id = catObj.get("id").getAsString();
                    String name = catObj.get("name").getAsString();
                    String icon = catObj.get("icon").getAsString();

                    JsonArray recipeIdsArr = catObj.getAsJsonArray("recipeCategoryIds");
                    String[] recipeCategoryIds = new String[recipeIdsArr.size()];
                    for (int j = 0; j < recipeIdsArr.size(); j++) {
                        recipeCategoryIds[j] = recipeIdsArr.get(j).getAsString();
                    }

                    categories[i] = new PortableBenchConfig.CategoryDef(id, name, icon, recipeCategoryIds);
                }

                PortableBenchConfig config = new PortableBenchConfig(benchId, benchName, categories);
                PortableBenchRegistry.register(itemId, config);
                count++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Skipping malformed bench entry: " + itemId, e);
            }
        }

        LOGGER.info("Loaded " + count + " portable bench config(s) from " + resourcePath);
        return count;
    }
}
