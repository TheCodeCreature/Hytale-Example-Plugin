package com.CodeCreature.scaling;

import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Resolves {@code ResourceTypeId}-based recipe inputs to concrete item IDs,
 * applying a bench-category-specific preference for natural vs. non-natural
 * items.
 *
 * <p>Uses the engine's own matching logic: exact string equality between the
 * {@code ResourceTypeId} and each item's {@link Item#getResourceTypes()}
 * entries. The two-pass approach ensures deterministic results:
 * <ol>
 *   <li><strong>Pass 1 (preferred)</strong>: scan only items matching the
 *       category's preference (natural for Furniture, non-natural for Builders),
 *       sorted so set-root items ({@code id == set}) come before derivatives</li>
 *   <li><strong>Pass 2 (fallback)</strong>: scan all items if pass 1 finds
 *       no match, again sorted set-roots first</li>
 * </ol>
 *
 * <p>Within each pass, items are sorted by their {@code Set} field: an item
 * whose ID equals its own Set value is a "set root" (e.g. Hardwood Planks)
 * and is preferred over derivatives whose Set points elsewhere (e.g.
 * Hardwood Decorative, Hardwood Ornate).
 *
 * <p>This class is <strong>thread-safe</strong> — it holds no mutable state.
 * Each call scans the (read-only, post-init) item asset map.
 */
public final class ResourceTypeResolver {

    private static final Field SET_FIELD;
    static {
        try {
            SET_FIELD = Item.class.getDeclaredField("set");
            SET_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Comparator that sorts set-root items (where {@code id == set}) before
     * derivative items (where {@code set} points to a different item).
     * {@code false} (root) sorts before {@code true} (derivative).
     */
    private static final Comparator<Map.Entry<String, Item>> SET_ROOT_FIRST =
            Comparator.comparing(e -> !isSetRoot(e.getKey(), e.getValue()));

    private ResourceTypeResolver() {}

    /**
     * Resolves a {@link MaterialQuantity} to a concrete item ID using the
     * given {@link BenchCategory} to determine natural/non-natural preference.
     *
     * <p>If the input has a direct {@code ItemId}, validates it exists in
     * the asset map and returns it. If the input uses a {@code ResourceTypeId},
     * delegates to {@link #resolveByResourceType(String, BenchCategory)}.
     *
     * @param input    the recipe input to resolve
     * @param category the bench category controlling resolution preference
     * @return the concrete item ID, or null if unresolvable
     */
    @Nullable
    public static String resolveInputItemId(@Nonnull MaterialQuantity input,
                                             @Nonnull BenchCategory category) {
        String itemId = input.getItemId();
        if (itemId != null && !"Empty".equals(itemId)) {
            Item item = Item.getAssetMap().getAsset(itemId);
            return item != null ? itemId : null;
        }
        String resId = input.getResourceTypeId();
        if (resId != null) {
            return resolveByResourceType(resId, category);
        }
        return null;
    }

    /**
     * Resolves a {@code ResourceTypeId} to a concrete item ID by scanning
     * {@link Item#getResourceTypes()} on every item in the asset map.
     *
     * <p>Two-pass resolution with set-root tie-breaking:
     * <ul>
     *   <li>Pass 1: items matching the category's preference
     *       ({@link BenchCategory#preferNatural()} controls whether
     *       natural or non-natural items are checked first),
     *       sorted so set-root items come before derivatives</li>
     *   <li>Pass 2: all items (fallback), also sorted set-roots first</li>
     * </ul>
     *
     * @param resId    the resource type ID (e.g. {@code "Wood_All"},
     *                 {@code "Wood_Hardwood"})
     * @param category the bench category controlling preference
     * @return the first matching item ID, or null if no item declares
     *         this resource type
     */
    @Nullable
    static String resolveByResourceType(@Nonnull String resId,
                                         @Nonnull BenchCategory category) {
        boolean preferNatural = category.preferNatural();

        // Pass 1: preferred items (natural for Furniture, non-natural for Builders),
        // sorted so set-root items (e.g. Planks) come before derivatives (Decorative/Ornate)
        var preferred = itemsWithResourceType(resId)
                .filter(e -> NaturalResourceRegistry.isNaturalItem(e.getKey()) == preferNatural)
                .sorted(SET_ROOT_FIRST)
                .map(Map.Entry::getKey)
                .findFirst();
        if (preferred.isPresent()) {
            return preferred.get();
        }

        // Pass 2: fallback to any item with a matching ResourceType, set-roots first
        var fallback = itemsWithResourceType(resId)
                .sorted(SET_ROOT_FIRST)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        return fallback;
    }

    /**
     * Returns a stream of asset-map entries whose item declares the given
     * ResourceTypeId. Filters out null items, null ResourceType arrays,
     * and items in the {@code Blocks.Deco} category (decorative props that
     * cannot be recovered by adventure-mode players).
     */
    private static Stream<Map.Entry<String, Item>> itemsWithResourceType(@Nonnull String resId) {
        return Item.getAssetMap().getAssetMap().entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getResourceTypes() != null)
                .filter(e -> !isDeco(e.getValue()))
                .filter(e -> Arrays.stream(e.getValue().getResourceTypes())
                        .anyMatch(rt -> resId.equals(rt.id)));
    }

    /**
     * Returns {@code true} if the item belongs to the {@code Blocks.Deco}
     * category — decorative props not intended for crafting resolution.
     *
     * <p>Package-private so {@link NaturalResourceRegistry} can reuse it
     * for the two-tier natural item set classification.
     */
    static boolean isDeco(@Nonnull Item item) {
        String[] cats = item.getCategories();
        if (cats == null) return false;
        for (String cat : cats) {
            if ("Blocks.Deco".equals(cat)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if this item is a "set root" — its ID matches
     * its own {@code Set} value (or the set is null/empty, meaning no
     * derivative relationship). Set roots represent the base item in a
     * family (e.g. "Hardwood Planks"), while derivatives point their set
     * to the root (e.g. "Hardwood Decorative" → set = "Wood_Hardwood_Planks").
     */
    private static boolean isSetRoot(@Nonnull String itemId, @Nonnull Item item) {
        String set = getSetId(item);
        return set == null || set.isEmpty() || set.equals(itemId);
    }

    /**
     * Reads the protected {@code set} field from an {@link Item} via
     * cached reflection.
     */
    @Nullable
    private static String getSetId(@Nonnull Item item) {
        try {
            return (String) SET_FIELD.get(item);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    // ─── Resource type filter matching ──────────────────────────

    /**
     * Checks whether any input of the given recipe matches any of the
     * specified resource type IDs.
     *
     * <p>Handles both input types:
     * <ul>
     *   <li><strong>{@code ResourceTypeId}-based inputs</strong>: checks if
     *       {@code input.getResourceTypeId()} is contained in
     *       {@code resourceTypeIds}</li>
     *   <li><strong>{@code ItemId}-based inputs</strong>: resolves the item
     *       via the asset map, then checks if any entry in
     *       {@link Item#getResourceTypes()} has an ID present in
     *       {@code resourceTypeIds}</li>
     * </ul>
     *
     * <p>Short-circuits on first match. Does NOT apply
     * {@link BenchCategory} preference — this is a filter predicate,
     * not a resolution operation.
     *
     * <p>This method is <strong>thread-safe</strong> — it reads only
     * immutable post-init data.
     *
     * @param recipe          the crafting recipe to check
     * @param resourceTypeIds the set of engine ResourceTypeId values to
     *                        match against (already resolved from meta-filters)
     * @return {@code true} if at least one recipe input matches any of
     *         the given resource type IDs; {@code false} if recipe has
     *         no inputs or none match
     */
    public static boolean recipeMatchesAnyResourceType(
            @Nonnull CraftingRecipe recipe,
            @Nonnull Set<String> resourceTypeIds) {
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) {
            return false;
        }
        for (MaterialQuantity input : inputs) {
            if (inputMatchesAnyResourceType(input, resourceTypeIds)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a single recipe input matches any of the specified
     * resource type IDs.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code input.getResourceTypeId()} is non-null, check direct
     *       membership in {@code resourceTypeIds}</li>
     *   <li>If {@code input.getItemId()} is non-null and not {@code "Empty"},
     *       look up the item in {@link Item#getAssetMap()}, then check if any
     *       of the item's {@link Item#getResourceTypes()} entries has an ID
     *       present in {@code resourceTypeIds}</li>
     *   <li>Otherwise return {@code false}</li>
     * </ol>
     *
     * @param input           a single recipe input (MaterialQuantity)
     * @param resourceTypeIds the set of resource type IDs to match against
     * @return {@code true} if this input matches any of the given IDs
     */
    private static boolean inputMatchesAnyResourceType(
            @Nonnull MaterialQuantity input,
            @Nonnull Set<String> resourceTypeIds) {
        // Path 1: ResourceTypeId-based input
        String resTypeId = input.getResourceTypeId();
        if (resTypeId != null && resourceTypeIds.contains(resTypeId)) {
            return true;
        }

        // Path 2: ItemId-based input — resolve to Item, check its ResourceTypes
        String itemId = input.getItemId();
        if (itemId != null && !"Empty".equals(itemId)) {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item != null && item.getResourceTypes() != null) {
                for (var rt : item.getResourceTypes()) {
                    if (rt != null && resourceTypeIds.contains(rt.id)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

}
