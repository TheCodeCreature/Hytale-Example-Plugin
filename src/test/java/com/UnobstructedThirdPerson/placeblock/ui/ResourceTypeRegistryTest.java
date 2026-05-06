package com.UnobstructedThirdPerson.placeblock.ui;

import com.UnobstructedThirdPerson.placeblock.ui.ResourceTypeRegistry.ResourceTypeEntry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for {@link ResourceTypeRegistry}.
 */
class ResourceTypeRegistryTest {

    @Nested
    class GetCount {

        @Test
        void returns73() {
            assertEquals(73, ResourceTypeRegistry.getCount());
        }
    }

    @Nested
    class GetAll {

        @Test
        void returnsNonEmptyList() {
            assertFalse(ResourceTypeRegistry.getAll().isEmpty());
        }

        @Test
        void returnsImmutableList() {
            List<ResourceTypeEntry> all = ResourceTypeRegistry.getAll();
            assertThrows(UnsupportedOperationException.class,
                    () -> all.add(new ResourceTypeEntry("Test", "Test.png", 999, false)));
        }

        @Test
        void isSortedAlphabeticallyByResourceTypeId() {
            List<ResourceTypeEntry> all = ResourceTypeRegistry.getAll();
            for (int i = 1; i < all.size(); i++) {
                String prev = all.get(i - 1).resourceTypeId();
                String curr = all.get(i).resourceTypeId();
                assertTrue(prev.compareTo(curr) < 0,
                        "Expected '%s' before '%s' in alphabetical order".formatted(prev, curr));
            }
        }

        @Test
        void firstEntryIsBlackwood() {
            assertEquals("Blackwood", ResourceTypeRegistry.getAll().getFirst().resourceTypeId());
        }

        @Test
        void lastEntryIsWoodTrunkTemp() {
            List<ResourceTypeEntry> all = ResourceTypeRegistry.getAll();
            assertEquals("Wood_Trunk_Temp", all.getLast().resourceTypeId());
        }
    }

    @Nested
    class GetIconPath {

        @Test
        void returnsIconForHardwood() {
            assertEquals("Hardwood.png", ResourceTypeRegistry.getIconPath("Hardwood"));
        }

        @Test
        void returnsIconForRockGroup() {
            assertEquals("Any_Rock.png", ResourceTypeRegistry.getIconPath("Rock_Group"));
        }

        @Test
        void returnsNullForNonexistent() {
            assertNull(ResourceTypeRegistry.getIconPath("nonexistent"));
        }

        @Test
        void returnsNullForNull() {
            assertNull(ResourceTypeRegistry.getIconPath(null));
        }
    }

    @Nested
    class EntryIntegrity {

        @Test
        void everyEntryHasNonEmptyResourceTypeId() {
            for (ResourceTypeEntry entry : ResourceTypeRegistry.getAll()) {
                assertNotNull(entry.resourceTypeId(),
                        "resourceTypeId must not be null");
                assertFalse(entry.resourceTypeId().isEmpty(),
                        "resourceTypeId must not be empty");
            }
        }

        @Test
        void everyEntryHasNonEmptyIconFilename() {
            for (ResourceTypeEntry entry : ResourceTypeRegistry.getAll()) {
                assertNotNull(entry.iconFilename(),
                        "iconFilename must not be null for " + entry.resourceTypeId());
                assertFalse(entry.iconFilename().isEmpty(),
                        "iconFilename must not be empty for " + entry.resourceTypeId());
            }
        }

        @Test
        void everyIconFilenameEndsWithPng() {
            for (ResourceTypeEntry entry : ResourceTypeRegistry.getAll()) {
                assertTrue(entry.iconFilename().endsWith(".png"),
                        "Expected .png extension for " + entry.resourceTypeId()
                                + " but got " + entry.iconFilename());
            }
        }

        @Test
        void noDuplicateResourceTypeIds() {
            List<ResourceTypeEntry> all = ResourceTypeRegistry.getAll();
            Set<String> seen = new HashSet<>();
            for (ResourceTypeEntry entry : all) {
                assertTrue(seen.add(entry.resourceTypeId()),
                        "Duplicate resourceTypeId: " + entry.resourceTypeId());
            }
        }

        @Test
        void sortOrderValuesAreSequential() {
            List<ResourceTypeEntry> all = ResourceTypeRegistry.getAll();
            for (int i = 0; i < all.size(); i++) {
                assertEquals(i, all.get(i).sortOrder(),
                        "Expected sortOrder %d for entry '%s' at index %d"
                                .formatted(i, all.get(i).resourceTypeId(), i));
            }
        }
    }
}
