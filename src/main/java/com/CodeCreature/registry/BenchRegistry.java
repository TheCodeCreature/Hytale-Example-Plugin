package com.CodeCreature.registry;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

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

    /**
     * The bench ID that receives a hardcoded {@code preferNatural = true}
     * override for backward compatibility.
     */
    private static final String FURNITURE_BENCH_ID = "Furniture_Bench";

    /** benchId → BenchConfig. Populated at init, immutable afterward. */
    private static Map<String, BenchConfig> configs = Collections.emptyMap();

    /** Bench IDs excluded from processing. Loaded from deny-list.json. */
    private static Set<String> denyList = Collections.emptySet();

    private BenchRegistry() {}

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
        // TODO: Load deny list from plugin data directory (deny-list.json)
        // TODO: Scan all CraftingRecipe assets for unique BenchRequirement.id values
        // TODO: Filter out denied bench IDs
        // TODO: Create BenchConfig per bench (Furniture_Bench → preferNatural=true, others → false)
        // TODO: Store in configs map (LinkedHashMap for deterministic order)
        // TODO: Log discovery results
        throw new UnsupportedOperationException("TODO");
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
        // TODO: Return unmodifiable key set of configs map
        throw new UnsupportedOperationException("TODO");
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
        // TODO: Look up in configs map
        throw new UnsupportedOperationException("TODO");
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
        // TODO: Iterate benchIds, look up each config, return true if any has preferNatural=true
        throw new UnsupportedOperationException("TODO");
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
        // TODO: Return !denyList.contains(benchId)
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Loads the deny list from the given JSON file path.
     *
     * <p>Expected format:
     * <pre>{@code
     * {
     *   "deniedBenchIds": ["Blueprint"]
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
        // TODO: Read JSON file, parse "deniedBenchIds" array
        // TODO: Return empty set on missing file or parse error (with warning log)
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Resets the registry to its uninitialized state.
     * <strong>Test-only</strong> — allows re-initialization in unit tests.
     */
    static void reset() {
        configs = Collections.emptyMap();
        denyList = Collections.emptySet();
    }
}
