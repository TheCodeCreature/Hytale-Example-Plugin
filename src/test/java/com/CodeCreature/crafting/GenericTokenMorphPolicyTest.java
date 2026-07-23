package com.CodeCreature.crafting;

/**
 * @node    GenericTokenMorphPolicyTest
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Verifies locked generic token morph policy behavior: largest matching stack selection,
 *          deterministic tie-break, and remain-generic fallback when no concrete stack matches.
 * @wave    5 (surface parity + regression coverage)
 * @status  Wave 5 - implemented policy contract tests for generic token morph helper
 * @do-not  Assert engine removeMaterials internals from these pure policy tests.
 *          Expand this suite into planner raw-cost integration behavior.
 */

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericTokenMorphPolicyTest {

        /** @intent Consumption ordering helper must preserve largest-stack-first and resolver-order tie-break semantics.
         *  @wave   5 - implemented ordering helper contract coverage
         *  @status implemented
         *  @node   GenericTokenMorphPolicyTest#orderForConsumptionLargestThenResolverOrder
         */
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

    /** @intent Policy A should choose the largest stack and keep resolver ordering as deterministic tie-break.
     *  @wave   5 - implemented selection contract coverage
     *  @status implemented
     *  @node   GenericTokenMorphPolicyTest#selectFromAvailabilityChoosesLargestThenResolverOrder
     */
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

    /** @intent No matching concrete stack for a generic token must keep the token generic instead of forcing representative morph.
     *  @wave   5 - implemented no-match fallback contract coverage
     *  @status implemented
     *  @node   GenericTokenMorphPolicyTest#selectFromAvailabilityNoMatchRemainsGeneric
     */
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
