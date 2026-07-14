package com.CodeCreature.registry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

/**
 * Maps raw bench IDs (discovered from recipes) into grouped tab IDs for
 * the Stencil Crafting UI.
 *
 * <p>Supports two grouping modes:
 * <ul>
 *   <li><b>Manual merge</b> — explicit groups defined in
 *       {@code bench-tab-groups.json}. Takes precedence over auto-merge.</li>
 *   <li><b>Auto-merge</b> — benches sharing a normalized prefix are
 *       automatically combined (e.g., "Furniture_Bench" + "Furniture Misc"
 *       → "Furniture").</li>
 * </ul>
 *
 * <p><b>Lifecycle:</b> Created by {@link BenchRegistry#init()} after bench
 * discovery. All public methods are thread-safe (state is immutable after
 * construction).
 *
 * <p><b>Key invariant:</b> Every raw bench ID maps to exactly one group key.
 * Ungrouped benches map to themselves (identity mapping).
 */
public final class BenchTabGrouper {

    /** Suffixes stripped during auto-merge normalization (lowercase). */
    private static final List<String> AUTO_MERGE_SUFFIXES = List.of("bench", "misc", "table");

    /**
     * A group of bench IDs displayed as a single tab.
     *
     * @param key         stable group identifier (used as {@code activeTab} value)
     * @param displayName human-readable name shown in tab tooltip
     * @param benchIds    the raw bench IDs that belong to this group (unmodifiable)
     */
    public record TabGroup(
            @Nonnull String key,
            @Nonnull String displayName,
            @Nonnull Set<String> benchIds
    ) {}

    // ─── Config DTOs ────────────────────────────────────────────

    /**
     * Deserialized content of {@code bench-tab-groups.json}.
     *
     * @param groups           manually configured groups; processed first
     * @param autoMergeEnabled when true, unclaimed bench IDs are auto-merged
     *                         by shared prefix; defaults to true
     * @param suffixes         suffixes stripped during auto-merge normalization
     */
    record TabGroupConfig(
            @Nonnull List<ManualGroup> groups,
            boolean autoMergeEnabled,
            @Nonnull List<String> suffixes,
            @Nonnull Map<String, BenchOverride> benchOverrides,
            @Nonnull List<String> skipPrefixes,
            @Nonnull Set<String> excludedTabs,
            @Nonnull Map<String, String> tabIcons
    ) {}

    /**
     * Per-bench configuration override from the config file.
     *
     * @param benchId       the bench ID this override applies to
     * @param preferNatural whether this bench prefers natural items
     */
    record BenchOverride(@Nonnull String benchId, boolean preferNatural) {}

    /**
     * A single manual group entry from the config file.
     *
     * @param displayName the tab tooltip / group key
     * @param benchIds    raw bench IDs to merge under this name
     */
    record ManualGroup(
            @Nonnull String displayName,
            @Nonnull List<String> benchIds
    ) {}

    // ─── State (immutable after construction) ───────────────────

    /** Path prefix for tab icons in GroupIcons directory (relative to page .ui). */
    private static final String ICON_PATH_PREFIX = "../../Common/GroupIcons/";

    /** Default tab icon path (relative to the .ui file). */
    private static final String DEFAULT_TAB_ICON = "../../Common/RecipesIcon.png";

    /** Tab group key or bench ID → icon filename (case-insensitive lookup). */
    private final Map<String, String> tabIcons;

    /** Raw bench ID → group key. Every known raw ID has an entry. */
    private final Map<String, String> rawToGroupKey;

    /** Group key → TabGroup. Includes both merged groups and identity (single-bench) entries. */
    private final Map<String, TabGroup> groups;

    /** Ordered group keys for tab display (sorted case-insensitive). */
    private final List<String> orderedTabIds;

    /** Tab group keys hidden from UI display. */
    private final Set<String> excludedTabs;

    private BenchTabGrouper(Map<String, String> rawToGroupKey,
                            Map<String, TabGroup> groups,
                            List<String> orderedTabIds,
                            Set<String> excludedTabs,
                            Map<String, String> tabIcons) {
        this.rawToGroupKey = Collections.unmodifiableMap(rawToGroupKey);
        this.groups = Collections.unmodifiableMap(groups);
        this.orderedTabIds = Collections.unmodifiableList(orderedTabIds);
        this.excludedTabs = Collections.unmodifiableSet(excludedTabs);
        this.tabIcons = Collections.unmodifiableMap(tabIcons);
    }

    // ─── Factory ────────────────────────────────────────────────

    /**
     * Creates a {@code BenchTabGrouper} from discovered bench IDs and an
     * optional JSON config file.
     *
     * <p>Processing order:
     * <ol>
     *   <li>Load config from {@code configPath} (or use defaults if missing)</li>
     *   <li>Apply manual groups — claim bench IDs, log warnings for duplicates</li>
     *   <li>Apply auto-merge on remaining unclaimed bench IDs (if enabled)</li>
     *   <li>Create identity mappings for any still-unclaimed bench IDs</li>
     * </ol>
     *
     * @param rawBenchIds all bench IDs discovered by {@link BenchRegistry}
     * @param configPath  path to {@code bench-tab-groups.json}; may be null
     *                    or point to a nonexistent file (defaults apply)
     * @return a fully initialized grouper; never null
     */
    @Nonnull
    public static BenchTabGrouper create(@Nonnull Set<String> rawBenchIds,
                                         @Nullable Path configPath) {
        TabGroupConfig config = loadConfig(configPath);
        List<String> suffixes = config.suffixes();

        Map<String, String> rawToGroupKey = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, TabGroup> groupMap = new LinkedHashMap<>();
        Set<String> claimed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1. Apply manual groups (case-insensitive matching; first group wins)
        for (ManualGroup mg : config.groups()) {
            Set<String> matched = new LinkedHashSet<>();
            for (String mgBenchId : mg.benchIds()) {
                // Case-insensitive match against raw bench IDs
                for (String rawId : rawBenchIds) {
                    if (rawId.equalsIgnoreCase(mgBenchId)) {
                        if (claimed.contains(rawId)) {
                            DebugLogger.log(REGISTRY, Level.WARNING,
                                    "[BenchTabGrouper] Bench ID '" + rawId +
                                    "' already claimed by another group; skipping in group '" + mg.displayName() + "'");
                            continue;
                        }
                        matched.add(rawId);
                        claimed.add(rawId);
                        rawToGroupKey.put(rawId, mg.displayName());
                    }
                }
            }
            if (!matched.isEmpty()) {
                groupMap.put(mg.displayName(), new TabGroup(
                        mg.displayName(), mg.displayName(), Collections.unmodifiableSet(matched)));
            }
        }

        // 2. Auto-merge unclaimed IDs (if enabled)
        if (config.autoMergeEnabled()) {
            Set<String> unclaimed = new LinkedHashSet<>();
            for (String rawId : rawBenchIds) {
                if (!claimed.contains(rawId)) {
                    unclaimed.add(rawId);
                }
            }

            // Group by normalized root key
            Map<String, List<String>> rootGroups = new LinkedHashMap<>();
            for (String rawId : unclaimed) {
                String root = normalizeForAutoMerge(rawId, suffixes);
                rootGroups.computeIfAbsent(root, k -> new ArrayList<>()).add(rawId);
            }

            for (Map.Entry<String, List<String>> entry : rootGroups.entrySet()) {
                List<String> members = entry.getValue();
                if (members.size() > 1) {
                    // Multi-member group: title-case the root key
                    String root = entry.getKey();
                    String displayName = root.substring(0, 1).toUpperCase(Locale.ROOT) + root.substring(1);
                    Set<String> memberSet = new LinkedHashSet<>(members);
                    for (String rawId : members) {
                        rawToGroupKey.put(rawId, displayName);
                        claimed.add(rawId);
                    }
                    groupMap.put(displayName, new TabGroup(
                            displayName, displayName, Collections.unmodifiableSet(memberSet)));
                }
            }
        }

        // 3. Identity mappings for remaining unclaimed IDs
        for (String rawId : rawBenchIds) {
            if (!claimed.contains(rawId)) {
                rawToGroupKey.put(rawId, rawId);
                groupMap.put(rawId, new TabGroup(rawId,
                        rawId.replace('_', ' '), Set.of(rawId)));
            }
        }

        // 4. Build ordered tab IDs sorted case-insensitive, excluding hidden tabs
        Set<String> excludedTabs = config.excludedTabs();
        List<String> orderedTabIds = new ArrayList<>();
        for (String key : groupMap.keySet()) {
            if (!excludedTabs.contains(key)) {
                orderedTabIds.add(key);
            }
        }
        orderedTabIds.sort(String.CASE_INSENSITIVE_ORDER);

        // Warn about excludedTabs that don't match any group
        for (String excluded : excludedTabs) {
            if (!groupMap.containsKey(excluded)) {
                DebugLogger.log(REGISTRY, Level.INFO,
                        "[BenchTabGrouper] excludedTab '" + excluded + "' does not match any tab group");
            }
        }

        DebugLogger.log(REGISTRY, Level.INFO,
                "[BenchTabGrouper] Created " + groupMap.size() + " tab groups from " +
                rawBenchIds.size() + " raw bench IDs (" + excludedTabs.size() + " excluded). Visible tabs: " + orderedTabIds);

        return new BenchTabGrouper(rawToGroupKey, groupMap, orderedTabIds, excludedTabs, config.tabIcons());
    }

    // ─── Config loading ─────────────────────────────────────────

    /**
     * Loads and parses {@code bench-tab-groups.json}.
     *
     * <p>Expected JSON format:
     * <pre>{@code
     * {
     *   "autoMergeEnabled": true,
     *   "groups": [
     *     { "displayName": "Crafting", "benchIds": ["fieldcraft", "workbench"] }
     *   ]
     * }
     * }</pre>
     *
     * <p>If the file is missing, returns a default config with auto-merge
     * enabled and no manual groups. If the file is malformed, logs a
     * warning and returns the default config.
     *
     * @param configPath path to the config file (may be null)
     * @return parsed config; never null
     */
    @Nonnull
    static TabGroupConfig loadConfig(@Nullable Path configPath) {
        Map<String, BenchOverride> defaultOverrides = Map.of(
                "Furniture_Bench", new BenchOverride("Furniture_Bench", true));
        List<String> defaultSkipPrefixes = List.of();
        Set<String> defaultExcludedTabs = Set.of();
        TabGroupConfig defaultConfig = new TabGroupConfig(List.of(), true, AUTO_MERGE_SUFFIXES,
                defaultOverrides, defaultSkipPrefixes, defaultExcludedTabs, Map.of());
        DebugLogger.log(REGISTRY, Level.INFO,
                "[BenchTabGrouper] loadConfig path=" + configPath + 
                " exists=" + (configPath != null && Files.exists(configPath)));
        if (configPath == null || !Files.exists(configPath)) {
            return defaultConfig;
        }
        try {
            String json = Files.readString(configPath);
            BsonDocument doc = BsonDocument.parse(json);

            boolean autoMergeEnabled = doc.getBoolean("autoMergeEnabled", new org.bson.BsonBoolean(true)).getValue();

            // Extract suffixes (default: AUTO_MERGE_SUFFIXES)
            List<String> suffixes;
            if (doc.containsKey("suffixes") && doc.isArray("suffixes")) {
                BsonArray suffixArray = doc.getArray("suffixes");
                suffixes = new ArrayList<>();
                for (BsonValue v : suffixArray) {
                    if (v.isString()) {
                        suffixes.add(v.asString().getValue().toLowerCase(Locale.ROOT));
                    }
                }
                if (suffixes.isEmpty()) {
                    suffixes = AUTO_MERGE_SUFFIXES;
                }
            } else {
                suffixes = AUTO_MERGE_SUFFIXES;
            }

            // Extract groups
            List<ManualGroup> groups = new ArrayList<>();
            if (doc.containsKey("groups") && doc.isArray("groups")) {
                BsonArray groupsArray = doc.getArray("groups");
                for (BsonValue gv : groupsArray) {
                    if (!gv.isDocument()) continue;
                    BsonDocument gDoc = gv.asDocument();
                    String displayName = gDoc.getString("displayName", new org.bson.BsonString("")).getValue();
                    if (displayName.isEmpty()) continue;
                    List<String> benchIds = new ArrayList<>();
                    if (gDoc.containsKey("benchIds") && gDoc.isArray("benchIds")) {
                        for (BsonValue bv : gDoc.getArray("benchIds")) {
                            if (bv.isString()) {
                                benchIds.add(bv.asString().getValue());
                            }
                        }
                    }
                    groups.add(new ManualGroup(displayName, benchIds));
                }
            }

            Map<String, BenchOverride> benchOverrides = new LinkedHashMap<>();
            if (doc.containsKey("benchOverrides")) {
                BsonDocument overridesDoc = doc.getDocument("benchOverrides");
                for (String benchId : overridesDoc.keySet()) {
                    BsonDocument overrideDoc = overridesDoc.getDocument(benchId);
                    boolean preferNatural = overrideDoc.getBoolean("preferNatural",
                            new org.bson.BsonBoolean(false)).getValue();
                    benchOverrides.put(benchId, new BenchOverride(benchId, preferNatural));
                }
            } else {
                benchOverrides.put("Furniture_Bench", new BenchOverride("Furniture_Bench", true));
            }

            List<String> skipPrefixes;
            if (doc.containsKey("skipPrefixes")) {
                BsonArray skipArr = doc.getArray("skipPrefixes");
                skipPrefixes = new ArrayList<>();
                for (BsonValue v : skipArr) {
                    String prefix = v.asString().getValue();
                    if (!prefix.isEmpty()) {
                        skipPrefixes.add(prefix);
                    } else {
                        DebugLogger.log(REGISTRY, Level.WARNING,
                                "[BenchTabGrouper] Ignoring empty skipPrefix entry");
                    }
                }
            } else {
                skipPrefixes = List.of();
            }

            Set<String> excludedTabs;
            if (doc.containsKey("excludedTabs")) {
                BsonArray exclArr = doc.getArray("excludedTabs");
                excludedTabs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (BsonValue v : exclArr) {
                    excludedTabs.add(v.asString().getValue());
                }
            } else {
                excludedTabs = Set.of();
            }

            Map<String, String> tabIcons = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            if (doc.containsKey("tabIcons")) {
                BsonDocument iconsDoc = doc.getDocument("tabIcons");
                for (String key : iconsDoc.keySet()) {
                    BsonValue val = iconsDoc.get(key);
                    if (val.isString() && !val.asString().getValue().isEmpty()) {
                        tabIcons.put(key, val.asString().getValue());
                    }
                }
            }

            DebugLogger.log(REGISTRY, Level.INFO,
                    "[BenchTabGrouper] Loaded config: autoMerge=" + autoMergeEnabled +
                    ", suffixes=" + suffixes + ", " + groups.size() + " manual groups" +
                    ", " + benchOverrides.size() + " bench overrides" +
                    ", skipPrefixes=" + skipPrefixes +
                    ", excludedTabs=" + excludedTabs +
                    ", " + tabIcons.size() + " tab icons");
            return new TabGroupConfig(groups, autoMergeEnabled, suffixes, benchOverrides, skipPrefixes, excludedTabs, tabIcons);
        } catch (IOException e) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                    "[BenchTabGrouper] Failed to read config: " + e.getMessage());
            return defaultConfig;
        } catch (Exception e) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                    "[BenchTabGrouper] Malformed config: " + e.getMessage());
            return defaultConfig;
        }
    }

    // ─── Auto-merge ─────────────────────────────────────────────

    /**
     * Normalizes a raw bench ID for auto-merge prefix matching.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Replace underscores with spaces</li>
     *   <li>Lowercase the entire string</li>
     *   <li>Strip any trailing token that matches a known suffix
     *       ({@link #AUTO_MERGE_SUFFIXES})</li>
     *   <li>Trim whitespace</li>
     * </ol>
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "Furniture_Bench"} → {@code "furniture"}</li>
     *   <li>{@code "Furniture Misc"} → {@code "furniture"}</li>
     *   <li>{@code "Architectsbench"} → {@code "architects"}</li>
     *   <li>{@code "Stonecutter"} → {@code "stonecutter"}</li>
     * </ul>
     *
     * @param rawBenchId the raw bench ID
     * @param suffixes   suffixes to strip (lowercase)
     * @return normalized root key (lowercase, suffixes stripped)
     */
    static String normalizeForAutoMerge(@Nonnull String rawBenchId, @Nonnull List<String> suffixes) {
        // Replace underscores with spaces, lowercase
        String normalized = rawBenchId.replace('_', ' ').toLowerCase(Locale.ROOT).trim();

        // Strip trailing token matching a suffix (space-separated)
        for (String suffix : suffixes) {
            if (normalized.endsWith(" " + suffix)) {
                normalized = normalized.substring(0, normalized.length() - suffix.length() - 1).trim();
                break;
            }
        }

        // Handle suffix attached without space (e.g., "architectsbench" → "architects")
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()
                    && !normalized.contains(" ")) {
                normalized = normalized.substring(0, normalized.length() - suffix.length()).trim();
                break;
            }
        }

        return normalized;
    }

    // ─── Public queries ─────────────────────────────────────────

    /**
     * Resolves a raw bench ID to its group key.
     *
     * <p>If the bench ID belongs to a manual or auto-merged group, returns
     * the group's key (which is used as the {@code activeTab} value and
     * stored in {@code RecipeEntry.benchId()}).
     *
     * <p>If the bench ID is ungrouped, returns the raw bench ID itself
     * (identity mapping).
     *
     * <p>If the bench ID is completely unknown (not passed to
     * {@link #create}), returns the raw bench ID unchanged and logs a
     * warning.
     *
     * @param rawBenchId the raw bench ID from a recipe's BenchRequirement
     * @return the group key to use as the recipe's effective bench ID
     */
    @Nonnull
    public String resolveTabId(@Nonnull String rawBenchId) {
        String groupKey = rawToGroupKey.get(rawBenchId);
        if (groupKey == null) {
            DebugLogger.log(REGISTRY, Level.WARNING,
                    "[BenchTabGrouper] Unknown bench ID '" + rawBenchId + "' — returning as-is");
            return rawBenchId;
        }
        return groupKey;
    }

    /**
     * Expands a group key to the set of raw bench IDs it contains.
     *
     * <p>For merged groups, returns all constituent bench IDs. For
     * ungrouped (identity) entries, returns a singleton set.
     *
     * <p>Useful for diagnostics and logging; NOT needed by the filter
     * pipeline (which compares group keys directly).
     *
     * @param groupKey the group key (as returned by {@link #resolveTabId})
     * @return unmodifiable set of raw bench IDs; empty if unknown key
     */
    @Nonnull
    public Set<String> expandTabId(@Nonnull String groupKey) {
        TabGroup group = groups.get(groupKey);
        return group != null ? group.benchIds() : Set.of();
    }

    /**
     * Returns the human-readable display name for a tab group.
     *
     * <p>For manual groups, this is the configured {@code displayName}.
     * For auto-merged groups, this is the title-cased root key.
     * For ungrouped benches, this is the raw bench ID with underscores
     * replaced by spaces (matching current {@code tabDisplayName()} behavior).
     *
     * @param groupKey the group key
     * @return display name suitable for tab tooltips; never null
     */
    @Nonnull
    public String getDisplayName(@Nonnull String groupKey) {
        TabGroup group = groups.get(groupKey);
        return group != null ? group.displayName() : groupKey.replace('_', ' ');
    }

    /**
     * Returns {@code true} if the given tab group key is excluded from UI display.
     *
     * @param tabId the tab group key to check
     * @return true if this tab should be hidden
     */
    public boolean isTabExcluded(@Nonnull String tabId) {
        return excludedTabs.contains(tabId);
    }

    /**
     * Resolves an explicitly configured icon path for a tab group key.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Direct match on {@code groupKey} in configured {@code tabIcons}</li>
     *   <li>First matching bench ID from the group's constituent bench IDs</li>
     * </ol>
     *
     * @param groupKey the tab group key to resolve an icon for
     * @return configured icon path, or {@code null} when no explicit mapping exists
     */
    @Nullable
    public String resolveMappedTabIcon(@Nonnull String groupKey) {
        // Step 1: Direct match on group key
        String icon = tabIcons.get(groupKey);
        if (icon != null) {
            return normalizeTabIconPath(icon);
        }

        // Step 2: Try constituent bench IDs
        TabGroup group = groups.get(groupKey);
        if (group != null) {
            for (String benchId : group.benchIds()) {
                icon = tabIcons.get(benchId);
                if (icon != null) {
                    return normalizeTabIconPath(icon);
                }
            }
        }

        return null;
    }

    /**
     * Returns whether an explicit tab icon mapping exists for a group key or one of its members.
     */
    public boolean hasMappedTabIcon(@Nonnull String groupKey) {
        return resolveMappedTabIcon(groupKey) != null;
    }

    /**
     * Resolves the icon path for a tab group key, with default fallback.
     */
    @Nonnull
    public String resolveTabIcon(@Nonnull String groupKey) {
        String mapped = resolveMappedTabIcon(groupKey);
        return mapped != null ? mapped : DEFAULT_TAB_ICON;
    }

    /**
     * Normalizes a configured tab icon value into a UI-relative asset path.
     * Accepts either a bare filename (e.g. "Blocks.png") or an explicit path.
     */
    @Nonnull
    private static String normalizeTabIconPath(@Nonnull String iconValue) {
        if (iconValue.contains("/") || iconValue.contains("\\")) {
            return java.util.Objects.requireNonNull(iconValue.replace('\\', '/'));
        }
        return ICON_PATH_PREFIX + iconValue;
    }

    /**
     * Returns the ordered list of group keys for tab construction.
     *
     * <p>Sorted case-insensitive alphabetically. Does NOT include the "All"
     * tab — that is added by {@code BlueprintSelectionPage.buildBenchTabs()}.
     *
     * @return unmodifiable ordered list of group keys
     */
    @Nonnull
    public List<String> getOrderedTabIds() {
        return orderedTabIds;
    }
}
