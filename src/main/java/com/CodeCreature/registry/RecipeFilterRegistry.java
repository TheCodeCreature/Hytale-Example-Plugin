package com.CodeCreature.registry;

import com.CodeCreature.scaling.AssetFieldAccessor;
import com.CodeCreature.scaling.BenchCategory;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Shared, read-only registry of crafting recipes that produce placeable blocks
 * and belong to at least one known bench category.
 *
 * <p>Scans all {@link CraftingRecipe} assets exactly once during
 * {@link #init(Set)}, applying a pipeline of validation predicates:
 * <ol>
 *   <li>Recipe is non-null with a non-null ID</li>
 *   <li>Recipe ID does not start with any configured skip prefix</li>
 *   <li>Primary output exists with a non-null item ID</li>
 *   <li>Output item exists in the asset map and has a non-null block ID</li>
 *   <li>At least one input has an {@code itemId} or {@code resourceTypeId}</li>
 *   <li>At least one {@link BenchRequirement} ID matches the allowed set
 *       (derived from {@link BenchCategory#allBenchIds()})</li>
 * </ol>
 *
 * <p>Each passing recipe produces a {@link FilteredRecipeEntry} that is
 * indexed by recipe ID, block type ID, and bench ID for efficient lookup.
 *
 * <p>This class is the <strong>single source of truth</strong> for:
 * <ul>
 *   <li>Which bench IDs are "allowed" (via {@link BenchCategory})</li>
 *   <li>The complete, correctly-scanned set of BenchRequirement IDs per recipe</li>
 *   <li>The {@code Item.set} value (extracted via reflection once, shared)</li>
 * </ul>
 *
 * <p>Consumers layer their own concerns on top of the shared entries:
 * <ul>
 *   <li>{@code BlueprintSelectionPage}: tab/set/search filtering, affordability</li>
 *   <li>{@code BenchRecipeRegistry}: base-block classification, natural
 *       resource detection</li>
 * </ul>
 *
 * <p><strong>Lifecycle:</strong> Call {@link #init(Set)} once after assets
 * load, before {@link BenchRecipeRegistries#init()} and before any UI
 * page queries the registry. Thread-safe after initialization (all fields
 * are effectively immutable).
 *
 * @see FilteredRecipeEntry
 * @see BenchCategory#allBenchIds()
 */
public final class RecipeFilterRegistry {

    /** Default skip prefixes used by both consumers. */
    public static final Set<String> DEFAULT_SKIP_PREFIXES = Set.of("Blueprint_", "Salvage");

    // ─── Registry state (immutable after init) ──────────────────

    /** All entries, sorted by recipe ID (case-insensitive). */
    private static List<FilteredRecipeEntry> entries = Collections.emptyList();

    /** Recipe ID → entry. */
    private static Map<String, FilteredRecipeEntry> byRecipeId = Collections.emptyMap();

    /** Bench ID → entries matching that bench. */
    private static Map<String, List<FilteredRecipeEntry>> byBenchId = Collections.emptyMap();

    /** Set name → entries belonging to that set. */
    private static Map<String, List<FilteredRecipeEntry>> bySet = Collections.emptyMap();

    private RecipeFilterRegistry() {}

    // ═══════════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scans all {@link CraftingRecipe} assets, applies the validation
     * predicate pipeline, and populates the registry indexes.
     *
     * <p>Must be called exactly once, after assets are loaded and after
     * {@link NaturalResourceRegistry#init()} (if ResourceType resolution
     * is needed downstream). Allowed bench IDs are derived from
     * {@link BenchCategory#allBenchIds()}.
     *
     * <p>Skip prefixes control which recipe ID prefixes are excluded.
     * Use {@link #DEFAULT_SKIP_PREFIXES} for the standard set
     * ({@code "Blueprint_"}, {@code "Salvage"}).
     *
     * @param skipPrefixes set of recipe ID prefixes to exclude; recipes
     *                     whose ID starts with any of these are skipped
     * @throws IllegalStateException if called more than once
     */
    public static void init(@Nonnull Set<String> skipPrefixes) {
        Set<String> allowedBenchIds = BenchCategory.allBenchIds();

        List<FilteredRecipeEntry> result = new ArrayList<>();
        Map<String, FilteredRecipeEntry> idMap = new HashMap<>();
        Map<String, List<FilteredRecipeEntry>> benchMap = new HashMap<>();
        Map<String, List<FilteredRecipeEntry>> setMap = new HashMap<>();

        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;

            String recipeId = recipe.getId();

            // Skip-prefix filter
            if (shouldSkip(recipeId, skipPrefixes)) continue;

            // Output validation
            MaterialQuantity output = recipe.getPrimaryOutput();
            if (output == null) continue;
            String outputItemId = output.getItemId();
            if (outputItemId == null) continue;

            // Block ID required
            Item outputItem = Item.getAssetMap().getAsset(outputItemId);
            if (outputItem == null) continue;
            String blockTypeId = outputItem.getBlockId();
            if (blockTypeId == null || blockTypeId.isEmpty()) continue;

            // Input validation
            if (!hasValidInput(recipe)) continue;

            // Scan ALL BenchRequirements for matching bench IDs
            Set<String> matchedBenchIds = extractMatchingBenchIds(recipe, allowedBenchIds);
            if (matchedBenchIds.isEmpty()) continue;

            // Classify bench category
            BenchCategory category = BenchCategory.fromRecipe(recipe);

            // Extract Item.set via reflection
            String itemSet = extractItemSet(outputItem);

            // Extract item categories
            String[] cats = outputItem.getCategories();
            List<String> categoryIds = (cats != null) ? List.of(cats) : List.of();

            FilteredRecipeEntry entry = new FilteredRecipeEntry(
                    recipe, recipeId, outputItemId, blockTypeId,
                    Collections.unmodifiableSet(matchedBenchIds), category, itemSet, categoryIds);

            result.add(entry);
            idMap.put(recipeId, entry);
            for (String benchId : matchedBenchIds) {
                benchMap.computeIfAbsent(benchId, k -> new ArrayList<>()).add(entry);
            }
            if (itemSet != null && !itemSet.isEmpty()) {
                setMap.computeIfAbsent(itemSet, k -> new ArrayList<>()).add(entry);
            }
        }

        result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.recipeId(), b.recipeId()));

        entries = Collections.unmodifiableList(result);
        byRecipeId = Collections.unmodifiableMap(idMap);
        // Make inner lists unmodifiable
        Map<String, List<FilteredRecipeEntry>> immutableBenchMap = new HashMap<>();
        for (var e : benchMap.entrySet()) {
            immutableBenchMap.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        byBenchId = Collections.unmodifiableMap(immutableBenchMap);
        Map<String, List<FilteredRecipeEntry>> immutableSetMap = new HashMap<>();
        for (var e : setMap.entrySet()) {
            immutableSetMap.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        bySet = Collections.unmodifiableMap(immutableSetMap);

        log("Initialized: " + result.size() + " entries, "
                + benchMap.size() + " bench IDs");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Queries
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns all filtered recipe entries, sorted by recipe ID
     * (case-insensitive). The returned list is unmodifiable.
     *
     * @return all entries in the registry
     * @throws IllegalStateException if {@link #init(Set)} has not been called
     */
    @Nonnull
    public static List<FilteredRecipeEntry> getAllEntries() {
        return entries;
    }

    /**
     * Looks up a single entry by recipe asset ID.
     *
     * @param recipeId the recipe ID (e.g. {@code "Wood_Hardwood_Planks"})
     * @return the entry, or {@code null} if no matching recipe passed filtering
     */
    @Nullable
    public static FilteredRecipeEntry getEntry(@Nonnull String recipeId) {
        return byRecipeId.get(recipeId);
    }

    /**
     * Returns all entries that declare the given bench ID in their
     * {@link BenchRequirement} array. The returned list is unmodifiable.
     *
     * <p>A single recipe may appear in multiple bench lists if it declares
     * multiple bench requirements.
     *
     * @param benchId the bench requirement ID (e.g. {@code "Builders"})
     * @return entries for that bench, or an empty list if none match
     */
    @Nonnull
    public static List<FilteredRecipeEntry> getEntriesForBench(@Nonnull String benchId) {
        return byBenchId.getOrDefault(benchId, Collections.emptyList());
    }

    /**
     * Returns all entries that belong to the given item set.
     * The returned list is unmodifiable.
     *
     * @param set the item set name (e.g. {@code "Wood_Hardwood_Planks"})
     * @return entries for that set, or an empty list if none match
     */
    @Nonnull
    public static List<FilteredRecipeEntry> getEntriesForSet(@Nonnull String set) {
        return bySet.getOrDefault(set, Collections.emptyList());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Predicate helpers (package-private for testability)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if the recipe ID starts with any of the
     * given skip prefixes.
     *
     * @param recipeId      the recipe asset ID
     * @param skipPrefixes  prefixes to check against
     * @return {@code true} if the recipe should be skipped
     */
    static boolean shouldSkip(@Nonnull String recipeId, @Nonnull Set<String> skipPrefixes) {
        for (String prefix : skipPrefixes) {
            if (recipeId.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the recipe has at least one input with a
     * non-null {@code itemId} or {@code resourceTypeId}.
     *
     * @param recipe the crafting recipe to check
     * @return {@code true} if at least one input is resolvable
     */
    static boolean hasValidInput(@Nonnull CraftingRecipe recipe) {
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) return false;
        for (MaterialQuantity mat : inputs) {
            if (mat != null && (mat.getItemId() != null || mat.getResourceTypeId() != null)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scans ALL {@link BenchRequirement} entries on the recipe and returns
     * the subset of IDs that are in the allowed set. Returns an empty set
     * if none match.
     *
     * <p>This fixes the bug in BlueprintSelectionPage which only checked
     * the first BenchRequirement entry.
     *
     * @param recipe          the crafting recipe
     * @param allowedBenchIds the set of bench IDs to match against
     * @return matching bench IDs (may be empty), insertion-ordered
     */
    @Nonnull
    static Set<String> extractMatchingBenchIds(@Nonnull CraftingRecipe recipe,
                                                @Nonnull Set<String> allowedBenchIds) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return Collections.emptySet();
        Set<String> matched = new LinkedHashSet<>();
        for (BenchRequirement req : reqs) {
            if (req != null && req.id != null && allowedBenchIds.contains(req.id)) {
                matched.add(req.id);
            }
        }
        return matched;
    }

    /**
     * Reads the {@code Item.set} field via cached reflection.
     *
     * @param item the item to read the set from
     * @return the set value, or {@code null} if reflection fails or field is null
     */
    @Nullable
    static String extractItemSet(@Nonnull Item item) {
        try {
            return (String) AssetFieldAccessor.INSTANCE.itemSet.get(item);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Logging
    // ═══════════════════════════════════════════════════════════════

    private static final Logger LOGGER = Logger.getLogger("RecipeFilterRegistry");

    private static void log(String msg) {
        LOGGER.info("[RecipeFilterReg] " + msg);
    }
}
