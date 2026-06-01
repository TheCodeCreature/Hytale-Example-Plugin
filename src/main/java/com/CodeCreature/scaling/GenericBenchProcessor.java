package com.CodeCreature.scaling;

import javax.annotation.Nonnull;

/**
 * Generic bench processor that handles blocks belonging to any bench-set.
 * Parameterized by a {@code preferNatural} flag that controls how
 * {@link ResourceTypeResolver} picks a concrete item for a
 * {@code ResourceTypeId} input. This class makes the preference
 * configurable at construction time.
 *
 * <p>All processing logic lives in {@link AbstractBenchProcessor} — this
 * class only provides the {@code preferNatural} value.
 *
 * <p><strong>Usage in DropScaler:</strong>
 * <pre>{@code
 * // For each distinct bench-set discovered at runtime:
 * boolean preferNatural = BenchRegistry.isPreferNatural(benchSet);
 * var processor = new GenericBenchProcessor(preferNatural);
 * processor.process(blockTypeIds, f);
 * }</pre>
 *
 * <p>Thread-safe: instances are stateless beyond the immutable
 * {@code preferNatural} flag.
 */
public final class GenericBenchProcessor extends AbstractBenchProcessor {

    private final boolean preferNatural;

    /**
     * Creates a processor with the given natural-item preference.
     *
     * @param preferNatural {@code true} to prefer natural items (trunks, logs)
     *                      when resolving ResourceTypeId inputs;
     *                      {@code false} to prefer non-natural items
     *                      (planks, decorative blocks)
     */
    public GenericBenchProcessor(boolean preferNatural) {
        this.preferNatural = preferNatural;
    }

    /**
     * Returns whether this processor prefers natural items for
     * ResourceTypeId resolution.
     *
     * <p>Used by {@link AbstractBenchProcessor#process} when calling
     * {@link ResourceTypeResolver#resolveInputItemId}.
     *
     * @return {@code true} for natural preference, {@code false} for
     *         non-natural preference
     */
    public boolean preferNatural() {
        return preferNatural;
    }

}
