package com.CodeCreature.crafting;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.CodeCreature.scaling.AssetTestHelper.cleanup;
import static com.CodeCreature.scaling.AssetTestHelper.installItems;
import static com.CodeCreature.scaling.AssetTestHelper.item;
import static com.CodeCreature.scaling.AssetTestHelper.materialQty;
import static com.CodeCreature.scaling.AssetTestHelper.materialQtyResource;
import static com.CodeCreature.scaling.AssetTestHelper.recipe;
import static com.CodeCreature.scaling.AssetTestHelper.resourceType;
import static com.CodeCreature.scaling.AssetTestHelper.setCraftedItemIds;
import static com.CodeCreature.scaling.AssetTestHelper.setNaturalRegistry;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

class AutoCraftPlannerParityTest {

    @AfterEach
    void tearDown() {
        cleanup();
        resetRawCostCache();
    }

    @Test
    void plannerAndFacadeAgreeForDirectGenericAffordability() {
        installHardwoodItems();
        CraftingRecipe recipe = hardwoodGenericRecipe(3);

        CombinedItemContainer container = containerWithStacks(List.of(
                stack("Wood_Hardwood_Planks", 1, false),
                stack("Wood_Hardwood_Decorative", 2, false)
        ));

        assertTrue(CraftingAffordabilityFacade.isAffordable(recipe, false, container));

        AutoCraftPlan plan = AutoCraftPlanner.plan(recipe, false, container);
        assertTrue(plan.affordable());
        assertFalse(plan.requiresAutoCraft());
    }

    @Test
    void plannerAndFacadeExcludeStencilTaggedStacks() {
        installHardwoodItems();
        CraftingRecipe recipe = hardwoodGenericRecipe(3);

        CombinedItemContainer container = containerWithStacks(List.of(
                stack("Wood_Hardwood_Planks", 1, false),
                stack("Wood_Hardwood_Planks", 5, true),
                stack("Wood_Hardwood_Decorative", 1, false)
        ));

        assertFalse(CraftingAffordabilityFacade.isAffordable(recipe, false, container));

        AutoCraftPlan plan = AutoCraftPlanner.plan(recipe, false, container);
        assertFalse(plan.affordable());
    }

    @Test
    void plannerDeficitExpansionUsesPolicyAMorphFirst() {
        installHardwoodItems();
        setCraftedItemIds(Set.of("Wood_Hardwood_Planks"));
        setRawCostCache(Map.of(
                "Wood_Hardwood_Planks", List.of(new RawMaterialRequirement("Rock_Stone_Cobble", 2))
        ));

        CraftingRecipe recipe = hardwoodGenericRecipe(5);
        CombinedItemContainer container = containerWithStacks(List.of(
                stack("Wood_Hardwood_Planks", 2, false),
                stack("Wood_Hardwood_Decorative", 1, false),
                stack("Rock_Stone_Cobble", 4, false)
        ));

        AutoCraftPlan plan = AutoCraftPlanner.plan(recipe, false, container);
        assertTrue(plan.affordable());
        assertTrue(plan.requiresAutoCraft());

        Map<String, Integer> merged = toMap(plan.consumptions());
        assertEquals(2, merged.getOrDefault("Wood_Hardwood_Planks", 0));
        assertEquals(1, merged.getOrDefault("Wood_Hardwood_Decorative", 0));
        assertEquals(4, merged.getOrDefault("Rock_Stone_Cobble", 0));
    }

    @Test
    void rawProjectionIsDisplayOnlyBoundary() {
        installHardwoodItems();
        CraftingRecipe recipe = hardwoodGenericRecipe(2);

        List<RawMaterialRequirement> projection = RecipeTreeResolver.resolveRecipeToRaw(recipe, false);
        assertEquals(1, projection.size());
        assertEquals("Wood_Hardwood_Planks", projection.get(0).itemId());

        CombinedItemContainer container = containerWithStacks(List.of(
                stack("Wood_Hardwood_Decorative", 2, false)
        ));

        AutoCraftPlan plan = AutoCraftPlanner.plan(recipe, false, container);
        assertTrue(plan.affordable());
        assertFalse(plan.requiresAutoCraft());

        Map<String, Integer> merged = toMap(plan.consumptions());
        assertEquals(2, merged.getOrDefault("Wood_Hardwood_Decorative", 0));
        assertNotEquals("Wood_Hardwood_Planks", plan.consumptions().get(0).itemId());
    }

    private static void installHardwoodItems() {
        Map<String, Item> items = new LinkedHashMap<>();
        items.put("Wood_Hardwood_Planks", item(
                "Wood_Hardwood_Planks",
                "Wood_Hardwood_Planks",
                true,
                100,
                "Wood_Hardwood_Planks",
                resourceType("Hardwood")));
        items.put("Wood_Hardwood_Decorative", item(
                "Wood_Hardwood_Decorative",
                "Wood_Hardwood_Decorative",
                true,
                100,
                "Wood_Hardwood_Planks",
                resourceType("Hardwood")));
        items.put("Rock_Stone_Cobble", item(
                "Rock_Stone_Cobble",
                "Rock_Stone_Cobble",
                true,
                100));

        installItems(items);
        setNaturalRegistry(Set.of(), Set.of());
        ResourceTypeResolver.initialize();
    }

    private static CraftingRecipe hardwoodGenericRecipe(int quantity) {
        return recipe(
                "Recipe_Generic_Hardwood",
                new MaterialQuantity[]{materialQtyResource("Hardwood", quantity)},
                materialQty("Output_Block", 1),
                BenchType.Crafting,
                "Workbench");
    }

    private static Map<String, Integer> toMap(List<ConsumptionEntry> consumptions) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (ConsumptionEntry consumption : consumptions) {
            merged.merge(consumption.itemId(), consumption.quantity(), Integer::sum);
        }
        return merged;
    }

    private record StackSpec(String itemId, int quantity, boolean stencil) {}

    private static StackSpec stack(String itemId, int quantity, boolean stencil) {
        return new StackSpec(itemId, quantity, stencil);
    }

    @SuppressWarnings("unchecked")
    private static CombinedItemContainer containerWithStacks(List<StackSpec> stacks) {
        CombinedItemContainer container = mock(CombinedItemContainer.class);
        Map<StackSpec, ItemStack> stackObjects = new LinkedHashMap<>();

        for (StackSpec spec : stacks) {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getItemId()).thenReturn(spec.itemId());
            if (spec.stencil()) {
                BsonDocument metadata = new BsonDocument();
                metadata.put("StencilStencil", new BsonString("true"));
                when(stack.getMetadata()).thenReturn(metadata);
            } else {
                when(stack.getMetadata()).thenReturn(null);
            }
            stackObjects.put(spec, stack);
        }

        when(container.countItemStacks(any())).thenAnswer(invocation -> {
            Predicate<ItemStack> predicate = invocation.getArgument(0);
            int total = 0;
            for (Map.Entry<StackSpec, ItemStack> entry : stackObjects.entrySet()) {
                if (predicate.test(entry.getValue())) {
                    total += entry.getKey().quantity();
                }
            }
            return total;
        });

        return container;
    }

    private static void setRawCostCache(Map<String, List<RawMaterialRequirement>> rawCostCache) {
        try {
            Field field = RecipeTreeResolver.class.getDeclaredField("rawCostCache");
            field.setAccessible(true);
            field.set(null, rawCostCache);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set RecipeTreeResolver raw cost cache for test", e);
        }
    }

    private static void resetRawCostCache() {
        setRawCostCache(Map.of());
    }
}
