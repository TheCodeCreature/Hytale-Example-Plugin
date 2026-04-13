package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NaturalStackSizeModifier}.
 * Validates that natural resource items get their maxStack multiplied,
 * and items with maxStack <= 1 are skipped.
 */
class NaturalStackSizeModifierTest {

    private TestDataSet data;

    @BeforeEach
    void setUp() {
        data = new TestDataSet();
        data.install();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void naturalItemStackSizeMultiplied() {
        NaturalStackSizeModifier.apply();

        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(100 * m, readItemMaxStack(data.itemRockStone),
                "Rock_Stone maxStack should be 100 * 12 = 1200");
        assertEquals(100 * m, readItemMaxStack(data.itemWoodLogOak),
                "Wood_Log_Oak maxStack should be 100 * 12 = 1200");
    }

    @Test
    void differentOriginalStackSizesScaledCorrectly() {
        NaturalStackSizeModifier.apply();

        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(100 * m, readItemMaxStack(data.itemDirt),
                "Dirt (100) should become 1200");
        assertEquals(100 * m, readItemMaxStack(data.itemSand),
                "Sand (100) should become 1200");
        assertEquals(100 * m, readItemMaxStack(data.itemPlantFiber),
                "Plant_Fiber (100) should become 1200");
        assertEquals(64 * m, readItemMaxStack(data.itemBerry),
                "Berry (64) should become 768");
    }

    @Test
    void nonNaturalItemsNotChanged() {
        NaturalStackSizeModifier.apply();

        // Metal_Ingot_Iron is not in the natural registry
        assertEquals(100, readItemMaxStack(data.itemMetalIngotIron),
                "Non-natural item (Metal_Ingot_Iron) should be unchanged");
    }

    @Test
    void recipeBlockItemsNotChanged() {
        NaturalStackSizeModifier.apply();

        // Recipe block items should not be in naturalItemIds by default
        assertEquals(100, readItemMaxStack(data.itemWoodPlanksOak),
                "Wood_Planks_Oak (recipe block item) should be unchanged");
        assertEquals(100, readItemMaxStack(data.itemRailIron),
                "Rail_Iron (recipe block item) should be unchanged");
    }

    @Test
    void maxStackOfOneSkipped() {
        // Add an item with maxStack=1 (e.g., a tool or weapon)
        Item singleStackItem = item("Unique_Tool", null, false, 1);
        data.items.put("Unique_Tool", singleStackItem);
        data.naturalItemIds.add("Unique_Tool");
        data.install();

        NaturalStackSizeModifier.apply();

        assertEquals(1, readItemMaxStack(singleStackItem),
                "Items with maxStack=1 should not be multiplied");
    }

    @Test
    void maxStackOfZeroSkipped() {
        Item zeroStackItem = item("Zero_Stack", null, false, 0);
        data.items.put("Zero_Stack", zeroStackItem);
        data.naturalItemIds.add("Zero_Stack");
        data.install();

        NaturalStackSizeModifier.apply();

        assertEquals(0, readItemMaxStack(zeroStackItem),
                "Items with maxStack=0 should not be multiplied");
    }

    @Test
    void negativeMaxStackSkipped() {
        // -1 means "default" in Hytale's system
        Item negativeStackItem = item("Default_Stack", null, false, -1);
        data.items.put("Default_Stack", negativeStackItem);
        data.naturalItemIds.add("Default_Stack");
        data.install();

        NaturalStackSizeModifier.apply();

        assertEquals(-1, readItemMaxStack(negativeStackItem),
                "Items with maxStack=-1 (default) should not be multiplied");
    }

    @Test
    void emptyNaturalRegistryDoesNothing() {
        setNaturalRegistry(new java.util.HashSet<>(), new java.util.HashSet<>());

        assertDoesNotThrow(() -> NaturalStackSizeModifier.apply());

        // Original items unchanged
        assertEquals(100, readItemMaxStack(data.itemRockStone));
    }

    @Test
    void missingItemInStoreSkipped() {
        // Add a natural item ID that doesn't exist in the Item store
        data.naturalItemIds.add("Nonexistent_Item");
        data.install();

        assertDoesNotThrow(() -> NaturalStackSizeModifier.apply());
    }
}
