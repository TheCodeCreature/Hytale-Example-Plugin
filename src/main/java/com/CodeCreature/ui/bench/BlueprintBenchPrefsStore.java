package com.CodeCreature.ui.bench;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

public class BlueprintBenchPrefsStore {

    private static final Logger LOGGER = Logger.getLogger("BlueprintBenchPrefsStore");
    private static Path prefsDir;

    public static void initialize(Path dataDirectory) {
        prefsDir = dataDirectory.resolve("player_prefs");
        try {
            Files.createDirectories(prefsDir);
        } catch (IOException e) {
            LOGGER.warning("[BlueprintPrefs] Failed to create prefs directory: " + e.getMessage());
        }
    }

    public static BlueprintBenchPrefs load(UUID playerUuid) {
        if (prefsDir == null) return new BlueprintBenchPrefs();

        Path file = prefsDir.resolve(playerUuid + ".json");
        BsonDocument doc = BsonUtil.readDocumentNow(file);
        if (doc == null) return new BlueprintBenchPrefs();

        try {
            return BlueprintBenchPrefs.CODEC.decode(doc, new ExtraInfo());
        } catch (Exception e) {
            LOGGER.warning("[BlueprintPrefs] Failed to decode prefs for " + playerUuid + ": " + e.getMessage());
            return new BlueprintBenchPrefs();
        }
    }

    public static void save(UUID playerUuid, BlueprintBenchPrefs prefs) {
        if (prefsDir == null) return;

        Path file = prefsDir.resolve(playerUuid + ".json");
        try {
            BsonDocument doc = BlueprintBenchPrefs.CODEC.encode(prefs, new ExtraInfo());
            BsonUtil.writeDocument(file, doc);
        } catch (Exception e) {
            LOGGER.warning("[BlueprintPrefs] Failed to save prefs for " + playerUuid + ": " + e.getMessage());
        }
    }
}
