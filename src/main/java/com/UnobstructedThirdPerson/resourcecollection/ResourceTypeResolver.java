package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.protocol.ItemResourceType;
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
 *
 * <p>Also provides {@link #isResourceTypeExclusivelyNatural(String, Set)} for
 * base-block classification, which is independent of bench preference and
 * checks whether ALL items matching a ResourceTypeId are natural.
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

        // TEMP DEBUG: log all candidates for Wood_Hardwood
        if ("Wood_Hardwood".equals(resId)) {
            System.out.println("[RTR-DEBUG] Resolving '" + resId + "' category=" + category
                    + " preferNatural=" + preferNatural);
            itemsWithResourceType(resId).forEach(e -> {
                Item it = e.getValue();
                System.out.println("[RTR-DEBUG]   candidate: " + e.getKey()
                        + " | set=" + getSetId(it)
                        + " | setRoot=" + isSetRoot(e.getKey(), it)
                        + " | blockId=" + it.getBlockId()
                        + " | natural=" + NaturalResourceRegistry.isNaturalItem(e.getKey()));
            });
        }

        // Pass 1: preferred items (natural for Furniture, non-natural for Builders),
        // sorted so set-root items (e.g. Planks) come before derivatives (Decorative/Ornate)
        var preferred = itemsWithResourceType(resId)
                .filter(e -> NaturalResourceRegistry.isNaturalItem(e.getKey()) == preferNatural)
                .sorted(SET_ROOT_FIRST)
                .map(Map.Entry::getKey)
                .findFirst();
        if (preferred.isPresent()) {
            if ("Wood_Hardwood".equals(resId))
                System.out.println("[RTR-DEBUG]   Pass 1 selected: " + preferred.get());
            return preferred.get();
        }

        // Pass 2: fallback to any item with a matching ResourceType, set-roots first
        var fallback = itemsWithResourceType(resId)
                .sorted(SET_ROOT_FIRST)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if ("Wood_Hardwood".equals(resId))
            System.out.println("[RTR-DEBUG]   Pass 2 selected: " + fallback);
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

    /**
     * Returns {@code true} only if <strong>every</strong> item in the asset
     * map that declares the given ResourceTypeId is a natural item.
     * Returns {@code false} if any non-natural item matches, or if no item
     * matches at all.
     *
     * <p>Used exclusively for base-block classification in
     * {@link BenchRecipeRegistry#init()}. This is intentionally independent
     * of bench preference — classification must be deterministic regardless
     * of which bench a recipe belongs to.
     *
     * @param resId        the resource type ID to check
     * @param naturalItems the set of known natural item IDs
     * @return {@code true} if all matching items are natural
     */
    public static boolean isResourceTypeExclusivelyNatural(
            @Nonnull String resId, @Nonnull Set<String> naturalItems) {
        var matchingItemIds = itemsWithResourceType(resId)
                .map(Map.Entry::getKey)
                .toList();
        return !matchingItemIds.isEmpty()
                && matchingItemIds.stream().allMatch(naturalItems::contains);
    }
}
