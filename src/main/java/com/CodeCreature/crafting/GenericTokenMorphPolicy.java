package com.CodeCreature.crafting;


import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

public final class GenericTokenMorphPolicy {

    public record MorphSelection(@Nullable String concreteItemId, boolean remainsGeneric) {}

    private static final Comparator<GenericVariantMatcher.VariantAvailability> LARGEST_STACK_THEN_RESOLVER_ORDER =
            Comparator.comparingInt(GenericVariantMatcher.VariantAvailability::quantity).reversed()
                    .thenComparingInt(GenericVariantMatcher.VariantAvailability::resolverOrder);

    private GenericTokenMorphPolicy() {}

    @Nonnull
    public static MorphSelection selectFromContainer(@Nonnull GenericIngredientResolution resolution,
                                                     @Nullable String fallbackItemId,
                                                     @Nonnull CombinedItemContainer container,
                                                     boolean excludeStencils) {
        List<GenericVariantMatcher.VariantAvailability> availability =
                GenericVariantMatcher.collectVariantAvailability(resolution, fallbackItemId, container, excludeStencils);
        return selectFromAvailability(resolution, availability);
    }

    @Nonnull
    public static MorphSelection selectFromAvailability(@Nonnull GenericIngredientResolution resolution,
                                                        @Nonnull List<GenericVariantMatcher.VariantAvailability> availability) {
        GenericVariantMatcher.VariantAvailability best = availability.stream()
                .filter(variant -> variant.quantity() > 0)
                .sorted(LARGEST_STACK_THEN_RESOLVER_ORDER)
                .findFirst()
                .orElse(null);

        if (best != null) {
            return new MorphSelection(best.itemId(), false);
        }

        boolean genericInput = resolution.identity().resourceTypeId() != null
                && !resolution.identity().resourceTypeId().isEmpty();
        if (genericInput) {
            // Locked fallback contract: no matching concrete stack => remain generic.
            return new MorphSelection(null, true);
        }

        String concreteItemId = resolution.identity().itemId();
        if (concreteItemId != null && !concreteItemId.isEmpty()) {
            return new MorphSelection(concreteItemId, false);
        }

        return new MorphSelection(null, false);
    }

    @Nonnull
    public static List<GenericVariantMatcher.VariantAvailability> orderForConsumption(
            @Nonnull List<GenericVariantMatcher.VariantAvailability> availability) {
        return availability.stream()
                .sorted(LARGEST_STACK_THEN_RESOLVER_ORDER)
                .toList();
    }
}