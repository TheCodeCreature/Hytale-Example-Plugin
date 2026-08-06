package com.CodeCreature.scaling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.CodeCreature.crafting.GenericIngredientResolution;
import com.CodeCreature.crafting.GenericIngredientResolver;
import com.CodeCreature.crafting.IndexedGenericIngredientResolver;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

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

    private static final GenericIngredientResolver GENERIC_INGREDIENT_RESOLVER = new IndexedGenericIngredientResolver();

    /**
     * Comparator that sorts set-root items (where {@code id == set}) before
     * derivative items (where {@code set} points to a different item).
     * {@code false} (root) sorts before {@code true} (derivative).
     */
    private static final Comparator<Map.Entry<String, Item>> SET_ROOT_FIRST =
            Comparator.comparing(e -> !isSetRoot(e.getKey(), e.getValue()));

    /** Pre-indexed item entry for O(1) resource type lookup. */
    private record IndexedItem(String itemId, boolean isNatural, boolean isSetRoot) {}

    /** Index: resourceTypeId → sorted list of matching items (set-roots first). */
    private static Map<String, List<IndexedItem>> resourceTypeIndex = Map.of();

    private ResourceTypeResolver() {}

    /**
     * Builds the resource type index from the item asset map.
     * Must be called after assets are loaded (after NaturalResourceRegistry.init()).
     */
    public static void initialize() {
        Map<String, List<IndexedItem>> index = new HashMap<>();
        for (var entry : Item.getAssetMap().getAssetMap().entrySet()) {
            Item item = entry.getValue();
            if (item == null || item.getResourceTypes() == null || isDeco(item)) continue;
            String itemId = entry.getKey();
            boolean isNatural = NaturalResourceRegistry.isNaturalItem(itemId);
            boolean setRoot = isSetRoot(itemId, item);
            IndexedItem indexed = new IndexedItem(itemId, isNatural, setRoot);
            for (ItemResourceType rt : item.getResourceTypes()) {
                if (rt.id != null) {
                    index.computeIfAbsent(rt.id, k -> new ArrayList<>()).add(indexed);
                }
            }
        }
        // Sort each list: set roots first (false < true → roots before derivatives)
        for (List<IndexedItem> list : index.values()) {
            list.sort(Comparator.comparing(i -> !i.isSetRoot));
        }
        resourceTypeIndex = Collections.unmodifiableMap(index);
    }

    /**
     * Resolves a {@link MaterialQuantity} to a concrete item ID using the
     * given preference to determine natural/non-natural item selection.
     *
     * <p>If the input has a direct {@code ItemId}, validates it exists in
     * the asset map and returns it. If the input uses a {@code ResourceTypeId},
     * delegates to {@link #resolveByResourceType(String, boolean)}.
     *
     * @param input          the recipe input to resolve
     * @param preferNatural  {@code true} to prefer natural items, {@code false} for non-natural
     * @return the concrete item ID, or null if unresolvable
     */
    @Nullable
    public static String resolveInputItemId(@Nonnull MaterialQuantity input,
                                             boolean preferNatural) {
        String itemId = input.getItemId();
        if (itemId != null && !"Empty".equals(itemId)) {
            Item item = Item.getAssetMap().getAsset(itemId);
            return item != null ? itemId : null;
        }
        String resId = input.getResourceTypeId();
        if (resId != null) {
            return resolveByResourceType(resId, preferNatural);
        }
        return null;
    }

    /**
     * Resolves a {@code ResourceTypeId} to a concrete item ID by scanning
     * {@link Item#getResourceTypes()} on every item in the asset map.
     *
     * <p>Two-pass resolution with set-root tie-breaking:
     * <ul>
     *   <li>Pass 1: items matching the preference
     *       ({@code preferNatural} controls whether natural or non-natural
     *       items are checked first), sorted so set-root items come before
     *       derivatives</li>
     *   <li>Pass 2: all items (fallback), also sorted set-roots first</li>
     * </ul>
     *
     * @param resId         the resource type ID (e.g. {@code "Wood_All"},
     *                      {@code "Wood_Hardwood"})
     * @param preferNatural {@code true} to prefer natural items, {@code false} for non-natural
     * @return the first matching item ID, or null if no item declares
     *         this resource type
     */
    @Nullable
    static String resolveByResourceType(@Nonnull String resId,
                                         boolean preferNatural) {
        List<IndexedItem> items = resourceTypeIndex.getOrDefault(resId, List.of());
        if (items.isEmpty()) return null;

        // Pass 1: preferred items (already sorted set-roots first)
        for (IndexedItem item : items) {
            if (item.isNatural == preferNatural) return item.itemId;
        }
        // Pass 2: any item (first in list is a set-root due to sorting)
        return items.get(0).itemId;
    }

    /**
     * Returns all concrete item IDs that match the given {@code ResourceTypeId},
     * in the same order as the pre-built index (set-roots first, then derivatives).
     *
     * <p>This method is the multi-variant counterpart to
     * {@link #resolveByResourceType(String, boolean)}, which returns only the
     * first (preferred) match. Use this method when all matching variants
     * should be considered (e.g., for inventory counting across all variants).
     *
     * <p>Each returned item ID appears exactly once. The list is unmodifiable.
     *
     * @param resourceTypeId the resource type ID to look up (e.g. {@code "Rock_Shale_Brick"})
     * @return unmodifiable list of concrete item IDs; empty if no items match
     */
    @Nonnull
    public static List<String> getAllMatchingItemIds(@Nonnull String resourceTypeId) {
        List<IndexedItem> items = resourceTypeIndex.getOrDefault(resourceTypeId, List.of());
        if (items.isEmpty()) return List.of();
        return items.stream().map(IndexedItem::itemId).toList();
    }

    @Nonnull
    public static GenericIngredientResolution resolveGenericIngredient(@Nonnull MaterialQuantity input,
                                                                       boolean preferNatural) {
        return GENERIC_INGREDIENT_RESOLVER.resolve(input, preferNatural);
    }

    @Nonnull
    public static List<GenericIngredientResolution> resolveGenericIngredients(@Nonnull List<MaterialQuantity> inputs,
                                                                              boolean preferNatural) {
        return GENERIC_INGREDIENT_RESOLVER.resolveAll(inputs, preferNatural);
    }

    private static Stream<Map.Entry<String, Item>> itemsWithResourceType(@Nonnull String resId) {
        return Item.getAssetMap().getAssetMap().entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getResourceTypes() != null)
                .filter(e -> !isDeco(e.getValue()))
                .filter(e -> Arrays.stream(e.getValue().getResourceTypes())
                        .anyMatch(rt -> resId.equals(rt.id)));
    }

    static boolean isDeco(@Nonnull Item item) {
        String[] cats = item.getCategories();
        if (cats == null) return false;
        for (String cat : cats) {
            if ("Blocks.Deco".equals(cat)) return true;
        }
        return false;
    }

    private static boolean isSetRoot(@Nonnull String itemId, @Nonnull Item item) {
        String set = getSetId(item);
        return set == null || set.isEmpty() || set.equals(itemId);
    }

    @Nullable
    private static String getSetId(@Nonnull Item item) {
        try {
            return (String) AssetFieldAccessor.INSTANCE.itemSet.get(item);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    // ─── Resource type filter matching ──────────────────────────

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
