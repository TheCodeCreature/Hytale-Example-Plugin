package com.CodeCreature.crafting;

import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @node    IndexedGenericIngredientResolver
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Resolves authored material inputs using the pre-indexed resource-type lookup so variant
 *          ordering stays aligned with ResourceTypeResolver index semantics.
 * @wave    4 (generic icons + generic affordability boundary)
 * @status  Wave 4 - implemented indexed generic resolver for typed affordability boundaries
 * @do-not  Change representative item selection policy independently of ResourceTypeResolver.
 *          Collapse generic inputs into direct concrete identity.
 */
public class IndexedGenericIngredientResolver implements GenericIngredientResolver {

    /** @intent Resolve a single authored input to typed generic identity plus ordered concrete variants.
     *  @wave   4 - implemented indexed resolver behavior
     *  @status implemented
     *  @node   IndexedGenericIngredientResolver#resolve
     */
    @Override
    @Nonnull
    public GenericIngredientResolution resolve(@Nonnull MaterialQuantity input, boolean preferNatural) {
        String itemId = normalizeItemId(input.getItemId());
        String resourceTypeId = normalizeId(input.getResourceTypeId());
        int quantity = Math.max(input.getQuantity(), 0);

        if (itemId != null) {
            GenericIngredientIdentity identity = new GenericIngredientIdentity(itemId, null, quantity);
            return new GenericIngredientResolution(identity, List.of(itemId), itemId, false);
        }

        if (resourceTypeId != null) {
            List<String> orderedMatches = ResourceTypeResolver.getAllMatchingItemIds(resourceTypeId);
            String representativeItemId = ResourceTypeResolver.resolveInputItemId(input, preferNatural);
            if ((representativeItemId == null || representativeItemId.isEmpty()) && !orderedMatches.isEmpty()) {
                representativeItemId = orderedMatches.get(0);
            }
            GenericIngredientIdentity identity = new GenericIngredientIdentity(null, resourceTypeId, quantity);
            return new GenericIngredientResolution(identity, orderedMatches, representativeItemId, true);
        }

        GenericIngredientIdentity identity = new GenericIngredientIdentity(null, null, quantity);
        return new GenericIngredientResolution(identity, List.of(), null, false);
    }

    private static String normalizeItemId(String itemId) {
        String normalized = normalizeId(itemId);
        if ("Empty".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
