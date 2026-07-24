package com.CodeCreature.registry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.REGISTRY;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

/**
 * Runtime registry of all crafting bench IDs discovered from recipe assets.
 *
 * <p>Replaces the hardcoded {@code BenchCategory} enum with a data-driven
 * approach: at init time, every {@link CraftingRecipe} asset is scanned for
 * {@link BenchRequirement} entries, and each unique {@code BenchRequirement.id}
 * is registered with a {@link BenchConfig}.
 *
 * <p><strong>Opt-out model:</strong> all discovered benches are included unless
 * explicitly listed in the deny-list configuration file ({@code deny-list.json}).
 *
 * <p><strong>preferNatural defaults:</strong>
 * <ul>
 *   <li>{@code "Furniture_Bench"} → {@code true} (hardcoded override for
 *       backward compatibility with existing drop-scaling behavior)</li>
 *   <li>All other benches → {@code false}</li>
 * </ul>
 *
 * <p><strong>Overlap resolution:</strong> when a recipe belongs to multiple
 * benches, {@link #isPreferNatural(Set)} returns {@code true} if ANY bench
 * in the set has {@code preferNatural = true}. This matches the legacy
 * {@code BUILDERS_AND_FURNITURE.preferNatural = true} behavior.
 *
 * <p><strong>Lifecycle:</strong> call {@link #init()} once before any
 * downstream consumer ({@link RecipeFilterRegistry}, {@link BenchRecipeRegistries},
 * {@link com.CodeCreature.scaling.NaturalResourceRegistry}) initializes.
 * After init, all methods are thread-safe (state is effectively immutable).
 *
 * <p><strong>Init order in {@code DropScaler.apply()}:</strong>
 * <pre>
 *   BenchRegistry.init();           // ← NEW first step
 *   NaturalResourceRegistry.init();
 *   RecipeTierClassifier.init();
 *   RecipeFilterRegistry.init(...);
 *   BenchRecipeRegistries.init();
 * </pre>
 */
public final class BenchRegistry {

    private static final String BENCH_TAB_GROUPS_FILE = "bench-tab-groups.json";
    private static final String DEFAULT_BENCH_TAB_GROUPS_RESOURCE = "/bench-tab-groups.json";

    /** benchId → BenchConfig. Populated at init, immutable afterward. */
    private static Map<String, BenchConfig> configs = Collections.emptyMap();

    /** Bench IDs excluded from processing. Loaded from deny-list.json. */
    private static Set<String> denyList = Collections.emptySet();

    /** Recipe ID prefixes excluded from RecipeFilterRegistry. Loaded from config. */
    private static List<String> skipPrefixes = List.of();

    /** Tab grouper mapping raw bench IDs to grouped tab IDs. */
    private static BenchTabGrouper tabGrouper;

    /** Plugin data directory for loading config files. Null until {@link #initialize} is called. */
    private static Path dataDirectory;

    /** Guard against double-init. */
    private static boolean initialized = false;

    private BenchRegistry() {}

    /**
     * Stores the plugin data directory for later use by {@link #init()}.
     * Must be called from {@code Plugin.setup()} before {@link #init()}.
     *
     * @param dataDir the plugin's data directory
     */
    public static void initialize(@Nonnull Path dataDir) {
        BenchRegistry.dataDirectory = dataDir;
        seedDefaultBenchTabGroupsConfig(dataDir);
    }

    /**
     * Returns the plugin data directory captured during initialize.
     *
     * @return plugin data directory, or null if initialize has not run yet
     */
    @Nullable
    public static Path getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Scans all {@link CraftingRecipe} assets, extracts unique
     * {@code BenchRequirement.id} values, applies the deny list,
     * and registers a {@link BenchConfig} per allowed bench.
     *
     * <p>Must be called exactly once, after assets are loaded and
     * before any downstream registry initializes.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Load deny list from {@code deny-list.json} in plugin data dir</li>
     *   <li>Iterate all {@link CraftingRecipe} assets</li>
     *   <li>For each recipe, extract all non-null {@code BenchRequirement.id}
     *       values</li>
     *   <li>Skip IDs present in the deny list</li>
     *   <li>Register a {@link BenchConfig} for each unique bench ID:
     *       <ul>
     *         <li>{@code "Furniture_Bench"} → {@code preferNatural = true}</li>
     *         <li>All others → {@code preferNatural = false}</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>Logs the number of discovered benches, denied benches, and
     * final registered benches at INFO level.
     */
    public static void init() {
        if (initialized) {
            DebugLogger.log(REGISTRY, Level.WARNING, "[BenchRegistry] init() called more than once — skipping");
            return;
        }
        initialized = true;

        if (dataDirectory != null) {
            denyList = loadDenyList(dataDirectory.resolve("deny-list.json"));
        } else {
            denyList = Collections.emptySet();
        }

        // Load config early to get benchOverrides and skipPrefixes
        Path configPath = dataDirectory != null ? dataDirectory.resolve(BENCH_TAB_GROUPS_FILE) : null;
        BenchTabGrouper.TabGroupConfig tabConfig = BenchTabGrouper.loadConfig(configPath);
        skipPrefixes = List.copyOf(tabConfig.skipPrefixes());

        Map<String, BenchConfig> discovered = new LinkedHashMap<>();
        int totalFound = 0;
        int denied = 0;

        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            BenchRequirement[] requirements = recipe.getBenchRequirement();
            if (requirements == null) continue;
            for (BenchRequirement req : requirements) {
                if (req == null || req.id == null) continue;
                totalFound++;
                if (denyList.contains(req.id)) {
                    denied++;
                    continue;
                }
                if (!discovered.containsKey(req.id)) {
                    BenchTabGrouper.BenchOverride override = tabConfig.benchOverrides().get(req.id);
                    boolean preferNatural = (override != null) && override.preferNatural();
                    discovered.put(req.id, new BenchConfig(req.id, preferNatural));
                }
            }
        }

        // Warn about benchOverrides referencing unknown bench IDs
        for (String overrideId : tabConfig.benchOverrides().keySet()) {
            if (!discovered.containsKey(overrideId)) {
                DebugLogger.log(REGISTRY, Level.WARNING,
                        "[BenchRegistry] benchOverride for '" + overrideId +
                        "' does not match any discovered bench");
            }
        }

        configs = Collections.unmodifiableMap(discovered);

        tabGrouper = BenchTabGrouper.create(configs.keySet(),
                dataDirectory != null ? dataDirectory.resolve(BENCH_TAB_GROUPS_FILE) : null);

        DebugLogger.log(REGISTRY, Level.INFO,
            "[BenchRegistry] Discovered " + totalFound + " bench references, "
                + denied + " denied, " + configs.size() + " registered");
    }


    private static void seedDefaultBenchTabGroupsConfig(@Nonnull Path dataDir) {
        Path target = dataDir.resolve(BENCH_TAB_GROUPS_FILE);

        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                    "[BenchRegistry] Failed to create data directory for default config: " +
                            dataDir + " (" + e.getMessage() + ")");
            return;
        }

        try (InputStream in = BenchRegistry.class.getResourceAsStream(DEFAULT_BENCH_TAB_GROUPS_RESOURCE)) {
            if (in == null) {
                DebugLogger.log(REGISTRY, Level.WARNING,
                        "[BenchRegistry] Missing bundled default " + BENCH_TAB_GROUPS_FILE +
                                " at " + DEFAULT_BENCH_TAB_GROUPS_RESOURCE);
                return;
            }

            String bundledJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            if (!Files.exists(target)) {
                Files.writeString(target, bundledJson, StandardCharsets.UTF_8);
                DebugLogger.log(REGISTRY, Level.INFO,
                        "[BenchRegistry] Seeded default " + BENCH_TAB_GROUPS_FILE + " to " + target);
                return;
            }

            String existingJson = Files.readString(target, StandardCharsets.UTF_8);
            if (shouldUpgradeLegacyBenchTabGroups(existingJson, bundledJson)) {
                Path backup = target.resolveSibling(BENCH_TAB_GROUPS_FILE + ".bak-" + System.currentTimeMillis());
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(target, bundledJson, StandardCharsets.UTF_8);
                DebugLogger.log(REGISTRY, Level.INFO,
                        "[BenchRegistry] Upgraded stale " + BENCH_TAB_GROUPS_FILE + " at " + target +
                                " (backup: " + backup + ")");
            }
        } catch (IOException e) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                    "[BenchRegistry] Failed to seed default " + BENCH_TAB_GROUPS_FILE +
                            " to " + target + " (" + e.getMessage() + ")");
        }
    }

    private static boolean shouldUpgradeLegacyBenchTabGroups(@Nonnull String existingJson,
                                                              @Nonnull String bundledJson) {
        try {
            BsonDocument existingDoc = BsonDocument.parse(existingJson);
            BsonDocument bundledDoc = BsonDocument.parse(bundledJson);
            int existingGroups = getGroupCount(existingDoc);
            int bundledGroups = getGroupCount(bundledDoc);
            return existingGroups == 0 && bundledGroups > 0;
        } catch (RuntimeException parseError) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                    "[BenchRegistry] Could not parse existing " + BENCH_TAB_GROUPS_FILE +
                            " for stale-check; leaving file unchanged (" + parseError.getMessage() + ")");
            return false;
        }
    }

    private static int getGroupCount(@Nonnull BsonDocument doc) {
        if (!doc.containsKey("groups") || !doc.get("groups").isArray()) {
            return 0;
        }
        return doc.getArray("groups").size();
    }

    /**
     * Returns all registered (non-denied) bench IDs.
     *
     * <p>This is the <strong>single source of truth</strong> for which
     * bench IDs the system recognizes. Replaces the former
     * {@code BenchCategory.allBenchIds()} and
     * {@code NaturalResourceRegistry.CRAFTING_BENCH_IDS}.
     *
     * @return unmodifiable set of all allowed bench IDs; empty if
     *         {@link #init()} has not been called
     */
    @Nonnull
    public static Set<String> allBenchIds() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    /**
     * Returns the configuration for a specific bench, or {@code null}
     * if the bench ID is unknown or denied.
     *
     * @param benchId the bench requirement ID (e.g. {@code "Builders"})
     * @return the config, or {@code null}
     */
    @Nullable
    public static BenchConfig getConfig(@Nonnull String benchId) {
        return configs.get(benchId);
    }

    /**
     * Resolves the effective {@code preferNatural} flag for a set of
     * bench IDs (i.e., the bench-set of a recipe that declares multiple
     * bench requirements).
     *
     * <p><strong>Overlap rule:</strong> returns {@code true} if ANY bench
     * in the set has {@code preferNatural = true}. This preserves the
     * legacy behavior where {@code BUILDERS_AND_FURNITURE} had
     * {@code preferNatural = true} because Furniture_Bench is in the set.
     *
     * <p>If the set is empty or contains only unknown bench IDs, returns
     * {@code false} (safe default — prefers non-natural items).
     *
     * @param benchIds the set of bench requirement IDs from a recipe
     * @return {@code true} if natural items should be preferred
     */
    public static boolean isPreferNatural(@Nonnull Set<String> benchIds) {
        for (String benchId : benchIds) {
            BenchConfig config = configs.get(benchId);
            if (config != null && config.preferNatural()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given bench ID is a crafting bench
     * (as opposed to a processing/refinement bench like Stonecutter).
     *
     * <p>Used by {@link BenchRecipeRegistries#getRecipeForBlock} to
     * prioritize crafting recipes over processing recipes when a block
     * has recipes at both types.
     *
     * @param benchId the bench requirement ID
     * @return true if this is a crafting bench
     */
    public static boolean isCraftingBenchId(@Nonnull String benchId) {
        return configs.containsKey(benchId);
    }

    /**
     * Returns the skip prefixes loaded from config.
     * Used by {@link com.CodeCreature.scaling.DropScaler} to pass to
     * {@link RecipeFilterRegistry#init(java.util.Set)}.
     *
     * @return unmodifiable list of skip prefixes; never null
     */
    @Nonnull
    public static List<String> getSkipPrefixes() {
        return skipPrefixes;
    }

    /**
     * Returns the tab grouper that maps raw bench IDs to grouped tab IDs.
     *
     * @return the tab grouper; never null after {@link #init()} has run
     */
    @Nonnull
    public static BenchTabGrouper getTabGrouper() {
        return tabGrouper;
    }

    /**
     * Returns {@code true} if the given bench ID is not on the deny list.
     *
     * <p>Note: a bench that is "allowed" by this method may still not
     * appear in {@link #allBenchIds()} if no recipe declares it. This
     * method is useful for deny-list diagnostics and testing.
     *
     * @param benchId the bench requirement ID to check
     * @return {@code true} if not denied
     */
    public static boolean isBenchAllowed(@Nonnull String benchId) {
        return !denyList.contains(benchId);
    }

    /**
     * Loads the deny list from the given JSON file path.
     *
     * <p>Expected format:
     * <pre>{@code
     * {
     *   "deniedBenchIds": ["Stencil"]
     * }
     * }</pre>
     *
     * <p>If the file does not exist or is malformed, returns an empty set
     * and logs a warning.
     *
     * @param configPath path to the deny-list.json file
     * @return set of denied bench IDs (never null)
     */
    @Nonnull
    static Set<String> loadDenyList(@Nonnull Path configPath) {
        if (!Files.exists(configPath)) {
            DebugLogger.log(REGISTRY, Level.INFO,
                "[BenchRegistry] No deny-list.json found at " + configPath + " — using empty deny list");
            return Collections.emptySet();
        }
        try {
            String json = Files.readString(configPath);
            BsonDocument doc = BsonDocument.parse(json);
            BsonArray array = doc.getArray("deniedBenchIds", new BsonArray());
            Set<String> denied = new HashSet<>();
            for (BsonValue value : array) {
                if (value.isString()) {
                    denied.add(value.asString().getValue());
                }
            }
            return Collections.unmodifiableSet(denied);
        } catch (IOException e) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                "[BenchRegistry] Failed to read deny-list.json: " + e.getMessage());
            return Collections.emptySet();
        } catch (Exception e) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                "[BenchRegistry] Malformed deny-list.json: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Resets the registry to its uninitialized state.
     * <strong>Test-only</strong> — allows re-initialization in unit tests.
     */
    public static void reset() {
        configs = Collections.emptyMap();
        denyList = Collections.emptySet();
        skipPrefixes = List.of();
        tabGrouper = null;
        initialized = false;
    }
}
