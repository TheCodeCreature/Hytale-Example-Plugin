package com.CodeCreature.util;

/**
 * @node    FeatureFlags
 * @wiki    docs/wiki/Core/FeatureFlags.md
 * @intent  Central runtime flag registry and persistence for controlled feature
 *          rollout, including stencil center renderer migration toggles.
 * @wave    1 (stencil renderer migration)
 * @status  Wave 1 - grouped center renderer flag added
 * @do-not  Encode page-routing migration behavior in feature defaults.
 */

import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Centralized, thread-safe feature flag system persisted to JSON.
 *
 * <p>Flags are stored as dot-notation keys (e.g. {@code "logging.global"},
 * {@code "diagnostics.breakLog"}) mapped to {@link AtomicBoolean} values in a
 * {@link ConcurrentHashMap}. This allows safe reads from any thread (server,
 * world, ECS) without synchronization.</p>
 *
 * <p>Persistence uses {@link BsonUtil} for JSON I/O with {@code .join()} to
 * ensure synchronous writes (matching Hytale's own TeleportPlugin pattern).</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   // During Plugin.setup():
 *   FeatureFlags.initialize(this.getDataDirectory());
 *
 *   // At any call site:
 *   if (FeatureFlags.get("logging.stencil")) { ... }
 *
 *   // From a command:
 *   boolean newVal = FeatureFlags.toggle("logging.stencil");
 * </pre>
 */
public final class FeatureFlags {

    private static final Logger LOGGER = Logger.getLogger("FeatureFlags");

    /** Thread-safe runtime store. Values are never removed once registered. */
    private static final ConcurrentHashMap<String, AtomicBoolean> flags = new ConcurrentHashMap<>();

    /** Path to the persisted JSON file. Null until {@link #initialize} is called. */
    private static Path filePath;

    private FeatureFlags() {
        // Static utility — no instances
    }

    /**
     * Loads flags from {@code {dataDirectory}/feature_flags.json}.
     *
     * <p>If the file exists, every key-value pair is loaded into the map.
     * After loading, all known default flags are registered (no-op for keys
     * already present). If the file is missing or unreadable, defaults are
     * used and a new file is written.</p>
     *
     * <p>Must be called once from {@code Plugin.setup()} before any other
     * method.</p>
     *
     * @param dataDirectory the plugin's data directory (from {@code getDataDirectory()})
     */
    public static void initialize(Path dataDirectory) {
        filePath = dataDirectory.resolve("feature_flags.json");
        BsonDocument doc = BsonUtil.readDocumentNow(filePath);
        if (doc != null) {
            for (Map.Entry<String, BsonValue> entry : doc.entrySet()) {
                if (entry.getValue().isBoolean()) {
                    flags.put(entry.getKey(), new AtomicBoolean(entry.getValue().asBoolean().getValue()));
                }
            }
        }
        registerDefaults();
        save();
    }

    /**
     * Persists the current flag state to {@code feature_flags.json}.
     *
     * <p>Builds a {@link BsonDocument} with one {@link BsonBoolean} entry per
     * flag, then writes synchronously via {@link BsonUtil#writeDocument} with
     * {@code .join()} to block until the async I/O completes. Logs a warning
     * on I/O failure — never throws.</p>
     */
    public static void save() {
        if (filePath == null) return;
        BsonDocument doc = new BsonDocument();
        for (Map.Entry<String, AtomicBoolean> entry : flags.entrySet()) {
            doc.put(entry.getKey(), new BsonBoolean(entry.getValue().get()));
        }
        try {
            BsonUtil.writeDocument(filePath, doc).join();
        } catch (Exception e) {
            LOGGER.warning("[FeatureFlags] Failed to save: " + e.getMessage());
        }
    }

    /**
     * Returns the current value of a flag.
     *
     * <p>If the key is not registered, returns {@code false} (fail-closed).
     * Thread-safe: reads from {@link AtomicBoolean#get()}.</p>
     *
     * @param key dot-notation flag key (e.g. {@code "logging.stencil"})
     * @return current flag value, or {@code false} if unregistered
     */
    public static boolean get(String key) {
        AtomicBoolean flag = flags.get(key);
        return flag == null ? false : flag.get();
    }

    /**
     * Sets a flag value and auto-saves to disk.
     *
     * <p>If the key is not yet registered, it is registered first.
     * Thread-safe: writes via {@link AtomicBoolean#set(boolean)}.</p>
     *
     * @param key   dot-notation flag key
     * @param value new value
     */
    public static void set(String key, boolean value) {
        flags.computeIfAbsent(key, k -> new AtomicBoolean(value)).set(value);
        save();
    }

    /**
     * Flips a flag using a CAS loop and auto-saves.
     *
     * <p>Uses a compare-and-set loop on the underlying {@link AtomicBoolean}
     * to ensure thread-safe flipping.</p>
     *
     * @param key dot-notation flag key
     * @return the new value after toggling
     */
    public static boolean toggle(String key) {
        AtomicBoolean flag = flags.computeIfAbsent(key, k -> new AtomicBoolean(false));
        boolean prev, next;
        do {
            prev = flag.get();
            next = !prev;
        } while (!flag.compareAndSet(prev, next));
        save();
        return next;
    }

    /**
     * Registers a flag with a default value if it is not already present.
     *
     * <p>Does not overwrite values loaded from disk — file-loaded values
     * take precedence over defaults.</p>
     *
     * @param key          dot-notation flag key
     * @param defaultValue value to use if the key is new
     */
    public static void register(String key, boolean defaultValue) {
        flags.putIfAbsent(key, new AtomicBoolean(defaultValue));
    }

    /**
     * Returns an unmodifiable snapshot of all registered flags.
     *
     * <p>The returned map is a copy — modifications do not affect the
     * runtime state.</p>
     *
     * @return unmodifiable {@code Map<String, Boolean>} of all flags
     */
    public static Map<String, Boolean> getAll() {
        LinkedHashMap<String, Boolean> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicBoolean> entry : flags.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Registers all known default flags.
     *
     * <p>Called from {@link #initialize} after loading the file. Keys already
     * present in the map (from the file) are not overwritten.</p>
     */
    private static void registerDefaults() {
        register("logging.global", false);
        register("logging.plugin", false);
        register("logging.stencil", false);
        register("logging.stencil_book", false);
        register("logging.scaling", false);
        register("logging.registry", false);
        register("logging.crafting", false);
        register("logging.ingredient_tree", false);
        register("diagnostics.breakLog", false);
        register("ui.stencil_book.grouped_center_renderer", false);
        register("ui.stencil_book.sandbox_route", true);
    }
}
