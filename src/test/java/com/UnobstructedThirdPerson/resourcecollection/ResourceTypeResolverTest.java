package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ResourceTypeResolver}.
 *
 * <p>Each test installs its own minimal item map for precise control over
 * which items exist, their insertion order, and their set/natural status.
 */
class ResourceTypeResolverTest {

    @AfterEach
    void tearDown() {
        cleanup();
    }

    // ── Shared item builders (modeled after real Hytale asset data) ──

    /** Wood_Oak_Trunk: natural, set="Wood_Oak" (not a set-root — id != set). */
    private static Item logOak() {
        return item("Wood_Log_Oak", "Wood_Log_Oak", true, 100,
                "Wood_Oak",
                resourceType("Wood_All"), resourceType("Wood_Hardwood"),
                resourceType("Wood_Trunk"), resourceType("Fuel"));
    }

    /** Wood_Hardwood_Planks: non-natural, set="Wood_Hardwood_Planks" (set-root — id == set). */
    private static Item planks() {
        return item("Wood_Hardwood_Planks", "Wood_Hardwood_Planks", true, 100,
                "Wood_Hardwood_Planks",
                resourceType("Wood_Planks"), resourceType("Wood_Hardwood"));
    }

    /** Wood_Hardwood_Decorative: non-natural, set="Wood_Hardwood_Planks" (derivative — id != set). */
    private static Item decorative() {
        return item("Wood_Hardwood_Decorative", "Wood_Hardwood_Decorative", true, 100,
                "Wood_Hardwood_Planks",
                resourceType("Wood_Hardwood"));
    }

    /** Wood_Hardwood_Ornate: non-natural, set="Wood_Hardwood_Planks" (derivative — id != set). */
    private static Item ornate() {
        return item("Wood_Hardwood_Ornate", "Wood_Hardwood_Ornate", true, 100,
                "Wood_Hardwood_Planks",
                resourceType("Wood_Hardwood"));
    }

    /** Wood_Log_Birch: natural, set="Wood_Birch", declares Wood_Trunk. */
    private static Item logBirch() {
        return item("Wood_Log_Birch", "Wood_Log_Birch", true, 100,
                "Wood_Birch",
                resourceType("Wood_All"), resourceType("Wood_Trunk"));
    }

    private void installMinimal(Map<String, Item> items) {
        installItems(items);
        setNaturalRegistry(
                Set.of("Rock_Stone", "Wood_Log_Oak", "Wood_Log_Birch"),
                Set.of("Rock_Stone", "Wood_Log_Oak", "Wood_Log_Birch"));
    }

    // ────────────────────────────────────────────────────────────
    @Nested
    class ResolveByResourceType {

        @Nested
        class WhenBuildersCategory {

            @Test
            void selectsPlanksWhenInsertedFirst() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Planks", planks());
                items.put("Wood_Hardwood_Decorative", decorative());
                items.put("Wood_Hardwood_Ornate", ornate());
                installMinimal(items);

                assertEquals("Wood_Hardwood_Planks",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.BUILDERS_ONLY));
            }

            @Test
            void selectsPlanksWhenInsertedLast() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Ornate", ornate());
                items.put("Wood_Hardwood_Decorative", decorative());
                items.put("Wood_Hardwood_Planks", planks());
                installMinimal(items);

                assertEquals("Wood_Hardwood_Planks",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.BUILDERS_ONLY));
            }

            @Test
            void selectsPlanksWhenDecorativeInsertedFirst() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Decorative", decorative());
                items.put("Wood_Hardwood_Planks", planks());
                items.put("Wood_Hardwood_Ornate", ornate());
                installMinimal(items);

                assertEquals("Wood_Hardwood_Planks",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.BUILDERS_ONLY));
            }

            @Test
            void skipsNaturalItemsInPass1() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Log_Oak", logOak());
                items.put("Wood_Hardwood_Decorative", decorative());
                items.put("Wood_Hardwood_Planks", planks());
                installMinimal(items);

                assertEquals("Wood_Hardwood_Planks",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.BUILDERS_ONLY),
                        "Builders should not pick natural Wood_Log_Oak");
            }

            @Test
            void picksADerivativeWhenNoSetRootExists() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Decorative", decorative());
                items.put("Wood_Hardwood_Ornate", ornate());
                installMinimal(items);

                String result = ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.BUILDERS_ONLY);
                assertNotNull(result, "Should find a non-natural derivative as fallback");
                assertTrue(result.equals("Wood_Hardwood_Decorative") || result.equals("Wood_Hardwood_Ornate"),
                        "Should pick one of the available derivatives");
            }

            @Test
            void fallsToPass2WhenOnlyNaturalItemsExist() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Log_Oak", logOak());
                installMinimal(items);

                assertEquals("Wood_Log_Oak",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.BUILDERS_ONLY),
                        "Pass 2 should fall back to natural item when no non-natural exists");
            }
        }

        @Nested
        class WhenFurnitureCategory {

            @Test
            void prefersNaturalItem() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Planks", planks());
                items.put("Wood_Log_Oak", logOak());
                items.put("Wood_Hardwood_Decorative", decorative());
                installMinimal(items);

                assertEquals("Wood_Log_Oak",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.FURNITURE_ONLY),
                        "Furniture should prefer natural Wood_Log_Oak");
            }

            @Test
            void fallsToSetRootWhenNoNaturalItemExists() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Decorative", decorative());
                items.put("Wood_Hardwood_Planks", planks());
                items.put("Wood_Hardwood_Ornate", ornate());
                installMinimal(items);

                assertEquals("Wood_Hardwood_Planks",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.FURNITURE_ONLY),
                        "Pass 2 should pick set-root Planks even for Furniture when no natural exists");
            }
        }

        @Nested
        class WhenBuildersAndFurnitureCategory {

            @Test
            void preferNaturalWinsFurniture() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Planks", planks());
                items.put("Wood_Log_Oak", logOak());
                items.put("Wood_Hardwood_Decorative", decorative());
                installMinimal(items);

                assertEquals("Wood_Log_Oak",
                        ResourceTypeResolver.resolveByResourceType("Wood_Hardwood", BenchCategory.BUILDERS_AND_FURNITURE),
                        "Dual-bench category should resolve to natural (furniture preference wins)");
            }
        }

        @Nested
        class WhenNoItemsMatchResourceType {

            @Test
            void returnsNullForUnknownResourceType() {
                var items = new LinkedHashMap<String, Item>();
                items.put("Wood_Hardwood_Planks", planks());
                installMinimal(items);

                assertNull(
                        ResourceTypeResolver.resolveByResourceType("Stone_Granite", BenchCategory.BUILDERS_ONLY),
                        "Unknown ResourceTypeId should return null");
            }
        }
    }

    // ────────────────────────────────────────────────────────────
    @Nested
    class ResolveInputItemId {

        @Test
        void returnsDirectItemIdWhenExists() {
            var items = new LinkedHashMap<String, Item>();
            items.put("Wood_Hardwood_Planks", planks());
            installMinimal(items);

            assertEquals("Wood_Hardwood_Planks",
                    ResourceTypeResolver.resolveInputItemId(
                            materialQty("Wood_Hardwood_Planks", 1), BenchCategory.BUILDERS_ONLY));
        }

        @Test
        void returnsNullForMissingDirectItemId() {
            var items = new LinkedHashMap<String, Item>();
            items.put("Wood_Hardwood_Planks", planks());
            installMinimal(items);

            assertNull(ResourceTypeResolver.resolveInputItemId(
                    materialQty("Nonexistent_Item", 1), BenchCategory.BUILDERS_ONLY));
        }

        @Test
        void delegatesToResolveByResourceType() {
            var items = new LinkedHashMap<String, Item>();
            items.put("Wood_Hardwood_Ornate", ornate());
            items.put("Wood_Log_Oak", logOak());
            items.put("Wood_Hardwood_Decorative", decorative());
            items.put("Wood_Hardwood_Planks", planks());
            installMinimal(items);

            assertEquals("Wood_Hardwood_Planks",
                    ResourceTypeResolver.resolveInputItemId(
                            materialQtyResource("Wood_Hardwood", 1), BenchCategory.BUILDERS_ONLY),
                    "ResourceTypeId input should delegate and pick set-root Planks");
        }

        @Test
        void treatsEmptyItemIdAsNoItemId() {
            var items = new LinkedHashMap<String, Item>();
            items.put("Wood_Hardwood_Planks", planks());
            items.put("Wood_Log_Oak", logOak());
            installMinimal(items);

            MaterialQuantity input = new MaterialQuantity("Empty", "Wood_Hardwood", null, 1, null);
            assertEquals("Wood_Hardwood_Planks",
                    ResourceTypeResolver.resolveInputItemId(input, BenchCategory.BUILDERS_ONLY),
                    "\"Empty\" ItemId should be ignored, falling through to ResourceTypeId");
        }

        @Test
        void returnsNullWhenNoItemIdAndNoResourceTypeId() {
            var items = new LinkedHashMap<String, Item>();
            items.put("Wood_Hardwood_Planks", planks());
            installMinimal(items);

            // MaterialQuantity requires at least one non-null identifier;
            // use tag-only input which has no ItemId and no ResourceTypeId
            MaterialQuantity input = new MaterialQuantity(null, null, "SomeTag", 1, null);
            assertNull(ResourceTypeResolver.resolveInputItemId(input, BenchCategory.BUILDERS_ONLY),
                    "Input with no ItemId and no ResourceTypeId should return null");
        }
    }
}
