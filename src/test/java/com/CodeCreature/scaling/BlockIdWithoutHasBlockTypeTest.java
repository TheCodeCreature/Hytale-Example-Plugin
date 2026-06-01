package com.CodeCreature.scaling;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.registry.BenchRegistry;
import com.CodeCreature.scaling.DropScaler;
import com.CodeCreature.scaling.NaturalResourceRegistry;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.CodeCreature.scaling.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for blocks like rails and doors whose items have a
 * {@code blockId} set but {@code hasBlockType=false}. These items reference
 * an external block definition rather than embedding one inline, which
 * previously caused them to slip through both registries and receive 12x
 * natural drops instead of 1x self-drops.
 *
 * The root cause: both NaturalResourceRegistry and BlockRecipeRegistry
 * relied on {@code item.hasBlockType()} to detect block-placing items.
 * Items with {@code hasBlockType=false} but a valid {@code blockId} were
 * misclassified as non-block items, making their blocks appear "natural".
 */
class BlockIdWithoutHasBlockTypeTest {

    // ── Blocks with hasBlockType=false but blockId set (the bug scenario) ──
    private BlockBreakingDropType railBreaking;
    private BlockType railBlock;
    private Item railItem;            // hasBlockType=false, blockId="Rail_Iron"
    private CraftingRecipe railRecipe;

    private BlockBreakingDropType doorBreaking;
    private BlockType doorBlock;
    private Item doorItem;            // hasBlockType=false, blockId="Door_Wood"
    private CraftingRecipe doorRecipe;

    // ── Supporting natural items ──
    private BlockBreakingDropType logBreaking;
    private BlockType logBlock;
    private Item logItem;

    private Item ironIngotItem;
    private Item planksItem;

    // ── Asset maps ──
    private Map<String, BlockType> blockTypes;
    private Map<String, Item> items;
    private Map<String, CraftingRecipe> recipes;

    @BeforeEach
    void setUp() {
        blockTypes = new HashMap<>();
        items = new HashMap<>();
        recipes = new HashMap<>();

        // Natural block: Wood_Log (hasBlockType=true, for base block reference)
        logBreaking = new BlockBreakingDropType("Woods", 0, 1, "Wood_Log", null);
        logBlock = blockType("Wood_Log", gathering(logBreaking, null, null, null));
        logItem = item("Wood_Log", "Wood_Log", true, 100);

        // Supporting items (not blocks)
        ironIngotItem = item("Metal_Ingot_Iron", null, false, 100);
        planksItem = item("Wood_Planks", "Wood_Planks", true, 100);

        // ── Rail: hasBlockType=false, blockId="Rail_Iron" ──
        railBreaking = new BlockBreakingDropType("Metals", 1, 1, "Rail_Iron", null);
        railBlock = blockType("Rail_Iron", gathering(railBreaking, null, null, null));
        railItem = item("Rail_Iron", "Rail_Iron", false, 100);  // KEY: hasBlockType=false
        railRecipe = recipe("Rail_Iron",
                new MaterialQuantity[]{materialQty("Metal_Ingot_Iron", 2)},
                materialQty("Rail_Iron", 1),
                BenchType.StructuralCrafting, "Builders");

        // ── Door: hasBlockType=false, blockId="Door_Wood" ──
        doorBreaking = new BlockBreakingDropType("Woods", 0, 1, "Door_Wood", null);
        doorBlock = blockType("Door_Wood", gathering(doorBreaking, null, null, null));
        doorItem = item("Door_Wood", "Door_Wood", false, 100);  // KEY: hasBlockType=false
        doorRecipe = recipe("Door_Wood",
                new MaterialQuantity[]{materialQty("Wood_Planks", 2)},
                materialQty("Door_Wood", 1),
                BenchType.StructuralCrafting, "Builders");

        // Populate asset maps
        blockTypes.put("Wood_Log", logBlock);
        blockTypes.put("Rail_Iron", railBlock);
        blockTypes.put("Door_Wood", doorBlock);

        items.put("Wood_Log", logItem);
        items.put("Metal_Ingot_Iron", ironIngotItem);
        items.put("Wood_Planks", planksItem);
        items.put("Rail_Iron", railItem);
        items.put("Door_Wood", doorItem);

        recipes.put("Rail_Iron", railRecipe);
        recipes.put("Door_Wood", doorRecipe);

        installBlockTypes(blockTypes);
        installItems(items);
        installRecipes(recipes);
        installDropLists(new HashMap<>());
        BenchRegistry.init();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    // ─────────────────────────────────────────────────────────────
    //  NaturalResourceRegistry tests
    // ─────────────────────────────────────────────────────────────

    @Nested
    class NaturalRegistryDetection {

        @Test
        void railWithBlockIdExcludedFromNaturalRegistry() {
            NaturalResourceRegistry.init();

            assertFalse(NaturalResourceRegistry.isNaturalBlock("Rail_Iron"),
                    "Rail_Iron should NOT be classified as natural "
                            + "(it has a recipe, even though hasBlockType=false)");
        }

        @Test
        void doorWithBlockIdExcludedFromNaturalRegistry() {
            NaturalResourceRegistry.init();

            assertFalse(NaturalResourceRegistry.isNaturalBlock("Door_Wood"),
                    "Door_Wood should NOT be classified as natural "
                            + "(it has a recipe, even though hasBlockType=false)");
        }

        @Test
        void logStillClassifiedAsNatural() {
            NaturalResourceRegistry.init();

            assertTrue(NaturalResourceRegistry.isNaturalBlock("Wood_Log"),
                    "Wood_Log (no recipe) should still be classified as natural");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  BlockRecipeRegistry tests
    // ─────────────────────────────────────────────────────────────

    @Nested
    class RecipeRegistryDetection {

        @Test
        void railWithBlockIdDetectedByRecipeRegistry() {
            NaturalResourceRegistry.init();
            RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
            BenchRecipeRegistries.init();

            assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Rail_Iron"),
                    "Rail_Iron should be in BenchRecipeRegistries "
                            + "(even though hasBlockType=false, blockId is set)");
        }

        @Test
        void doorWithBlockIdDetectedByRecipeRegistry() {
            NaturalResourceRegistry.init();
            RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
            BenchRecipeRegistries.init();

            assertTrue(BenchRecipeRegistries.hasRecipeAnywhere("Door_Wood"),
                    "Door_Wood should be in BenchRecipeRegistries "
                            + "(even though hasBlockType=false, blockId is set)");
        }

        @Test
        void recipeForRailResolvesCorrectly() {
            NaturalResourceRegistry.init();
            RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
            BenchRecipeRegistries.init();

            CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock("Rail_Iron");
            assertNotNull(recipe);
            assertEquals("Rail_Iron", recipe.getId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Drop scaling — the 12x bug guard
    // ─────────────────────────────────────────────────────────────

    @Nested
    class DropScalerGuard {

        @Test
        void railDoesNotGet12xDrop() {
            DropScaler.apply();

            assertEquals(1, readBreakingQuantity(railBreaking),
                    "Rail_Iron should keep 1x drop quantity — NOT 12x");
        }

        @Test
        void doorDoesNotGet12xDrop() {
            DropScaler.apply();

            assertEquals(1, readBreakingQuantity(doorBreaking),
                    "Door_Wood should keep 1x drop quantity — NOT 12x");
        }

        @Test
        void logStillGets12xDrop() {
            DropScaler.apply();

            assertEquals(12,
                    readBreakingQuantity(readGatheringBreaking(logBlock.getGathering())),
                    "Wood_Log (natural block) should still get 12x");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Full pipeline test
    // ─────────────────────────────────────────────────────────────

    @Nested
    class FullPipeline {

        @Test
        void railDropsIngredientsNotSelf() {
            DropScaler.apply();

            var breaking = readGatheringBreaking(railBlock.getGathering());
            assertEquals("Metal_Ingot_Iron", breaking.getItemId(),
                    "Rail should drop its ingredient (iron ingot), not itself");
            // 2 ingots * 12 / 1 output = 24
            assertEquals(24, breaking.getQuantity());
        }

        @Test
        void doorDropsIngredientsNotSelf() {
            DropScaler.apply();

            var breaking = readGatheringBreaking(doorBlock.getGathering());
            assertEquals("Wood_Planks", breaking.getItemId(),
                    "Door should drop its ingredient (planks), not itself");
            // 2 planks * 12 / 1 output = 24
            assertEquals(24, breaking.getQuantity());
        }

        @Test
        void railNeverDrops12xOfItself() {
            DropScaler.apply();

            var breaking = readGatheringBreaking(railBlock.getGathering());
            assertFalse(
                    "Rail_Iron".equals(breaking.getItemId()) && breaking.getQuantity() > 1,
                    "Rail should NEVER drop multiple copies of itself");
        }

        @Test
        void doorNeverDrops12xOfItself() {
            DropScaler.apply();

            var breaking = readGatheringBreaking(doorBlock.getGathering());
            assertFalse(
                    "Door_Wood".equals(breaking.getItemId()) && breaking.getQuantity() > 1,
                    "Door should NEVER drop multiple copies of itself");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Edge case: item with null blockId and hasBlockType=false
    // ─────────────────────────────────────────────────────────────

    @Nested
    class NullBlockIdEdgeCases {

        @Test
        void itemWithNullBlockIdAndNoHasBlockTypeSkippedByRegistries() {
            // A non-block item (e.g., a tool) should not cause issues
            CraftingRecipe toolRecipe = recipe("Tool_Recipe",
                    new MaterialQuantity[]{materialQty("Metal_Ingot_Iron", 3)},
                    materialQty("Metal_Ingot_Iron", 1),  // output is non-block item
                    BenchType.StructuralCrafting, "Builders");
            recipes.put("Tool_Recipe", toolRecipe);
            installRecipes(recipes);

            NaturalResourceRegistry.init();
            RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
            BenchRecipeRegistries.init();

            assertFalse(BenchRecipeRegistries.hasRecipeAnywhere("Metal_Ingot_Iron"),
                    "Non-block item should not create a block recipe entry");
        }

        @Test
        void itemWithEmptyBlockIdTreatedAsNonBlock() {
            Item emptyBlockIdItem = item("Empty_BlockId", "", false, 100);
            items.put("Empty_BlockId", emptyBlockIdItem);

            CraftingRecipe emptyRecipe = recipe("Empty_BlockId_Recipe",
                    new MaterialQuantity[]{materialQty("Wood_Log", 1)},
                    materialQty("Empty_BlockId", 1),
                    BenchType.StructuralCrafting, "Builders");
            recipes.put("Empty_BlockId_Recipe", emptyRecipe);
            installItems(items);
            installRecipes(recipes);

            NaturalResourceRegistry.init();
            RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES);
            BenchRecipeRegistries.init();

            assertFalse(BenchRecipeRegistries.hasRecipeAnywhere(""),
                    "Item with empty blockId should not create block recipe entry");
        }
    }
}
