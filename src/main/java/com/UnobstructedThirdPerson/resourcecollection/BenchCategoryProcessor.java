package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Processes a set of recipe blocks belonging to a single {@link BenchCategory}.
 *
 * <p>Each implementation handles the specific drop-resolution logic for its
 * bench type. Implementations are stateless and thread-safe — all mutable
 * results are returned in a {@link ProcessResult}.
 *
 * <p>Designed for parallel execution: each category's blocks are an
 * independent partition, so multiple processors can run concurrently on
 * virtual threads without overlapping on the same block types.
 *
 * <p>New bench categories can be supported by adding new implementations
 * without modifying existing code.
 */
public interface BenchCategoryProcessor {

    /**
     * The bench category this processor handles.
     *
     * @return the category this processor is responsible for
     */
    @Nonnull
    BenchCategory category();

    /**
     * Processes all non-base recipe blocks in the given set, resolving
     * their breaking drops to recipe ingredient items.
     *
     * <p>For each block:
     * <ol>
     *   <li>Look up the recipe from the appropriate {@link BenchRecipeRegistry}</li>
     *   <li>Resolve each input using {@link ResourceTypeResolver#resolveInputItemId}
     *       with this processor's {@link #category()}</li>
     *   <li>Calculate proportional drop quantities (inputQty / outputQty)</li>
     *   <li>Set the block's breaking config to drop the resolved items</li>
     * </ol>
     *
     * @param blockTypeIds      the block type IDs to process (non-base blocks
     *                          for this category)
     * @param f                 shared (immutable) field accessor for reflection
     * @param ingredientItemIds merged set of all ingredient item IDs (for
     *                          cross-reference; may not be needed by all impls)
     * @return the result containing modification counts and synthetic drop lists
     */
    @Nonnull
    ProcessResult process(@Nonnull Set<String> blockTypeIds,
                          @Nonnull AssetFieldAccessor f,
                          @Nonnull Set<String> ingredientItemIds);

    /**
     * Immutable result of processing a category's blocks.
     *
     * @param modified           number of blocks successfully modified
     * @param skipped            number of blocks skipped (no recipe, no gathering, etc.)
     * @param syntheticDropLists synthetic drop lists created for multi-ingredient recipes
     */
    record ProcessResult(int modified, int skipped,
                         @Nonnull List<ItemDropList> syntheticDropLists) {

        /** Empty result with zero counts and no synthetic drop lists. */
        public static final ProcessResult EMPTY = new ProcessResult(0, 0, List.of());
    }
}
