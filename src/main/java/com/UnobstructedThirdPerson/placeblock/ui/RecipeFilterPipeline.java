package com.UnobstructedThirdPerson.placeblock.ui;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Sequential, composable filter pipeline for Blueprint Bench recipes.
 *
 * <p>Replaces the interleaved filtering logic previously split across
 * {@code BlueprintSelectionPage.applyFilter()} and {@code buildRecipeList()}.
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
     * @param recipeId     recipe asset ID (e.g. "Wood_Hardwood_Planks")
     * @param outputItemId output item asset ID
     * @param blockTypeId  output block type ID
     * @param benchId      primary bench ID for tab grouping
     * @param set          {@code Item.set} value; may be {@code null}
     */
    public record InputRecipe(
            String recipeId,
            String outputItemId,
            String blockTypeId,
            String benchId,
            @Nullable String set,
            List<String> categoryIds
    ) {}

    /**
     * A recipe exiting the pipeline, enriched with affordability and a
     * guaranteed non-null {@code effectiveSet}.
     *
     * @param recipeId      recipe asset ID
     * @param outputItemId  output item asset ID
     * @param blockTypeId   output block type ID
     * @param benchId       primary bench ID
     * @param effectiveSet  never null; equals original set or {@link #UNCATEGORIZED_SET}
     * @param affordable    {@code true} if the player can craft this recipe
     *                      (raw materials OR BlockGroup interchangeability)
     */
    public record TaggedRecipe(
            String recipeId,
            String outputItemId,
            String blockTypeId,
            String benchId,
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
     * @param showUncategorized      when false, recipes with effectiveSet equal to
     *                               {@link #UNCATEGORIZED_SET} are excluded from results;
     *                               when true, they are included
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
            boolean showUncategorized,
            Map<String, CategoryInfo> categoryInfoMap
    ) {
        List<InputRecipe> tabFiltered = filterByTab(allRecipes, activeTab);
        List<InputRecipe> searchFiltered = filterBySearch(tabFiltered, searchQuery);
        List<TaggedRecipe> tagged = tagAffordability(searchFiltered, checker);

        // Derive material groups from category metadata.
        // When affordability is on, only show categories that contain ≥1 affordable recipe.
        List<TaggedRecipe> categorySource = affordableOnly ? filterByAffordability(tagged) : tagged;
        List<MaterialGroup> currentGroups = extractMaterialGroups(categorySource, categoryInfoMap, 25);

        // Filter by active material groups (category-based)
        List<TaggedRecipe> groupFiltered = filterByMaterialGroups(tagged, activeMaterialGroups);

        // Sets are derived from category-filtered recipes
        List<String> visibleSets = affordableOnly
                ? extractSets(filterByAffordability(groupFiltered))
                : extractSets(groupFiltered);

        // Restrict to selected sets or all visible sets
        Set<String> effectiveSetFilter = (activeSetFilters != null && !activeSetFilters.isEmpty())
                ? activeSetFilters
                : new TreeSet<>(visibleSets);
        List<TaggedRecipe> setFiltered = filterBySets(groupFiltered, effectiveSetFilter);
        if (!showUncategorized) {
            setFiltered.removeIf(r -> UNCATEGORIZED_SET.equals(r.effectiveSet()));
        }
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
            if (activeTab.equals(recipe.benchId())) {
                result.add(recipe);
            }
        }
        return result;
    }

    /**
     * Stage 2: Retain recipes matching the search query.
     *
     * <p>Matches against {@code recipeId}, {@code blockTypeId}, and {@code set}
     * (all case-insensitive substring match). If query is null or empty,
     * all recipes pass through.
     *
     * @param recipes input recipe list
     * @param query   search text (may be null or empty)
     * @return new list containing only matching recipes
     */
    List<InputRecipe> filterBySearch(List<InputRecipe> recipes, @Nullable String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(recipes);
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<InputRecipe> result = new ArrayList<>();
        for (InputRecipe recipe : recipes) {
            if (recipe.recipeId().toLowerCase(Locale.ROOT).contains(lowerQuery)
                    || recipe.blockTypeId().toLowerCase(Locale.ROOT).contains(lowerQuery)
                    || (recipe.set() != null && recipe.set().toLowerCase(Locale.ROOT).contains(lowerQuery))) {
                result.add(recipe);
            }
        }
        return result;
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
                    recipe.benchId(),
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
     * Converts a raw set name to a display-friendly label by stripping
     * any prefix before the first underscore and replacing remaining
     * underscores with spaces.
     */
    static String setDisplayLabel(String setName) {
        if (setName == null) return "";
        String label = setName;
        if (label.contains("_")) {
            label = label.substring(label.indexOf('_') + 1).replace('_', ' ');
        }
        return label;
    }
}
