package com.CodeCreature.scaling;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Classifies every block type into a {@link BenchCategory} based on which
 * bench(es) its crafting recipe requires.
 *
 * <p>Built once after {@link BenchRecipeRegistries#init()} completes.
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

    /**
     * Scans all block types and determines which bench(es) each recipe block
     * belongs to.
     *
     * <p>For each block type with a recipe in any registered bench:
     * <ol>
     *   <li>Look up the recipe via {@link BenchRecipeRegistries}</li>
     *   <li>Determine the {@link BenchCategory} from the recipe's
     *       {@link com.hypixel.hytale.protocol.BenchRequirement}s</li>
     * </ol>
     *
     * <p>Must be called after {@link BenchRecipeRegistries#init()}.
     */
    public void classify() {
        Map<String, BenchCategory> categories = new HashMap<>();

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
        }

        this.blockCategories = Collections.unmodifiableMap(categories);
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

}
