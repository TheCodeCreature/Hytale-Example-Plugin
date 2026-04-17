package com.UnobstructedThirdPerson.portablebench;

import com.UnobstructedThirdPerson.portablebench.PortableBenchConfig.CategoryDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortableBenchConfigTest {

    private static final CategoryDef[] BUILDER_CATEGORIES = {
            new CategoryDef("Blocks", "Blocks", "", new String[]{"WoodPlanks", "Bricks"}),
            new CategoryDef("Stairs", "Stairs & Slabs", "", new String[]{"Stairs", "HalfSlab"})
    };

    @Test
    void constructsWithValidArguments() {
        var config = new PortableBenchConfig("Builders", "server.items.Bench_Builders.name", BUILDER_CATEGORIES);

        assertEquals("Builders", config.benchId());
        assertEquals("server.items.Bench_Builders.name", config.benchName());
        assertEquals(2, config.categories().length);
        assertEquals("Blocks", config.categories()[0].id());
    }

    @Test
    void rejectsNullBenchId() {
        assertThrows(NullPointerException.class, () ->
                new PortableBenchConfig(null, "name", BUILDER_CATEGORIES));
    }

    @Test
    void rejectsNullBenchName() {
        assertThrows(NullPointerException.class, () ->
                new PortableBenchConfig("Builders", null, BUILDER_CATEGORIES));
    }

    @Test
    void rejectsNullCategories() {
        assertThrows(NullPointerException.class, () ->
                new PortableBenchConfig("Builders", "name", null));
    }

    @Test
    void rejectsEmptyCategories() {
        assertThrows(IllegalArgumentException.class, () ->
                new PortableBenchConfig("Builders", "name", new CategoryDef[]{}));
    }

    @Test
    void categoriesArrayIsDefensivelyCopied() {
        CategoryDef[] original = {
                new CategoryDef("Blocks", "Blocks", "", new String[]{"WoodPlanks"})
        };
        var config = new PortableBenchConfig("Builders", "name", original);

        // Mutating the original array should not affect the config
        original[0] = new CategoryDef("MUTATED", "MUTATED", "", new String[]{"X"});
        assertEquals("Blocks", config.categories()[0].id());
    }

    @Test
    void equalConfigsAreEqual() {
        var a = new PortableBenchConfig("Builders", "name", BUILDER_CATEGORIES);
        var b = new PortableBenchConfig("Builders", "name", BUILDER_CATEGORIES);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentConfigsAreNotEqual() {
        var a = new PortableBenchConfig("Builders", "name", BUILDER_CATEGORIES);
        var b = new PortableBenchConfig("Furniture", "name",
                new CategoryDef[]{new CategoryDef("Beds", "Beds", "", new String[]{"Furniture_Beds"})});
        assertNotEquals(a, b);
    }

    // --- CategoryDef tests ---

    @Test
    void categoryDefConstructsWithValidArguments() {
        var cat = new CategoryDef("Blocks", "Blocks", "icon.png", new String[]{"WoodPlanks", "Bricks"});
        assertEquals("Blocks", cat.id());
        assertEquals("Blocks", cat.name());
        assertEquals("icon.png", cat.icon());
        assertArrayEquals(new String[]{"WoodPlanks", "Bricks"}, cat.recipeCategoryIds());
    }

    @Test
    void categoryDefRejectsNullId() {
        assertThrows(NullPointerException.class, () ->
                new CategoryDef(null, "name", "", new String[]{"X"}));
    }

    @Test
    void categoryDefRejectsNullName() {
        assertThrows(NullPointerException.class, () ->
                new CategoryDef("id", null, "", new String[]{"X"}));
    }

    @Test
    void categoryDefRejectsNullIcon() {
        assertThrows(NullPointerException.class, () ->
                new CategoryDef("id", "name", null, new String[]{"X"}));
    }

    @Test
    void categoryDefRejectsNullRecipeCategoryIds() {
        assertThrows(NullPointerException.class, () ->
                new CategoryDef("id", "name", "", null));
    }

    @Test
    void categoryDefRejectsEmptyRecipeCategoryIds() {
        assertThrows(IllegalArgumentException.class, () ->
                new CategoryDef("id", "name", "", new String[]{}));
    }

    @Test
    void categoryDefRecipeCategoryIdsDefensivelyCopied() {
        String[] original = {"WoodPlanks", "Bricks"};
        var cat = new CategoryDef("id", "name", "", original);

        original[0] = "MUTATED";
        assertEquals("WoodPlanks", cat.recipeCategoryIds()[0]);

        cat.recipeCategoryIds()[0] = "MUTATED";
        assertEquals("WoodPlanks", cat.recipeCategoryIds()[0]);
    }

    @Test
    void categoryDefEqualityWorks() {
        var a = new CategoryDef("Blocks", "Blocks", "", new String[]{"WoodPlanks"});
        var b = new CategoryDef("Blocks", "Blocks", "", new String[]{"WoodPlanks"});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        var c = new CategoryDef("Other", "Other", "", new String[]{"Bricks"});
        assertNotEquals(a, c);
    }
}
