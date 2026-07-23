package com.CodeCreature.crafting;

/**
 * @node    AutoCraftPlannerParityTest
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Covers planner parity-critical behavior across generic deficits, stencil exclusion,
 *          and raw projection boundaries while keeping tests scoped to deterministic contracts.
 * @wave    5 (surface parity + regression coverage)
 * @status  Wave 5 - implemented focused planner parity regression tests
 * @do-not  Treat these tests as runtime proof of engine removeMaterials ordering.
 *          Expand this suite into full craftprobe runtime matrix coverage.
 */

import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.CodeCreature.scaling.AssetTestHelper.cleanup;
import static com.CodeCreature.scaling.AssetTestHelper.installItems;
import static com.CodeCreature.scaling.AssetTestHelper.item;
import static com.CodeCreature.scaling.AssetTestHelper.materialQty;
import static com.CodeCreature.scaling.AssetTestHelper.materialQtyResource;
import static com.CodeCreature.scaling.AssetTestHelper.recipe;
import static com.CodeCreature.scaling.AssetTestHelper.resourceType;
import static com.CodeCreature.scaling.AssetTestHelper.setCraftedItemIds;
import static com.CodeCreature.scaling.AssetTestHelper.setNaturalRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoCraftPlannerParityTest {

    @AfterEach
    void tearDown() {
        cleanup();
        resetRawCostCache();
    }

    /** @intent Direct affordability and planner verification should agree for generic variant sums when no auto-craft is required.
     *  @wave   5 - implemented direct/planner consistency coverage
     *  @status implemented
     *  @node   AutoCraftPlannerParityTest#plannerAndFacadeAgreeForDirectGenericAffordability
     */
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

    /** @intent Stencil-tagged stacks must be excluded identically from direct affordability and planner verification.
     *  @wave   5 - implemented stencil exclusion parity coverage
     *  @status implemented
     *  @node   AutoCraftPlannerParityTest#plannerAndFacadeExcludeStencilTaggedStacks
     */
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

    /** @intent Deficit expansion should keep deterministic behavior while preferring policy-A concrete morph selection before compatibility fallback.
     *  @wave   5 - implemented planner deficit expansion coverage
     *  @status implemented
     *  @node   AutoCraftPlannerParityTest#plannerDeficitExpansionUsesPolicyAMorphFirst
     */
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

    /** @intent Raw projection remains display-only; planner consumptions must still follow variant availability instead of representative projection identity.
     *  @wave   5 - implemented raw-projection boundary coverage
     *  @status implemented
     *  @node   AutoCraftPlannerParityTest#rawProjectionIsDisplayOnlyBoundary
     */
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
