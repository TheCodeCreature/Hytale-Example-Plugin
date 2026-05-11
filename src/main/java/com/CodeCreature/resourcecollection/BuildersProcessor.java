package com.CodeCreature.resourcecollection;

import javax.annotation.Nonnull;

/**
 * Processes blocks that belong to the Builders bench only.
 *
 * <p>Resolution preference: <strong>non-natural items</strong> (planks,
 * decorative, ornate — the FullBlocks set). When a fence recipe uses
 * {@code ResourceTypeId: "Wood_Hardwood"}, this resolves to
 * {@code Wood_Hardwood_Planks} rather than {@code Wood_Oak_Trunk}.
 */
public final class BuildersProcessor extends AbstractBenchProcessor {

    @Override
    @Nonnull
    public BenchCategory category() {
        return BenchCategory.BUILDERS_ONLY;
    }
}
