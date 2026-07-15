package com.CodeCreature.ui.bench;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

public class StencilBookPrefsStore {

    private static Path prefsDir;

    public static void initialize(Path dataDirectory) {
        prefsDir = dataDirectory.resolve("player_prefs");
        try {
            Files.createDirectories(prefsDir);
        } catch (IOException e) {
            DebugLogger.log(STENCIL_BOOK, Level.WARNING, "[StencilPrefs] Failed to create prefs directory: " + e.getMessage());
        }
    }

    public static StencilBookPrefs load(UUID playerUuid) {
        if (prefsDir == null) return new StencilBookPrefs();

        Path file = prefsDir.resolve(playerUuid + ".json");
        BsonDocument doc = BsonUtil.readDocumentNow(file);
        if (doc == null) return new StencilBookPrefs();

        try {
            return StencilBookPrefs.CODEC.decode(doc, new ExtraInfo());
        } catch (Exception e) {
            DebugLogger.log(STENCIL_BOOK, Level.WARNING, "[StencilPrefs] Failed to decode prefs for " + playerUuid + ": " + e.getMessage());
            return new StencilBookPrefs();
        }
    }

    public static void save(UUID playerUuid, StencilBookPrefs prefs) {
        if (prefsDir == null) return;

        Path file = prefsDir.resolve(playerUuid + ".json");
        try {
            BsonDocument doc = StencilBookPrefs.CODEC.encode(prefs, new ExtraInfo());
            BsonUtil.writeDocument(file, doc);
        } catch (Exception e) {
            DebugLogger.log(STENCIL_BOOK, Level.WARNING, "[StencilPrefs] Failed to save prefs for " + playerUuid + ": " + e.getMessage());
        }
    }
}
