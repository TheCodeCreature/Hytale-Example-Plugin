package com.UnobstructedThirdPerson.portablebench;

import com.UnobstructedThirdPerson.portablebench.PortableBenchConfig.CategoryDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortableBenchRegistryTest {

    private static final PortableBenchConfig BUILDERS_CONFIG = new PortableBenchConfig(
            "Builders", "server.items.Bench_Builders.name",
            new CategoryDef[]{
                    new CategoryDef("Blocks", "Blocks", "", new String[]{"WoodPlanks", "Bricks"})
            }
    );

    private static final PortableBenchConfig FURNITURE_CONFIG = new PortableBenchConfig(
            "Furniture_Bench", "server.items.Bench_Furniture.name",
            new CategoryDef[]{
                    new CategoryDef("Storage", "Storage", "", new String[]{"Furniture_Storage"})
            }
    );

    @AfterEach
    void tearDown() {
        PortableBenchRegistry.clear();
    }

    @Test
    void registersAndRetrievesConfig() {
        PortableBenchRegistry.register("PortableBench_Builders", BUILDERS_CONFIG);

        var retrieved = PortableBenchRegistry.getConfig("PortableBench_Builders");
        assertEquals(BUILDERS_CONFIG, retrieved);
    }

    @Test
    void returnsNullForUnknownItemId() {
        assertNull(PortableBenchRegistry.getConfig("NonExistent"));
    }

    @Test
    void returnsNullForNullItemId() {
        assertNull(PortableBenchRegistry.getConfig(null));
    }

    @Test
    void hasConfigReturnsTrueForRegistered() {
        PortableBenchRegistry.register("PortableBench_Builders", BUILDERS_CONFIG);
        assertTrue(PortableBenchRegistry.hasConfig("PortableBench_Builders"));
    }

    @Test
    void hasConfigReturnsFalseForUnknown() {
        assertFalse(PortableBenchRegistry.hasConfig("NonExistent"));
    }

    @Test
    void hasConfigReturnsFalseForNull() {
        assertFalse(PortableBenchRegistry.hasConfig(null));
    }

    @Test
    void rejectsNullItemId() {
        assertThrows(IllegalArgumentException.class, () ->
                PortableBenchRegistry.register(null, BUILDERS_CONFIG));
    }

    @Test
    void rejectsBlankItemId() {
        assertThrows(IllegalArgumentException.class, () ->
                PortableBenchRegistry.register("  ", BUILDERS_CONFIG));
    }

    @Test
    void rejectsNullConfig() {
        assertThrows(IllegalArgumentException.class, () ->
                PortableBenchRegistry.register("PortableBench_Builders", null));
    }

    @Test
    void supportsMultipleConfigs() {
        PortableBenchRegistry.register("PortableBench_Builders", BUILDERS_CONFIG);
        PortableBenchRegistry.register("PortableBench_Furniture", FURNITURE_CONFIG);

        assertEquals(2, PortableBenchRegistry.size());
        assertEquals(BUILDERS_CONFIG, PortableBenchRegistry.getConfig("PortableBench_Builders"));
        assertEquals(FURNITURE_CONFIG, PortableBenchRegistry.getConfig("PortableBench_Furniture"));
    }

    @Test
    void clearRemovesAllConfigs() {
        PortableBenchRegistry.register("PortableBench_Builders", BUILDERS_CONFIG);
        PortableBenchRegistry.clear();

        assertEquals(0, PortableBenchRegistry.size());
        assertNull(PortableBenchRegistry.getConfig("PortableBench_Builders"));
    }

    @Test
    void overwritesExistingConfig() {
        PortableBenchRegistry.register("PortableBench_Builders", BUILDERS_CONFIG);
        PortableBenchRegistry.register("PortableBench_Builders", FURNITURE_CONFIG);

        assertEquals(FURNITURE_CONFIG, PortableBenchRegistry.getConfig("PortableBench_Builders"));
        assertEquals(1, PortableBenchRegistry.size());
    }
}
