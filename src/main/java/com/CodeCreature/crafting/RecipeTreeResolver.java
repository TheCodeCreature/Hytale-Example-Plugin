package com.CodeCreature.crafting;

import com.CodeCreature.scaling.BenchCategory;
import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.scaling.RecipeTierClassifier;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Recursively resolves crafted items to their raw material equivalents,
 * following crafting recipe chains until only gatherable natural resources
 * remain.
 *
 * <p>Results are pre-computed at {@link #init()} time and cached for the
 * lifetime of the server. Runtime lookups via {@link #resolveItemToRaw}
 * and {@link #resolveRecipeToRaw} are O(1) map lookups.
 *
 * <h3>Resolution rules</h3>
 * <ul>
 *   <li>Only non-Salvage recipes from registered crafting benches are
 *       followed (same filter as {@link RecipeTierClassifier})</li>
 *   <li>Processing recipes (smelting, stonecutting) are included — items
 *       produced via processing benches are eligible for auto-craft resolution</li>
 *   <li>{@code ResourceTypeId} inputs are resolved to concrete item IDs
 *       via {@link ResourceTypeResolver} at each level</li>
 *   <li>Cycles are detected and reported; cyclic items are treated as
 *       terminal (unresolvable)</li>
 * </ul>
 *
 * <h3>Initialization order</h3>
 * Must be called <b>after</b>:
 * <ol>
 *   <li>{@link NaturalResourceRegistry#init()}</li>
 *   <li>{@link RecipeTierClassifier#init()}</li>
 *   <li>{@code DropScaler.applyModifications()} — so that recipes have
 *       their scaled input quantities</li>
 * </ol>
 *
 * <h3>Threading</h3>
 * All fields are effectively immutable after {@link #init()}. Safe to
 * read from any thread.
 *
 * @see RawMaterialRequirement
 * @see AutoCraftPlanner
 * @see RecipeTierClassifier
 */
public final class RecipeTreeResolver {

    private static final Logger LOGGER = Logger.getLogger("RecipeTreeResolver");

    /**
     * Maps a crafted item's ID to the crafting recipe that produces it.
     * Only includes non-Salvage recipes from registered crafting benches.
     * If multiple recipes produce the same item, the first by sorted
     * recipe ID is stored.
     */
    private static Map<String, CraftingRecipe> recipeByOutputItem = Collections.emptyMap();

    /**
     * Maps a crafted item's ID to the raw materials needed to produce
     * ONE unit of that item, recursively resolved through all intermediate
     * crafting steps. {@code null} value means the item was encountered
     * during resolution but could not be resolved (cycle or missing recipe).
     */
    private static Map<String, List<RawMaterialRequirement>> rawCostCache = Collections.emptyMap();

    /**
     * Bench IDs whose recipes are eligible for auto-craft resolution.
     * Includes both crafting benches and processing benches.
     */
    private static final Set<String> ELIGIBLE_BENCH_IDS = Set.of(
            "Builders", "Furniture_Bench", "Workbench", "Fieldcraft",
            "Stonecutter", "Refinery", "Furnace", "Kiln");

    private RecipeTreeResolver() {}

    private static void log(String msg) {
        LOGGER.info("[RecipeTreeResolver] " + msg);
    }

    /**
     * Initializes the recipe-by-output index and pre-computes the raw
     * material cost cache for all known crafted items.
     *
     * <p>Must be called once after assets are loaded and recipes have been
     * scaled by {@code DropScaler.applyModifications()}.
     *
     * <p>Initialization steps:
     * <ol>
     *   <li>Scan all crafting recipes to build {@link #recipeByOutputItem}
     *       — excludes Salvage recipes and processing-bench recipes</li>
     *   <li>For each item classified as crafted by
     *       {@link RecipeTierClassifier#isCraftedItem}, compute and cache
     *       its raw material cost via recursive resolution</li>
     * </ol>
     */
    public static void init() {
        // Step 1: Build recipeByOutputItem
        Map<String, CraftingRecipe> byOutput = new LinkedHashMap<>();
        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;
            if (recipe.getId().startsWith("Salvage")) continue;
            if (!isEligibleBenchRecipe(recipe)) continue;

            MaterialQuantity output = recipe.getPrimaryOutput();
            if (output == null) continue;
            String itemId = output.getItemId();
            if (itemId == null || itemId.isEmpty()) continue;

            // If multiple recipes produce same item, we'll resolve cheapest later
            byOutput.putIfAbsent(itemId, recipe);
        }
        recipeByOutputItem = Collections.unmodifiableMap(byOutput);

        // Step 2: Pre-compute rawCostCache
        Map<String, List<RawMaterialRequirement>> cache = new LinkedHashMap<>();
        int resolved = 0, unresolvable = 0;
        for (String itemId : recipeByOutputItem.keySet()) {
            Map<String, Integer> rawCost = computeRawCost(itemId, new HashSet<>());
            if (rawCost != null && !rawCost.isEmpty()) {
                cache.put(itemId, rawCost.entrySet().stream()
                        .map(e -> new RawMaterialRequirement(e.getKey(), e.getValue()))
                        .toList());
                resolved++;
            } else {
                unresolvable++;
            }
        }
        rawCostCache = Collections.unmodifiableMap(cache);
        log("Initialized: " + resolved + " items resolved, " + unresolvable + " unresolvable");
    }

    /**
     * Returns the raw materials needed to produce ONE unit of the given
     * crafted item, recursively resolved through all intermediate
     * crafting steps.
     *
     * <p>Uses the pre-computed cache populated by {@link #init()}.
     *
     * <p>Example: {@code resolveItemToRaw("Brick")} might return
     * {@code [RawMaterialRequirement("Rock_Stone_Cobble", 12)]} if
     * 1 brick costs 12 cobblestone (after scaling).
     *
     * @param itemId the crafted item to resolve
     * @return list of raw material requirements for one unit,
     *         or {@code null} if the item is raw, has no recipe,
     *         or is unresolvable (cycle)
     */
    @Nullable
    public static List<RawMaterialRequirement> resolveItemToRaw(@Nonnull String itemId) {
        return rawCostCache.get(itemId);
    }

    /**
     * Returns the total raw material cost for one unit of the given
     * recipe's output, recursively resolving all crafted intermediate
     * inputs.
     *
     * <p>For each per-unit input of the recipe:
     * <ul>
     *   <li>If the input is a raw material (per
     *       {@link RecipeTierClassifier#isRawInput}), it appears
     *       directly in the result</li>
     *   <li>If the input is a crafted intermediate, its raw cost
     *       is looked up via {@link #resolveItemToRaw} and multiplied
     *       by the input quantity</li>
     * </ul>
     *
     * <p>Duplicate raw material entries are merged (quantities summed).
     *
     * <p>This method is used by the UI to display the total raw material
     * cost for a recipe (e.g., "Total: 36 cobblestone" for brick stairs).
     *
     * @param recipe the crafting recipe to resolve
     * @return list of raw materials needed for one placement; empty if
     *         the recipe has no inputs
     */
    @Nonnull
    public static List<RawMaterialRequirement> resolveRecipeToRaw(@Nonnull CraftingRecipe recipe) {
        List<MaterialQuantity> perUnit = PlaceBlockCostUtil.getPerUnitCost(recipe);
        if (perUnit.isEmpty()) return List.of();

        Map<String, Integer> merged = new LinkedHashMap<>();
        for (MaterialQuantity mq : perUnit) {
            if (mq == null) continue;
            String resolvedId = ResourceTypeResolver.resolveInputItemId(mq, BenchCategory.BUILDERS_ONLY);
            if (resolvedId == null || resolvedId.isEmpty()) continue;
            resolvedId = NaturalResourceRegistry.resolveToGatherableForm(resolvedId);
            int qty = mq.getQuantity();

            if (RecipeTierClassifier.isRawInput(mq)) {
                merged.merge(resolvedId, qty, Integer::sum);
            } else {
                List<RawMaterialRequirement> subCost = resolveItemToRaw(resolvedId);
                if (subCost == null) {
                    merged.merge(resolvedId, qty, Integer::sum);
                } else {
                    for (RawMaterialRequirement raw : subCost) {
                        merged.merge(raw.itemId(), raw.quantity() * qty, Integer::sum);
                    }
                }
            }
        }
        return merged.entrySet().stream()
                .map(e -> new RawMaterialRequirement(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Returns the crafting recipe that produces the given item, or
     * {@code null} if no eligible recipe exists.
     *
     * <p>Only returns recipes from registered crafting benches, excluding
     * Salvage and processing recipes. If multiple recipes produce the
     * same item, returns the one selected during {@link #init()} (first
     * by sorted recipe ID).
     *
     * @param itemId the output item ID to look up
     * @return the producing recipe, or {@code null}
     */
    @Nullable
    public static CraftingRecipe findRecipeFor(@Nonnull String itemId) {
        return recipeByOutputItem.get(itemId);
    }

    /**
     * Recursively computes the raw material cost for one unit of the
     * given crafted item.
     *
     * <p>This is the core resolution algorithm, called during
     * {@link #init()} to populate the cache. It is NOT called at
     * runtime.
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li>Check {@code visited} set for cycle detection — if the item
     *       is already being resolved, log a warning and return null</li>
     *   <li>Look up the recipe via {@link #recipeByOutputItem}</li>
     *   <li>Get per-unit costs via
     *       {@link PlaceBlockCostUtil#getPerUnitCost}</li>
     *   <li>For each per-unit input:
     *     <ul>
     *       <li>Resolve to concrete item ID via
     *           {@link ResourceTypeResolver#resolveInputItemId}</li>
     *       <li>Map through
     *           {@link NaturalResourceRegistry#resolveToGatherableForm}</li>
     *       <li>If raw: add to result map</li>
     *       <li>If crafted: recurse, multiply sub-costs by input quantity,
     *           add to result map</li>
     *     </ul>
     *   </li>
     *   <li>Remove item from {@code visited} set (backtrack)</li>
     * </ol>
     *
     * @param itemId  the crafted item to resolve
     * @param visited set of item IDs currently being resolved (for cycle
     *                detection) — must be mutable
     * @return map of raw material ID → quantity for one unit, or
     *         {@code null} if unresolvable
     */
    @Nullable
    private static Map<String, Integer> computeRawCost(@Nonnull String itemId,
                                                        @Nonnull Set<String> visited) {
        if (visited.contains(itemId)) {
            log("Cycle detected at " + itemId);
            return null;
        }

        CraftingRecipe recipe = recipeByOutputItem.get(itemId);
        if (recipe == null) return null;

        visited.add(itemId);
        List<MaterialQuantity> perUnitCosts = PlaceBlockCostUtil.getPerUnitCost(recipe);
        Map<String, Integer> rawMaterials = new LinkedHashMap<>();

        for (MaterialQuantity mq : perUnitCosts) {
            if (mq == null) continue;
            String resolvedId = ResourceTypeResolver.resolveInputItemId(mq, BenchCategory.BUILDERS_ONLY);
            if (resolvedId == null || resolvedId.isEmpty()) continue;
            resolvedId = NaturalResourceRegistry.resolveToGatherableForm(resolvedId);
            int qty = mq.getQuantity();

            if (RecipeTierClassifier.isRawInput(mq)) {
                rawMaterials.merge(resolvedId, qty, Integer::sum);
            } else {
                Map<String, Integer> subCost = computeRawCost(resolvedId, visited);
                if (subCost == null) {
                    rawMaterials.merge(resolvedId, qty, Integer::sum);
                } else {
                    for (var entry : subCost.entrySet()) {
                        rawMaterials.merge(entry.getKey(), entry.getValue() * qty, Integer::sum);
                    }
                }
            }
        }

        visited.remove(itemId);
        return rawMaterials;
    }

    /**
     * Checks whether the given recipe is from an eligible bench
     * (crafting or processing).
     *
     * <p>Uses the expanded bench ID set that includes both crafting
     * and processing benches.
     *
     * @param recipe the recipe to check
     * @return true if the recipe requires at least one bench in
     *         {@link #ELIGIBLE_BENCH_IDS}
     */
    private static boolean isEligibleBenchRecipe(@Nonnull CraftingRecipe recipe) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return false;
        for (BenchRequirement req : reqs) {
            if (req != null && req.id != null && ELIGIBLE_BENCH_IDS.contains(req.id)) {
                return true;
            }
        }
        return false;
    }
}
