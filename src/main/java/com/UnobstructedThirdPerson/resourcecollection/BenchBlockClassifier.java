package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Classifies every block type into a {@link BenchCategory} based on which
 * bench(es) its crafting recipe requires. Also tracks which blocks are
 * base-block types (all recipe inputs are exclusively natural).
 *
 * <p>Built once after {@link BenchRecipeRegistries#init(String...)} completes.
 * The classification is used by {@link DropScaler} to partition blocks into
 * independent sets that can be processed in parallel by category-specific
 * {@link BenchCategoryProcessor} implementations.
 *
 * <p>This class is immutable after {@link #classify()} — safe to read
 * from multiple threads.
 */
public final class BenchBlockClassifier {

    /** blockTypeId → BenchCategory (only blocks with recipes are present) */
    private Map<String, BenchCategory> blockCategories = Collections.emptyMap();

    /** blockTypeIds classified as base blocks (all inputs exclusively natural) */
    private Set<String> baseBlockTypes = Collections.emptySet();

    /**
     * Scans all block types, determines which bench(es) each recipe block
     * belongs to, and classifies base-block types.
     *
     * <p>For each block type with a recipe in any registered bench:
     * <ol>
     *   <li>Look up the recipe via {@link BenchRecipeRegistries}</li>
     *   <li>Determine the {@link BenchCategory} from the recipe's
     *       {@link com.hypixel.hytale.protocol.BenchRequirement}s</li>
     *   <li>Check whether the recipe qualifies as a base-block recipe
     *       (all inputs resolve to exclusively natural items via
     *       {@link ResourceTypeResolver#isResourceTypeExclusivelyNatural})</li>
     * </ol>
     *
     * <p>Must be called after {@link NaturalResourceRegistry#init()} and
     * {@link BenchRecipeRegistries#init(String...)}.
     */
    public void classify() {
        Map<String, BenchCategory> categories = new HashMap<>();
        Set<String> baseBlocks = new HashSet<>();

        for (var entry : BlockType.getAssetMap().getAssetMap().entrySet()) {
            BlockType bt = entry.getValue();
            if (bt == null) continue;
            String btId = bt.getId();
            if ("Empty".equals(btId) || "Unknown".equals(btId)) continue;

            CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(btId);
            if (recipe == null) continue;

            BenchCategory category = BenchCategory.fromRecipe(recipe);
            if (category == null) continue;

            categories.put(btId, category);

            if (allInputsExclusivelyNatural(recipe)) {
                baseBlocks.add(btId);
            }
        }

        this.blockCategories = Collections.unmodifiableMap(categories);
        this.baseBlockTypes = Collections.unmodifiableSet(baseBlocks);
    }

    /**
     * Returns the bench category for a block type, or {@code null} if the
     * block has no recipe in any registered bench.
     */
    @Nullable
    public BenchCategory getCategory(@Nonnull String blockTypeId) {
        return blockCategories.get(blockTypeId);
    }

    /**
     * Whether this block type is classified as a base block
     * (all recipe inputs are exclusively natural resources).
     */
    public boolean isBaseBlock(@Nonnull String blockTypeId) {
        return baseBlockTypes.contains(blockTypeId);
    }

    /**
     * Returns all block type IDs belonging to the given category.
     *
     * @param category the bench category to filter by
     * @return immutable set of block type IDs (may be empty)
     */
    @Nonnull
    public Set<String> getBlocksByCategory(@Nonnull BenchCategory category) {
        return blockCategories.entrySet().stream()
                .filter(e -> e.getValue() == category)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns all block type IDs that have a recipe but are NOT base blocks.
     * These are the blocks that need recipe-drop processing.
     *
     * @param category the bench category to filter by
     * @return immutable set of non-base block type IDs for the category
     */
    @Nonnull
    public Set<String> getNonBaseBlocksByCategory(@Nonnull BenchCategory category) {
        return blockCategories.entrySet().stream()
                .filter(e -> e.getValue() == category)
                .map(Map.Entry::getKey)
                .filter(btId -> !baseBlockTypes.contains(btId))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Checks whether all inputs of a recipe resolve to exclusively natural
     * items. For direct {@code ItemId} inputs, checks membership in
     * {@link NaturalResourceRegistry}. For {@code ResourceTypeId} inputs,
     * delegates to {@link ResourceTypeResolver#isResourceTypeExclusivelyNatural}.
     *
     * @param recipe the recipe to check
     * @return true if every input resolves to exclusively natural items
     */
    private static boolean allInputsExclusivelyNatural(@Nonnull CraftingRecipe recipe) {
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) return false;
        Set<String> naturalItems = NaturalResourceRegistry.getNaturalItemIds();
        for (MaterialQuantity mq : inputs) {
            if (mq == null) continue;
            String itemId = mq.getItemId();
            if (itemId != null && !"Empty".equals(itemId)) {
                if (!naturalItems.contains(itemId)) return false;
            } else {
                String resId = mq.getResourceTypeId();
                if (resId == null) return false;
                if (!ResourceTypeResolver.isResourceTypeExclusivelyNatural(resId, naturalItems))
                    return false;
            }
        }
        return true;
    }
}
