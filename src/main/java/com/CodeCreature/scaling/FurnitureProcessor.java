package com.CodeCreature.scaling;

import javax.annotation.Nonnull;

/**
 * Processes blocks that belong to the Furniture bench only.
 *
 * <p>Resolution preference: <strong>natural items</strong> (trunks,
 * logs — raw wood). When a Kweebec Bed recipe uses
 * {@code ResourceTypeId: "Wood_All"}, this resolves to
 * {@code Wood_Log_Oak} rather than a crafted planks block.
 */
public final class FurnitureProcessor extends AbstractBenchProcessor {

    @Override
    @Nonnull
    public BenchCategory category() {
        return BenchCategory.FURNITURE_ONLY;
    }
}
