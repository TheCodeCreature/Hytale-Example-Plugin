package com.CodeCreature.crafting;

/**
 * @node    GenericVariantMatcher
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Provides one shared generic-variant inventory matching boundary for planner and
 *          affordability flows, including stencil-exclusion semantics and deterministic variant order.
 * @wave    2 (matcher consolidation)
 * @status  Wave 2 - implemented shared variant counting and availability projection
 * @do-not  Imply final engine removeMaterials ordering from these match snapshots.
 *          Collapse generic fallback policy into this matcher.
 */

import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

public final class GenericVariantMatcher {

    /** @intent Snapshot one concrete variant's available quantity while preserving resolver-order index for deterministic tie-breaks.
     *  @wave   2 - implemented shared matcher availability record
     *  @status implemented
     *  @node   GenericVariantMatcher.VariantAvailability
     */
    public record VariantAvailability(@Nonnull String itemId, int quantity, int resolverOrder) {}

    private GenericVariantMatcher() {}

    /** @intent Collect per-variant availability in resolver order with optional concrete fallback when no typed variants are present.
     *  @wave   2 - implemented shared availability projection
     *  @status implemented
     *  @node   GenericVariantMatcher#collectVariantAvailability
     */
    @Nonnull
    public static List<VariantAvailability> collectVariantAvailability(@Nonnull GenericIngredientResolution resolution,
                                                                       @Nullable String fallbackItemId,
                                                                       @Nonnull CombinedItemContainer container,
                                                                       boolean excludeStencils) {
        LinkedHashMap<String, Integer> orderedIds = collectOrderedConcreteVariantIds(resolution, fallbackItemId);
        List<VariantAvailability> availability = new ArrayList<>(orderedIds.size());
        int resolverOrder = 0;
        for (String itemId : orderedIds.keySet()) {
            int qty = countConcreteItem(container, itemId, excludeStencils);
            availability.add(new VariantAvailability(itemId, qty, resolverOrder++));
        }
        return List.copyOf(availability);
    }

    /** @intent Count total matching quantity for a typed ingredient using the shared concrete-variant matcher semantics.
     *  @wave   2 - implemented shared total counting helper
     *  @status implemented
     *  @node   GenericVariantMatcher#countMatchingQuantity
     */
    public static int countMatchingQuantity(@Nonnull GenericIngredientResolution resolution,
                                            @Nullable String fallbackItemId,
                                            @Nonnull CombinedItemContainer container,
                                            boolean excludeStencils) {
        int total = 0;
        for (VariantAvailability availability : collectVariantAvailability(resolution, fallbackItemId, container, excludeStencils)) {
            total += availability.quantity();
        }
        return total;
    }

    /** @intent Count a single concrete item ID using the same stencil-exclusion semantics as generic matching callers.
     *  @wave   2 - implemented shared concrete count adapter
     *  @status implemented
     *  @node   GenericVariantMatcher#countConcreteQuantity
     */
    public static int countConcreteQuantity(@Nonnull CombinedItemContainer container,
                                            @Nonnull String itemId,
                                            boolean excludeStencils) {
        return countConcreteItem(container, itemId, excludeStencils);
    }

    /** @intent Build a deterministic concrete variant ID set from typed resolution and gatherable mapping while de-duplicating aliases.
     *  @wave   2 - implemented internal ordered-variant projection
     *  @status implemented
     *  @node   GenericVariantMatcher#collectOrderedConcreteVariantIds
     */
    @Nonnull
    private static LinkedHashMap<String, Integer> collectOrderedConcreteVariantIds(@Nonnull GenericIngredientResolution resolution,
                                                                                    @Nullable String fallbackItemId) {
        LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
        int resolverOrder = 0;
        for (String variantId : resolution.orderedMatchingItemIds()) {
            String resolved = NaturalResourceRegistry.resolveToGatherableForm(variantId);
            if (!resolved.isEmpty()) {
                ordered.putIfAbsent(resolved, resolverOrder);
            }
            resolverOrder++;
        }

        String concreteItemId = resolution.identity().itemId();
        if (concreteItemId != null && !concreteItemId.isEmpty()) {
            ordered.putIfAbsent(NaturalResourceRegistry.resolveToGatherableForm(concreteItemId), resolverOrder++);
        }

        if (ordered.isEmpty() && fallbackItemId != null && !fallbackItemId.isEmpty()) {
            ordered.put(NaturalResourceRegistry.resolveToGatherableForm(fallbackItemId), resolverOrder);
        }

        return ordered;
    }

    /** @intent Count one concrete item with optional stencil filtering so planner and direct affordability share identical exclusion semantics.
     *  @wave   2 - implemented shared concrete count helper
     *  @status implemented
     *  @node   GenericVariantMatcher#countConcreteItem
     */
    private static int countConcreteItem(@Nonnull CombinedItemContainer container,
                                         @Nonnull String itemId,
                                         boolean excludeStencils) {
        Predicate<ItemStack> predicate = stack -> itemId.equals(stack.getItemId());
        if (excludeStencils) {
            predicate = predicate.and(stack -> !StencilMetadata.isStencil(stack));
        }
        return container.countItemStacks(predicate);
    }
}