package com.CodeCreature.crafting;

/**
 * @node    CraftingAffordabilityFacadeTest
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Verifies generic-aware direct affordability checks sum concrete variants and expose
 *          presentation metadata for rendering generic icons with concrete fallback behavior.
 * @wave    4 (generic icons + generic affordability boundary)
 * @status  Wave 4 - implemented focused facade coverage for generic affordability and presentation
 * @do-not  Assert engine removeMaterials consumption ordering in this test suite.
 *          Expand this file into planner raw-cost integration behavior.
 */

import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
import static com.CodeCreature.scaling.AssetTestHelper.setNaturalRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CraftingAffordabilityFacadeTest {

    @AfterEach
    void tearDown() {
        cleanup();
    }

    /** @intent Verify generic affordability counts inventory across all matching variants instead of one representative item.
     *  @wave   4 - implemented generic affordability variant-summing coverage
     *  @status implemented
     *  @node   CraftingAffordabilityFacadeTest#isAffordableSumsGenericVariants
     */
    @Test
    void isAffordableSumsGenericVariants() {
        installHardwoodItems();
        CraftingRecipe recipe = hardwoodGenericRecipe(3);

        CombinedItemContainer affordableContainer = containerWithQuantities(Map.of(
                "Wood_Hardwood_Planks", 1,
                "Wood_Hardwood_Decorative", 2
        ));
        CombinedItemContainer insufficientContainer = containerWithQuantities(Map.of(
                "Wood_Hardwood_Planks", 1,
                "Wood_Hardwood_Decorative", 1
        ));

        assertTrue(CraftingAffordabilityFacade.isAffordable(recipe, false, affordableContainer));
        assertFalse(CraftingAffordabilityFacade.isAffordable(recipe, false, insufficientContainer));
    }

    /** @intent Verify direct ingredient view marks generic inputs and exposes generic icon metadata while retaining concrete fallback icon item id.
     *  @wave   4 - implemented generic presentation metadata coverage
     *  @status implemented
     *  @node   CraftingAffordabilityFacadeTest#resolveDirectIngredientsMarksGenericPresentation
     */
    @Test
    void resolveDirectIngredientsMarksGenericPresentation() {
        installHardwoodItems();
        CraftingRecipe recipe = hardwoodGenericRecipe(3);

        CombinedItemContainer container = containerWithQuantities(Map.of(
                "Wood_Hardwood_Planks", 1,
                "Wood_Hardwood_Decorative", 2
        ));

        List<CraftingAffordabilityFacade.DirectIngredientView> views =
                CraftingAffordabilityFacade.resolveDirectIngredients(recipe, false, container);

        assertEquals(1, views.size());
        CraftingAffordabilityFacade.DirectIngredientView view = views.get(0);
        assertEquals("Hardwood", view.identity().resourceTypeId());
        assertEquals(3, view.requiredQty());
        assertEquals(3, view.playerHas());
        assertTrue(view.sufficient());

        assertTrue(view.presentation().genericMode());
        assertEquals("Hardwood", view.presentation().resourceTypeId());
        assertEquals("Common/Icons/ResourceTypes/Hardwood.png", view.presentation().genericIconPath());
        assertNotNull(view.presentation().iconItemId());
    }

    /** @intent Verify concrete authored inputs keep concrete icon fallback metadata without entering generic presentation mode.
     *  @wave   4 - implemented concrete fallback presentation coverage
     *  @status implemented
     *  @node   CraftingAffordabilityFacadeTest#resolveDirectIngredientsKeepsConcretePresentationForDirectItem
     */
    @Test
    void resolveDirectIngredientsKeepsConcretePresentationForDirectItem() {
        installHardwoodItems();
        CraftingRecipe recipe = recipe(
                "Recipe_Direct_Planks",
                new MaterialQuantity[]{materialQty("Wood_Hardwood_Planks", 2)},
                materialQty("Output_Block", 1),
                BenchType.Crafting,
                "Workbench");

        CombinedItemContainer container = containerWithQuantities(Map.of("Wood_Hardwood_Planks", 2));
        List<CraftingAffordabilityFacade.DirectIngredientView> views =
                CraftingAffordabilityFacade.resolveDirectIngredients(recipe, false, container);

        assertEquals(1, views.size());
        CraftingAffordabilityFacade.DirectIngredientView view = views.get(0);
        assertFalse(view.presentation().genericMode());
        assertNull(view.presentation().resourceTypeId());
        assertNull(view.presentation().genericIconPath());
        assertEquals("Wood_Hardwood_Planks", view.presentation().iconItemId());
    }

    private static void installHardwoodItems() {
        Map<String, Item> items = new LinkedHashMap<>();
        items.put("Wood_Hardwood_Decorative", item(
                "Wood_Hardwood_Decorative",
                "Wood_Hardwood_Decorative",
                true,
                100,
                "Wood_Hardwood_Planks",
                resourceType("Hardwood")));
        items.put("Wood_Hardwood_Planks", item(
                "Wood_Hardwood_Planks",
                "Wood_Hardwood_Planks",
                true,
                100,
                "Wood_Hardwood_Planks",
                resourceType("Hardwood")));

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

    @SuppressWarnings("unchecked")
    private static CombinedItemContainer containerWithQuantities(Map<String, Integer> quantitiesByItemId) {
        CombinedItemContainer container = mock(CombinedItemContainer.class);
        Map<String, ItemStack> stackByItemId = new LinkedHashMap<>();
        for (String itemId : quantitiesByItemId.keySet()) {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getItemId()).thenReturn(itemId);
            stackByItemId.put(itemId, stack);
        }

        when(container.countItemStacks(any())).thenAnswer(invocation -> {
            Predicate<ItemStack> predicate = invocation.getArgument(0);
            int total = 0;
            for (Map.Entry<String, Integer> entry : quantitiesByItemId.entrySet()) {
                ItemStack stack = stackByItemId.get(entry.getKey());
                if (stack != null && predicate.test(stack)) {
                    total += entry.getValue();
                }
            }
            return total;
        });

        return container;
    }
}
