package com.CodeCreature.ui.bench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.CodeCreature.ui.bench.AffordabilityMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for {@link AffordabilityMode}.
 */
class AffordabilityModeTest {

    @Nested
    class NextCycling {

        @Test
        void inventoryDrivenCyclesToResourcePlanning() {
            assertEquals(AffordabilityMode.RESOURCE_PLANNING, AffordabilityMode.INVENTORY_DRIVEN.next());
        }

        @Test
        void resourcePlanningCyclesToInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.RESOURCE_PLANNING.next());
        }

        @Test
        void fullCycleFromInventoryDrivenReturnsToInventoryDriven() {
            AffordabilityMode mode = AffordabilityMode.INVENTORY_DRIVEN;
            mode = mode.next().next();
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, mode);
        }

        @Test
        void fullCycleFromResourcePlanningReturnsToResourcePlanning() {
            AffordabilityMode mode = AffordabilityMode.RESOURCE_PLANNING;
            mode = mode.next().next();
            assertEquals(AffordabilityMode.RESOURCE_PLANNING, mode);
        }
    }

    @Nested
    class FromString {

        @Test
        void parsesInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.fromString("INVENTORY_DRIVEN"));
        }

        @Test
        void parsesResourcePlanning() {
            assertEquals(AffordabilityMode.RESOURCE_PLANNING, AffordabilityMode.fromString("RESOURCE_PLANNING"));
        }

        @Test
        void legacyTrueMigratesToInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.fromString("true"));
        }

        @Test
        void legacyFalseMigratesToResourcePlanning() {
            assertEquals(AffordabilityMode.RESOURCE_PLANNING, AffordabilityMode.fromString("false"));
        }

        @Test
        void nullDefaultsToInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.fromString(null));
        }

        @Test
        void unrecognizedDefaultsToInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.fromString("garbage"));
        }
    }
}
