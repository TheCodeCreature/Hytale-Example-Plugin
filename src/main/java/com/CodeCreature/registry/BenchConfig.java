package com.CodeCreature.registry;

import javax.annotation.Nonnull;

/**
 * Immutable configuration for a single crafting bench, identified by its
 * {@code BenchRequirement.id} from Hytale recipe assets.
 *
 * <p>Each discovered bench gets a {@code BenchConfig} registered in
 * {@link BenchRegistry}. The {@link #preferNatural()} flag controls
 * how {@link com.CodeCreature.scaling.ResourceTypeResolver} picks
 * concrete items when resolving {@code ResourceTypeId}-based recipe
 * inputs for blocks crafted at this bench.
 *
 * <p><strong>Default behavior:</strong>
 * <ul>
 *   <li>{@code Furniture_Bench} → {@code preferNatural = true}
 *       (hardcoded override for backward compatibility)</li>
 *   <li>All other benches → {@code preferNatural = false}
 *       (prefers crafted/FullBlocks items)</li>
 * </ul>
 *
 * @param benchId       the unique bench identifier, matching
 *                      {@code BenchRequirement.id} in recipe assets
 *                      (e.g. {@code "Builders"}, {@code "Furniture_Bench"})
 * @param preferNatural {@code true} if ResourceTypeResolver should prefer
 *                      natural items (trunks, logs) for this bench;
 *                      {@code false} for non-natural (planks, decorative)
 */
public record BenchConfig(
        @Nonnull String benchId,
        boolean preferNatural
) {
    /**
     * Compact constructor with null check.
     *
     * @throws NullPointerException if {@code benchId} is null
     */
    public BenchConfig {
        if (benchId == null) throw new NullPointerException("benchId must not be null");
    }
}
