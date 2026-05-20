package com.CodeCreature.crafting;

import javax.annotation.Nonnull;

/**
 * Immutable record representing a single raw material and its required quantity
 * for producing one unit of a crafted item.
 *
 * <p>Produced by {@link RecipeTreeResolver} during recursive recipe resolution.
 * Multiple {@code RawMaterialRequirement} records may share the same
 * {@code itemId} across different resolution paths — the resolver merges
 * them before returning.
 *
 * @param itemId   the concrete item ID of the raw material (post-resolution
 *                 through {@code ResourceTypeResolver} and
 *                 {@code NaturalResourceRegistry.resolveToGatherableForm()})
 * @param quantity the quantity of this raw material needed to produce one
 *                 unit of the target crafted item
 */
public record RawMaterialRequirement(
        @Nonnull String itemId,
        int quantity
) {}
