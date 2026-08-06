package com.CodeCreature.crafting;

import java.util.List;

import javax.annotation.Nonnull;

import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

public class IndexedGenericIngredientResolver implements GenericIngredientResolver {

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
