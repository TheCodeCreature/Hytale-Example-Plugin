package com.CodeCreature.crafting;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class GenericTokenMorphPolicyTest {

        @Test
        void orderForConsumptionLargestThenResolverOrder() {
                List<GenericVariantMatcher.VariantAvailability> unordered = List.of(
                                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Decorative", 2, 1),
                                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Planks", 4, 2),
                                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Raw", 4, 0));

                List<String> orderedIds = GenericTokenMorphPolicy.orderForConsumption(unordered).stream()
                                .map(GenericVariantMatcher.VariantAvailability::itemId)
                                .collect(Collectors.toList());

                assertEquals(List.of("Wood_Hardwood_Raw", "Wood_Hardwood_Planks", "Wood_Hardwood_Decorative"), orderedIds);
        }

    @Test
    void selectFromAvailabilityChoosesLargestThenResolverOrder() {
        GenericIngredientResolution resolution = new GenericIngredientResolution(
                new GenericIngredientIdentity(null, "Hardwood", 3),
                List.of("Wood_Hardwood_Planks", "Wood_Hardwood_Decorative"),
                "Wood_Hardwood_Planks",
                true);

        List<GenericVariantMatcher.VariantAvailability> tied = List.of(
                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Planks", 3, 0),
                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Decorative", 3, 1));

        GenericTokenMorphPolicy.MorphSelection tieSelection =
                GenericTokenMorphPolicy.selectFromAvailability(resolution, tied);
        assertEquals("Wood_Hardwood_Planks", tieSelection.concreteItemId());
        assertFalse(tieSelection.remainsGeneric());

        List<GenericVariantMatcher.VariantAvailability> uneven = List.of(
                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Planks", 1, 0),
                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Decorative", 4, 1));

        GenericTokenMorphPolicy.MorphSelection unevenSelection =
                GenericTokenMorphPolicy.selectFromAvailability(resolution, uneven);
        assertEquals("Wood_Hardwood_Decorative", unevenSelection.concreteItemId());
        assertFalse(unevenSelection.remainsGeneric());
    }

    @Test
    void selectFromAvailabilityNoMatchRemainsGeneric() {
        GenericIngredientResolution resolution = new GenericIngredientResolution(
                new GenericIngredientIdentity(null, "Hardwood", 2),
                List.of("Wood_Hardwood_Planks", "Wood_Hardwood_Decorative"),
                "Wood_Hardwood_Planks",
                true);

        List<GenericVariantMatcher.VariantAvailability> none = List.of(
                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Planks", 0, 0),
                new GenericVariantMatcher.VariantAvailability("Wood_Hardwood_Decorative", 0, 1));

        GenericTokenMorphPolicy.MorphSelection selection =
                GenericTokenMorphPolicy.selectFromAvailability(resolution, none);

        assertNull(selection.concreteItemId());
        assertTrue(selection.remainsGeneric());
    }
}
