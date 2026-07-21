package com.CodeCreature.ui.bench;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Sequential, composable filter pipeline for Stencil Crafting recipes.
 *
 * <p>Replaces the interleaved filtering logic previously split across
 * {@code StencilSelectionPage.applyFilter()} and {@code buildRecipeList()}.
 * Each stage has a single responsibility: take a list, produce a list.
 * Affordability is computed <b>once</b> in a dedicated tagging stage.
 *
 * <h3>Pipeline stages (executed in order):</h3>
 * <ol>
 *   <li><b>filterByTab</b> — retain recipes matching the active bench tab</li>
 *   <li><b>filterBySearch</b> — retain recipes matching the search query</li>
 *   <li><b>tagAffordability</b> — compute {@code affordable} boolean per recipe;
 *       normalize null sets to {@link #UNCATEGORIZED_SET}</li>
 *   <li><b>extractSets</b> — derive sidebar set list from tagged recipes</li>
 *   <li><b>filterBySets</b> — retain recipes matching selected sets</li>
 *   <li><b>sort</b> — group by set, affordable-first, then alphabetical</li>
 * </ol>
 *
 * <p>The pipeline is a pure function: no UI state, no side effects, fully testable.
 */
public final class RecipeFilterPipeline {

    /** Synthetic set name assigned to recipes whose {@code Item.set} is null. */
    static final String UNCATEGORIZED_SET = "Uncategorized";

    /** Tab value that means "show all tabs". */
    private static final String ALL_TAB = "All";

    // ─── Input / Output types ───────────────────────────────────

    /**
     * A recipe entering the pipeline. Contains only registry-sourced data;
     * no affordability information.
     *
         * @param recipeId           recipe asset ID (e.g. "Wood_Hardwood_Planks")
         * @param outputItemId       output item asset ID
         * @param blockTypeId        output block type ID
         * @param benchIds           resolved bench group keys for tab filtering
         * @param set                {@code Item.set} value; may be {@code null}
         * @param searchableName     precomputed display/search name text; may be {@code null}
         * @param searchableDescription precomputed description text; may be {@code null}
     */
    public record InputRecipe(
            String recipeId,
            String outputItemId,
            String blockTypeId,
            Set<String> benchIds,
            @Nullable String set,
            List<String> categoryIds,
            @Nullable String searchableName,
            @Nullable String searchableDescription
    ) {}

    /**
     * A recipe exiting the pipeline, enriched with affordability and a
     * guaranteed non-null {@code effectiveSet}.
     *
     * @param recipeId      recipe asset ID
     * @param outputItemId  output item asset ID
     * @param blockTypeId   output block type ID
     * @param benchIds      resolved bench group keys
     * @param effectiveSet  never null; equals original set or {@link #UNCATEGORIZED_SET}
     * @param affordable    {@code true} if the player can craft this recipe
     *                      (raw materials OR BlockGroup interchangeability)
     */
    public record TaggedRecipe(
            String recipeId,
            String outputItemId,
            String blockTypeId,
            Set<String> benchIds,
            String effectiveSet,
            boolean affordable,
            List<String> categoryIds
    ) {}

    public record CategoryInfo(
            String categoryId,
            String displayName,
            String iconPath,
            int sortOrder
    ) {}

    /**
     * A material group derived from ItemCategory metadata.
     *
     * @param categoryId  the category asset ID
     * @param displayName human-readable name
     * @param iconPath    icon asset path
     * @param sortOrder   sort priority
     */
    public record MaterialGroup(
            String categoryId,
            String displayName,
            String iconPath,
            int sortOrder
    ) {}

    /**
     * Complete output of a pipeline execution.
     *
     * @param displayedRecipes recipes to render in the grid, sorted and filtered
     * @param currentSets      set names to show in the sidebar, sorted case-insensitive
     * @param currentGroups    all available material groups (for group bar)
     */
    public record PipelineResult(
            List<TaggedRecipe> displayedRecipes,
            List<String> currentSets,
            List<MaterialGroup> currentGroups
    ) {}

    /**
     * Functional interface for affordability checks.
     *
     * <p>Implementations should account for both raw material availability
     * (does the player have all required ingredients?) and BlockGroup
     * interchangeability (does the player own any member of the output's
     * FullBlocks group, enabling free conversion?).
     *
     * <p>The pipeline calls this exactly once per recipe per execution.
     * Implementations may safely perform asset lookups and inventory scans.
     */
    @FunctionalInterface
    public interface AffordabilityChecker {

        /**
         * Determines whether the given recipe is affordable for the current player.
         *
         * @param recipe the recipe to check (provides recipeId for CraftingRecipe
         *               lookup and outputItemId for BlockGroup lookup)
         * @return {@code true} if the player can craft this recipe or obtain the
         *         output via BlockGroup conversion
         */
        boolean isAffordable(InputRecipe recipe);
    }

    /**
     * Functional interface for resource type matching.
     *
     * <p>Implementations check whether a recipe's inputs match any of the
     * currently selected resource types. Used in Resource Planning affordability
     * mode to tag recipes as matching/non-matching.
     *
     * <p>The pipeline calls this exactly once per recipe per execution.
     */
    @FunctionalInterface
    public interface ResourceTypeChecker {
        boolean matchesResourceType(InputRecipe recipe);
    }

    // ─── Pipeline execution ─────────────────────────────────────

    /**
     * Executes the full filter pipeline.
     *
     * <p>Stages run sequentially with no backtracking:
     * <pre>
     * allRecipes → filterByTab → filterBySearch → tagAffordability
     *            → extractMaterialGroups (→ currentGroups)
     *            → filterByMaterialGroups (→ groupFiltered)
     *            → extractSets (→ visibleSets)
     *            → filterBySets → sort (→ displayedRecipes)
     * </pre>
     *
     * @param allRecipes             complete recipe list from the registry (unmodified)
     * @param activeTab              current bench tab; {@code "All"} = no tab filter
     * @param activeMaterialGroups   selected material group prefixes; empty = show all groups
     * @param activeSetFilters       selected set names for sidebar filtering;
     *                               empty = show all sets
     * @param searchQuery            search text; empty or null = no search filter
     * @param checker                affordability checker; {@code null} = all recipes
     *                               are considered affordable
     * @param affordableOnly         when true, unaffordable recipes are removed before
     *                               extracting sets and building the display list;
     *                               sets with zero affordable recipes will not appear
     * @return pipeline result containing displayed recipes, sidebar sets, and material groups
     */
    public PipelineResult execute(
            List<InputRecipe> allRecipes,
            String activeTab,
            Set<String> activeMaterialGroups,
            Set<String> activeSetFilters,
            String searchQuery,
            @Nullable AffordabilityChecker checker,
            boolean affordableOnly,
            Map<String, CategoryInfo> categoryInfoMap,
            @Nullable ResourceTypeChecker resourceTypeChecker
    ) {
        List<InputRecipe> tabFiltered = filterByTab(allRecipes, activeTab);
        List<InputRecipe> searchFiltered = filterBySearch(tabFiltered, searchQuery);

        // Route to the appropriate tagging method based on which checker is provided
        List<TaggedRecipe> tagged;
        if (resourceTypeChecker != null && checker == null) {
            tagged = tagResourceTypeMatch(searchFiltered, resourceTypeChecker);
        } else {
            tagged = tagAffordability(searchFiltered, checker);
        }

        // Base for sidebar extraction: optionally filter by affordability
        List<TaggedRecipe> affordableBase = affordableOnly ? filterByAffordability(tagged) : tagged;

        // Categories: derived from set-filtered base (selecting a set narrows categories)
        List<TaggedRecipe> setOnlyFiltered = filterBySets(affordableBase, activeSetFilters);
        List<MaterialGroup> currentGroups = extractMaterialGroups(setOnlyFiltered, categoryInfoMap, 25);

        // Filter by active material groups (category-based)
        List<TaggedRecipe> groupFiltered = filterByMaterialGroups(tagged, activeMaterialGroups);

        // Sets: derived from category-filtered base (selecting a category narrows sets)
        List<TaggedRecipe> groupOnlyBase = filterByMaterialGroups(affordableBase, activeMaterialGroups);
        List<String> visibleSets = extractSets(groupOnlyBase);
        // Always include uncategorized if present
        {
            boolean hasUncategorized = groupOnlyBase.stream()
                    .anyMatch(r -> UNCATEGORIZED_SET.equals(r.effectiveSet()));
            if (hasUncategorized) {
                visibleSets.add(UNCATEGORIZED_SET);
            }
        }

        // Restrict to selected sets or all visible sets
        Set<String> effectiveSetFilter = (activeSetFilters != null && !activeSetFilters.isEmpty())
                ? activeSetFilters
                : new TreeSet<>(visibleSets);
        List<TaggedRecipe> setFiltered = filterBySets(groupFiltered, effectiveSetFilter);
        List<TaggedRecipe> sorted = sort(setFiltered);
        return new PipelineResult(sorted, visibleSets, currentGroups);
    }

    // ─── Individual stages (package-private for testing) ────────

    /**
     * Stage 1: Retain recipes matching the active bench tab.
     *
     * <p>If {@code activeTab} is {@code "All"}, all recipes pass through.
     * Otherwise, only recipes whose {@code benchId} equals {@code activeTab}
     * (case-sensitive) are retained.
     *
     * @param recipes   input recipe list
     * @param activeTab the tab to filter by
     * @return new list containing only matching recipes
     */
    List<InputRecipe> filterByTab(List<InputRecipe> recipes, String activeTab) {
        if (ALL_TAB.equals(activeTab)) {
            return new ArrayList<>(recipes);
        }
        List<InputRecipe> result = new ArrayList<>();
        for (InputRecipe recipe : recipes) {
            if (recipe.benchIds().contains(activeTab)) {
                result.add(recipe);
            }
        }
        return result;
    }

    /**
     * Stage 2: Retain recipes matching the search query.
     *
    * <p>Matches against recipe IDs and user-facing metadata using fuzzy logic:
    * {@code recipeId}, {@code outputItemId}, {@code blockTypeId}, {@code set},
    * {@code searchableName}, and {@code searchableDescription}.
    *
    * <p>Matching rules (case-insensitive):
    * <ul>
    *   <li>Direct substring match</li>
    *   <li>Ordered-subsequence match (for shorthand queries)</li>
    *   <li>Per-token edit-distance match (for typos)</li>
    * </ul>
    *
    * <p>If query is null or empty, all recipes pass through.
     *
     * @param recipes input recipe list
     * @param query   search text (may be null or empty)
     * @return new list containing only matching recipes
     */
    List<InputRecipe> filterBySearch(List<InputRecipe> recipes, @Nullable String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(recipes);
        }
        String normalizedQuery = normalizeSearchText(query);
        if (normalizedQuery.isEmpty()) {
            return new ArrayList<>(recipes);
        }

        String[] terms = normalizedQuery.split("\\s+");
        List<InputRecipe> result = new ArrayList<>();
        for (InputRecipe recipe : recipes) {
            List<String> fields = buildSearchFields(recipe);
            if (matchesAllTerms(terms, fields)) {
                result.add(recipe);
            }
        }
        return result;
    }

    private static List<String> buildSearchFields(InputRecipe recipe) {
        List<String> fields = new ArrayList<>(6);
        fields.add(normalizeSearchText(recipe.recipeId()));
        fields.add(normalizeSearchText(recipe.outputItemId()));
        fields.add(normalizeSearchText(recipe.blockTypeId()));
        fields.add(normalizeSearchText(recipe.set()));
        fields.add(normalizeSearchText(recipe.searchableName()));
        fields.add(normalizeSearchText(recipe.searchableDescription()));
        return fields;
    }

    private static boolean matchesAllTerms(String[] terms, List<String> fields) {
        for (String term : terms) {
            if (term.isEmpty()) {
                continue;
            }
            boolean matched = false;
            for (String field : fields) {
                if (field.isEmpty()) {
                    continue;
                }
                if (matchesTerm(term, field)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesTerm(String term, String field) {
        if (field.contains(term)) {
            return true;
        }

        String compactTerm = compactAlnum(term);
        String compactField = compactAlnum(field);
        // Keep shorthand matching for compact abbreviations (e.g., "hwp").
        // Allow slightly longer consonant-style shorthand (e.g., "hrdwd"),
        // but avoid applying this to normal words like "cloth".
        boolean allowSubsequence = compactTerm.length() <= 4
            || (compactTerm.length() <= 6 && isConsonantAbbreviation(compactTerm));
        if (allowSubsequence && !compactTerm.isEmpty()
                && isSubsequence(compactTerm, compactField)) {
            return true;
        }

        if (compactTerm.length() < 4) {
            return false;
        }

        String[] fieldTokens = field.split("\\s+");
        int maxDistance = maxTyposForTerm(compactTerm.length());
        for (String fieldToken : fieldTokens) {
            String compactToken = compactAlnum(fieldToken);
            if (compactToken.isEmpty()) {
                continue;
            }
            if (compactToken.length() < 4) {
                continue;
            }
            if (compactToken.charAt(0) != compactTerm.charAt(0)) {
                continue;
            }
            int lengthDelta = Math.abs(compactToken.length() - compactTerm.length());
            if (lengthDelta > maxDistance) {
                continue;
            }
            if (levenshteinDistance(compactTerm, compactToken, maxDistance) <= maxDistance) {
                return true;
            }
        }

        return false;
    }

    private static int maxTyposForTerm(int length) {
        if (length <= 4) return 1;
        if (length <= 8) return 2;
        return 3;
    }

    private static String normalizeSearchText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ');
        StringBuilder normalized = new StringBuilder(lowered.length());
        boolean previousWasSpace = true;
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                normalized.append(' ');
                previousWasSpace = true;
            }
        }
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == ' ') {
            end--;
        }
        return normalized.substring(0, end);
    }

    private static String compactAlnum(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder compact = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                compact.append(c);
            }
        }
        return compact.toString();
    }

    private static boolean isSubsequence(String needle, String haystack) {
        if (needle.isEmpty()) {
            return true;
        }
        int j = 0;
        for (int i = 0; i < haystack.length() && j < needle.length(); i++) {
            if (haystack.charAt(i) == needle.charAt(j)) {
                j++;
            }
        }
        return j == needle.length();
    }

    private static boolean isConsonantAbbreviation(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') {
                return false;
            }
        }
        return true;
    }

    private static int levenshteinDistance(String a, String b, int cutoff) {
        if (a.equals(b)) {
            return 0;
        }
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int minInRow = current[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                int insertion = current[j - 1] + 1;
                int deletion = previous[j] + 1;
                int substitution = previous[j - 1] + cost;
                int best = Math.min(Math.min(insertion, deletion), substitution);
                current[j] = best;
                if (best < minInRow) {
                    minInRow = best;
                }
            }
            if (minInRow > cutoff) {
                return cutoff + 1;
            }
            int[] tmp = previous;
            previous = current;
            current = tmp;
        }

        return previous[b.length()];
    }

    /**
     * Stage 3: Convert InputRecipes to TaggedRecipes with affordability.
     *
     * <p>For each recipe:
     * <ul>
     *   <li>Normalizes null {@code set} to {@link #UNCATEGORIZED_SET}</li>
     *   <li>Computes {@code affordable} via the checker (or {@code true}
     *       if checker is null)</li>
     * </ul>
     *
     * <p>This is the <b>only</b> stage that calls the affordability checker.
     *
     * @param recipes input recipe list
     * @param checker affordability checker; null = all affordable
     * @return new list of TaggedRecipe with affordability set
     */
    /**
     * Stage 3 (alt): Convert InputRecipes to TaggedRecipes with resource type matching.
     *
     * <p>Mirrors {@link #tagAffordability} but uses a {@link ResourceTypeChecker}
     * to determine the {@code affordable} flag based on whether the recipe's
     * inputs match any selected resource type.
     *
     * @param recipes input recipe list
     * @param checker resource type checker; null = all matching
     * @return new list of TaggedRecipe with affordable set based on resource type match
     */
    List<TaggedRecipe> tagResourceTypeMatch(List<InputRecipe> recipes,
                                            @Nullable ResourceTypeChecker checker) {
        List<TaggedRecipe> result = new ArrayList<>();
        for (InputRecipe recipe : recipes) {
            String effectiveSet = (recipe.set() != null && !recipe.set().isEmpty())
                    ? recipe.set() : UNCATEGORIZED_SET;
            boolean affordable = (checker != null) ? checker.matchesResourceType(recipe) : true;
            result.add(new TaggedRecipe(
                    recipe.recipeId(),
                    recipe.outputItemId(),
                    recipe.blockTypeId(),
                    recipe.benchIds(),
                    effectiveSet,
                    affordable,
                    recipe.categoryIds()
            ));
        }
        return result;
    }

    List<TaggedRecipe> tagAffordability(List<InputRecipe> recipes,
                                        @Nullable AffordabilityChecker checker) {
        List<TaggedRecipe> result = new ArrayList<>();
        for (InputRecipe recipe : recipes) {
            String effectiveSet = (recipe.set() != null && !recipe.set().isEmpty())
                    ? recipe.set() : UNCATEGORIZED_SET;
            boolean affordable = (checker != null) ? checker.isAffordable(recipe) : true;
            result.add(new TaggedRecipe(
                    recipe.recipeId(),
                    recipe.outputItemId(),
                    recipe.blockTypeId(),
                    recipe.benchIds(),
                    effectiveSet,
                    affordable,
                    recipe.categoryIds()
            ));
        }
        return result;
    }

    /**
     * Stage 3b: Remove unaffordable recipes.
     *
     * <p>Used when "Affordable Only" is active. Removes recipes tagged
     * as unaffordable so they do not appear in the grid and their sets
     * are excluded from the sidebar when no affordable recipes remain
     * in that set.
     *
     * @param recipes tagged recipe list
     * @return new list containing only affordable recipes
     */
    List<TaggedRecipe> filterByAffordability(List<TaggedRecipe> recipes) {
        List<TaggedRecipe> result = new ArrayList<>();
        for (TaggedRecipe recipe : recipes) {
            if (recipe.affordable()) {
                result.add(recipe);
            }
        }
        return result;
    }

    /**
     * Stage 4: Extract unique set names from tagged recipes.
     *
     * <p>Returns a sorted (case-insensitive) list of all distinct
     * {@code effectiveSet} values present in the recipe list. This becomes
     * the sidebar set list ({@code currentSets}).
     *
     * <p>Called <b>before</b> set filtering so the sidebar reflects all
     * available sets, not just the selected ones.
     *
     * @param recipes tagged recipe list
     * @return sorted list of unique set names
     */
    List<String> extractSets(List<TaggedRecipe> recipes) {
        TreeSet<String> sets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (TaggedRecipe recipe : recipes) {
            sets.add(recipe.effectiveSet());
        }
        sets.remove(UNCATEGORIZED_SET);
        return new ArrayList<>(sets);
    }

    /**
     * Stage 5: Retain recipes matching the active set filters.
     *
     * <p>If {@code activeSetFilters} is empty, all recipes pass through
     * (equivalent to "All" selected). Otherwise, only recipes whose
     * {@code effectiveSet} is in the filter set are retained.
     *
     * @param recipes          tagged recipe list
     * @param activeSetFilters selected set names; empty = no filtering
     * @return new list containing only matching recipes
     */
    List<TaggedRecipe> filterBySets(List<TaggedRecipe> recipes, Set<String> activeSetFilters) {
        if (activeSetFilters == null || activeSetFilters.isEmpty()) {
            return new ArrayList<>(recipes);
        }
        TreeSet<String> filterSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        filterSet.addAll(activeSetFilters);
        List<TaggedRecipe> result = new ArrayList<>();
        for (TaggedRecipe recipe : recipes) {
            if (filterSet.contains(recipe.effectiveSet())) {
                result.add(recipe);
            }
        }
        return result;
    }

    /**
     * Stage 6: Sort recipes for display.
     *
     * <p>Sort order:
     * <ol>
     *   <li>By {@code effectiveSet} (case-insensitive alphabetical)</li>
     *   <li>Affordable recipes before unaffordable within each set</li>
     *   <li>By {@code recipeId} (case-insensitive alphabetical) within
     *       each affordability group</li>
     * </ol>
     *
     * @param recipes tagged recipe list
     * @return new sorted list
     */
    List<TaggedRecipe> sort(List<TaggedRecipe> recipes) {
        List<TaggedRecipe> sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator
                .comparing(TaggedRecipe::effectiveSet, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> !r.affordable())
                .thenComparing(TaggedRecipe::recipeId, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * Derives material groups from category metadata on the tagged recipes.
     *
     * @param recipes         tagged recipes (post-affordability, pre-set-filtering)
     * @param categoryInfoMap category metadata keyed by category ID
     * @param maxGroups       maximum number of groups to return
     * @return sorted list of material groups
     */
    List<MaterialGroup> extractMaterialGroups(List<TaggedRecipe> recipes,
                                              Map<String, CategoryInfo> categoryInfoMap,
                                              int maxGroups) {
        // Collect distinct category IDs present on any recipe
        Set<String> seenCategories = new LinkedHashSet<>();
        for (TaggedRecipe recipe : recipes) {
            if (recipe.categoryIds() != null) {
                seenCategories.addAll(recipe.categoryIds());
            }
        }

        // Build groups from categories that exist in the info map (top-level only)
        List<MaterialGroup> groups = new ArrayList<>();
        for (String catId : seenCategories) {
            CategoryInfo info = categoryInfoMap.get(catId);
            if (info != null) {
                groups.add(new MaterialGroup(info.categoryId(), info.displayName(),
                        info.iconPath(), info.sortOrder()));
            }
        }

        // Sort by sortOrder, then categoryId as tiebreaker
        groups.sort(Comparator.comparingInt(MaterialGroup::sortOrder)
                .thenComparing(MaterialGroup::categoryId, String.CASE_INSENSITIVE_ORDER));
        if (groups.size() > maxGroups) {
            groups = new ArrayList<>(groups.subList(0, maxGroups));
        }
        return groups;
    }

    /**
     * Filters recipes to only include those with at least one category
     * matching the active material groups.
     *
     * @param recipes              tagged recipe list
     * @param activeMaterialGroups selected category IDs; empty = no filtering
     * @return new list containing only matching recipes
     */
    List<TaggedRecipe> filterByMaterialGroups(List<TaggedRecipe> recipes,
                                              Set<String> activeMaterialGroups) {
        if (activeMaterialGroups == null || activeMaterialGroups.isEmpty()) {
            return new ArrayList<>(recipes);
        }
        List<TaggedRecipe> result = new ArrayList<>();
        for (TaggedRecipe recipe : recipes) {
            if (recipe.categoryIds() != null) {
                for (String catId : recipe.categoryIds()) {
                    if (activeMaterialGroups.contains(catId)) {
                        result.add(recipe);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Converts a raw set name to a display-friendly label by replacing
     * underscores with spaces.
     */
    static String setDisplayLabel(String setName) {
        if (setName == null) return "";
        return setName.replace('_', ' ');
    }
}
