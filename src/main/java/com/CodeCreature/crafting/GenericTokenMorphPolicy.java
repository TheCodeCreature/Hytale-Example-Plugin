package com.CodeCreature.crafting;

/**
 * @node    GenericTokenMorphPolicy
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Encapsulates the locked generic token morph policy: choose the largest currently
 *          matching concrete stack with deterministic tie-break, else remain generic.
 * @wave    3 (deficit alignment scaffolding)
 * @status  Wave 3 - implemented reusable token morph policy helper with generic fallback contract
 * @do-not  Force a representative concrete fallback when no matching stack exists.
 *          Interpret this policy as proof of engine removeMaterials internals.
 */

import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

public final class GenericTokenMorphPolicy {

    /** @intent Return the concrete morph target when chosen, or indicate that the token remains generic.
     *  @wave   3 - implemented policy result contract
     *  @status implemented
     *  @node   GenericTokenMorphPolicy.MorphSelection
     */
    public record MorphSelection(@Nullable String concreteItemId, boolean remainsGeneric) {}

    private static final Comparator<GenericVariantMatcher.VariantAvailability> LARGEST_STACK_THEN_RESOLVER_ORDER =
            Comparator.comparingInt(GenericVariantMatcher.VariantAvailability::quantity).reversed()
                    .thenComparingInt(GenericVariantMatcher.VariantAvailability::resolverOrder);

    private GenericTokenMorphPolicy() {}

    /** @intent Apply policy A from live container inventory using shared matcher semantics.
     *  @wave   3 - implemented live-container policy helper
     *  @status implemented
     *  @node   GenericTokenMorphPolicy#selectFromContainer
     */
    @Nonnull
    public static MorphSelection selectFromContainer(@Nonnull GenericIngredientResolution resolution,
                                                     @Nullable String fallbackItemId,
                                                     @Nonnull CombinedItemContainer container,
                                                     boolean excludeStencils) {
        List<GenericVariantMatcher.VariantAvailability> availability =
                GenericVariantMatcher.collectVariantAvailability(resolution, fallbackItemId, container, excludeStencils);
        return selectFromAvailability(resolution, availability);
    }

    /** @intent Apply policy A from a precomputed availability snapshot to avoid duplicate counting across planner stages.
     *  @wave   3 - implemented snapshot policy helper
     *  @status implemented
     *  @node   GenericTokenMorphPolicy#selectFromAvailability
     */
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

    /** @intent Produce a deterministic consumption candidate order for direct-variant usage under policy A.
     *  @wave   3 - implemented consumption-order helper
     *  @status implemented
     *  @node   GenericTokenMorphPolicy#orderForConsumption
     */
    @Nonnull
    public static List<GenericVariantMatcher.VariantAvailability> orderForConsumption(
            @Nonnull List<GenericVariantMatcher.VariantAvailability> availability) {
        return availability.stream()
                .sorted(LARGEST_STACK_THEN_RESOLVER_ORDER)
                .toList();
    }
}