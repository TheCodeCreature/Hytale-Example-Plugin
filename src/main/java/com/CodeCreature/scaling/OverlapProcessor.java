package com.CodeCreature.scaling;

import javax.annotation.Nonnull;

/**
 * Processes blocks that belong to both Builders and Furniture benches.
 *
 * <p>Resolution preference: <strong>natural items</strong> (furniture
 * preference wins for overlapping recipes). If a recipe requires both
 * bench types, it is treated like a Furniture Bench recipe for drop
 * resolution purposes.
 */
public final class OverlapProcessor extends AbstractBenchProcessor {

    @Override
    @Nonnull
    public BenchCategory category() {
        return BenchCategory.BUILDERS_AND_FURNITURE;
    }
}
