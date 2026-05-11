package com.CodeCreature.placeblock.ui;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.CodeCreature.placeblock.ui.AffordabilityMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for {@link AffordabilityMode}.
 */
class AffordabilityModeTest {

    @Nested
    class NextCycling {

        @Test
        void allCyclesToInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.ALL.next());
        }

        @Test
        void inventoryDrivenCyclesToResourceDriven() {
            assertEquals(AffordabilityMode.RESOURCE_DRIVEN, AffordabilityMode.INVENTORY_DRIVEN.next());
        }

        @Test
        void resourceDrivenCyclesToAll() {
            assertEquals(AffordabilityMode.ALL, AffordabilityMode.RESOURCE_DRIVEN.next());
        }

        @Test
        void fullCycleFromAllReturnsToAll() {
            AffordabilityMode mode = AffordabilityMode.ALL;
            mode = mode.next().next().next();
            assertEquals(AffordabilityMode.ALL, mode);
        }

        @Test
        void fullCycleFromInventoryDrivenReturnsToInventoryDriven() {
            AffordabilityMode mode = AffordabilityMode.INVENTORY_DRIVEN;
            mode = mode.next().next().next();
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, mode);
        }

        @Test
        void fullCycleFromResourceDrivenReturnsToResourceDriven() {
            AffordabilityMode mode = AffordabilityMode.RESOURCE_DRIVEN;
            mode = mode.next().next().next();
            assertEquals(AffordabilityMode.RESOURCE_DRIVEN, mode);
        }
    }

    @Nested
    class Label {

        @Test
        void allReturnsAll() {
            assertEquals("All", AffordabilityMode.ALL.label());
        }

        @Test
        void inventoryDrivenReturnsInventoryDriven() {
            assertEquals("Inventory Driven", AffordabilityMode.INVENTORY_DRIVEN.label());
        }

        @Test
        void resourceDrivenReturnsResourceDriven() {
            assertEquals("Resource Driven", AffordabilityMode.RESOURCE_DRIVEN.label());
        }
    }

    @Nested
    class FromString {

        @Test
        void parsesAll() {
            assertEquals(AffordabilityMode.ALL, AffordabilityMode.fromString("ALL"));
        }

        @Test
        void parsesInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.fromString("INVENTORY_DRIVEN"));
        }

        @Test
        void parsesResourceDriven() {
            assertEquals(AffordabilityMode.RESOURCE_DRIVEN, AffordabilityMode.fromString("RESOURCE_DRIVEN"));
        }

        @Test
        void legacyTrueMigratesToInventoryDriven() {
            assertEquals(AffordabilityMode.INVENTORY_DRIVEN, AffordabilityMode.fromString("true"));
        }

        @Test
        void legacyFalseMigratesToAll() {
            assertEquals(AffordabilityMode.ALL, AffordabilityMode.fromString("false"));
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
