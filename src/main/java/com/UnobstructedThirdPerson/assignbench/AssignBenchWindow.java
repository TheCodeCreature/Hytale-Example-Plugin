package com.UnobstructedThirdPerson.assignbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.ItemQuantity;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.SelectSlotAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockConstants;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockMetadata;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockQualitySwapper;
import com.UnobstructedThirdPerson.portablebench.PortableBenchConfig;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A StructuralCrafting window for the physical Assignment Bench block.
 * The player inserts a PlaceBlock item into the input slot, browses
 * recipes from both Builders and Furniture benches, and "assigns" a
 * recipe to the PlaceBlock — writing the recipe ID to its BSON metadata
 * and swapping it to the correct quality variant.
 *
 * <p>Implements {@link ItemContainerWindow} and {@link MaterialContainerWindow}
 * to satisfy the StructuralCrafting packet requirements (same pattern as
 * {@link com.UnobstructedThirdPerson.portablebench.PortableStructuralWindow}).</p>
 *
 * <h3>Key differences from standard crafting windows</h3>
 * <ul>
 *   <li>Input slot only accepts PlaceBlock items (validated by slot filter)</li>
 *   <li>"Craft" action is repurposed as "Assign" — no materials consumed, no output produced</li>
 *   <li>Recipe categories serve as a browsable guide; all recipes are visible regardless
 *       of the player's current inventory</li>
 *   <li>On assignment, the PlaceBlock's metadata is updated and its quality variant is swapped</li>
 * </ul>
 */
public class AssignBenchWindow extends Window
        implements ItemContainerWindow, MaterialContainerWindow {

    private static final Logger LOGGER = Logger.getLogger(AssignBenchWindow.class.getSimpleName());

    @Nonnull
    private final PortableBenchConfig config;
    @Nonnull
    private final JsonObject windowData = new JsonObject();
    @Nonnull
    private final SimpleItemContainer inputContainer;
    @Nonnull
    private final SimpleItemContainer optionsContainer;
    @Nonnull
    private final CombinedItemContainer combinedItemContainer;
    @Nonnull
    private final MaterialExtraResourcesSection extraResourcesSection;
    @Nonnull
    private final Short2ObjectOpenHashMap<String> optionSlotToRecipeMap = new Short2ObjectOpenHashMap<>();

    @Nullable
    private Player player;
    private int selectedSlot = 0;

    /**
     * Constructs an Assignment Bench window.
     *
     * @param config bench config defining recipe categories (should aggregate
     *               both Builders and Furniture bench categories)
     */
    public AssignBenchWindow(@Nonnull PortableBenchConfig config) {
        super(WindowType.StructuralCrafting);
        this.config = config;

        // 1 input slot — player places their PlaceBlock here
        this.inputContainer = new SimpleItemContainer((short) 1);
        this.inputContainer.registerChangeEvent(e -> this.updateRecipes());
        this.inputContainer.setSlotFilter(FilterActionType.ADD, (short) 0, this::isPlaceBlockItem);

        // 64 output option slots — shows recipe outputs for browsing
        this.optionsContainer = new SimpleItemContainer((short) 64);
        this.optionsContainer.setGlobalFilter(FilterType.DENY_ALL);

        // Combined container for the protocol (65 slots total)
        this.combinedItemContainer = new CombinedItemContainer(this.inputContainer, this.optionsContainer);

        // Empty extra resources (no nearby chests)
        this.extraResourcesSection = new MaterialExtraResourcesSection();
        this.extraResourcesSection.setExtraMaterials(new ItemQuantity[0]);
        this.extraResourcesSection.setValid(true);

        // Build window data JSON
        this.windowData.addProperty("type", BenchType.StructuralCrafting.ordinal());
        this.windowData.addProperty("id", config.benchId());
        this.windowData.addProperty("name", config.benchName());
        this.windowData.addProperty("blockItemId", "hytale:Bench_Builders");
        this.windowData.addProperty("tierLevel", 1);
        this.windowData.addProperty("nearbyChestCount", 0);
        this.windowData.addProperty("maxChestCount", 0);
        this.windowData.addProperty("chestHorizontalRadius", 0);
        this.windowData.addProperty("chestVerticalRadius", 0);
        this.windowData.addProperty("selected", 0);
        this.windowData.addProperty("allowBlockGroupCycling", true);
        this.windowData.addProperty("alwaysShowInventoryHints", false);
        this.windowData.add("inventoryHints", new JsonArray());
        this.windowData.add("optionSlotRecipes", new JsonArray());
        this.windowData.add("memoriesPerLevel", new JsonArray());
        buildCategories();

        LOGGER.info("[AssignBench] Window created — benchId=" + config.benchId());
    }

    /**
     * Builds recipe category tabs as a guide for browsing.
     * All recipes are shown regardless of player inventory.
     */
    private void buildCategories() {
        // TODO: Build categories array from config, same as PortableStructuralWindow.
        // Each category shows all available recipes for browsing.
        // Include a "Clear Recipe" tab that signals recipe clearing on assignment.
        JsonArray categories = new JsonArray();

        for (PortableBenchConfig.CategoryDef categoryDef : config.categories()) {
            JsonObject category = new JsonObject();
            category.addProperty("id", categoryDef.id());
            category.addProperty("name", categoryDef.name());
            category.addProperty("icon", categoryDef.icon());

            JsonArray craftableRecipes = new JsonArray();
            for (String recipeCategoryId : categoryDef.recipeCategoryIds()) {
                Set<String> recipeIds = CraftingPlugin.getAvailableRecipesForCategory(
                        config.benchId(), recipeCategoryId);
                if (recipeIds != null) {
                    for (String recipeId : recipeIds) {
                        craftableRecipes.add(recipeId);
                    }
                }
            }
            category.add("craftableRecipes", craftableRecipes);
            categories.add(category);
        }
        this.windowData.add("categories", categories);
    }

    /**
     * Slot filter: only allows PlaceBlock items in the input slot.
     */
    private boolean isPlaceBlockItem(FilterActionType type, ItemContainer container,
                                      short slot, ItemStack stack) {
        if (type != FilterActionType.ADD) return true;
        if (stack == null) return true;
        return PlaceBlockMetadata.isPlaceBlock(stack);
    }

    /**
     * Called when the input container changes (PlaceBlock inserted/removed).
     * Updates the options container to show available recipes.
     */
    private void updateRecipes() {
        // TODO: Implement recipe population logic:
        //
        // 1. Clear options container and recipe map:
        //    this.optionsContainer.clear();
        //    this.optionSlotToRecipeMap.clear();
        //
        // 2. Check if a PlaceBlock is in the input slot:
        //    ItemStack inputStack = this.inputContainer.getItemStack((short) 0);
        //    if (inputStack == null || !PlaceBlockMetadata.isPlaceBlock(inputStack)) {
        //        // No PlaceBlock — clear options and return
        //        this.windowData.add("optionSlotRecipes", new JsonArray());
        //        this.setNeedRebuild(); this.invalidate(); return;
        //    }
        //
        // 3. Populate options with all recipes from configured categories:
        //    Iterate config.categories() → recipeCategoryIds → CraftingPlugin
        //    For each recipe, add its output to optionsContainer and map slot → recipeId
        //
        // 4. Rebuild and invalidate:
        //    this.setNeedRebuild();
        //    this.invalidate();
    }

    /**
     * Assigns the selected recipe to the PlaceBlock in the input slot.
     * Does NOT consume materials or produce output — only writes metadata
     * and swaps the quality variant.
     *
     * @param recipeId the CraftingRecipe asset ID to assign
     */
    private void assignRecipe(@Nonnull String recipeId) {
        // TODO: Implement assignment logic:
        //
        // 1. Get PlaceBlock from input slot:
        //    ItemStack placeBlock = this.inputContainer.getItemStack((short) 0);
        //    if (placeBlock == null || !PlaceBlockMetadata.isPlaceBlock(placeBlock)) return;
        //
        // 2. Resolve recipe for display name:
        //    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        //    if (recipe == null) return;
        //    String recipeName = recipe.getPrimaryOutput().getItemId();
        //
        // 3. Write recipe metadata:
        //    placeBlock = PlaceBlockMetadata.setRecipeId(placeBlock, recipeId);
        //    placeBlock = PlaceBlockMetadata.setRecipeName(placeBlock, recipeName);
        //
        // 4. Evaluate resources and determine variant:
        //    ItemContainer inventory = player.getInventory().getCombinedBackpackStorageHotbar();
        //    List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe, 1);
        //    if (inventory.canRemoveMaterials(inputs)) {
        //        placeBlock = PlaceBlockQualitySwapper.swapToArmed(placeBlock);
        //    } else {
        //        placeBlock = PlaceBlockQualitySwapper.swapToNoResources(placeBlock);
        //    }
        //
        // 5. Replace PlaceBlock in input slot:
        //    this.inputContainer.setItemStackForSlot((short) 0, placeBlock, false);
        //
        // 6. Invalidate window:
        //    this.setNeedRebuild();
        //    this.invalidate();
    }

    @Nonnull
    @Override
    public ItemContainer getItemContainer() {
        return this.combinedItemContainer;
    }

    @Nonnull
    @Override
    public MaterialExtraResourcesSection getExtraResourcesSection() {
        return this.extraResourcesSection;
    }

    @Override
    public void invalidateExtraResources() {
        // No-op — no nearby chests for assignment bench
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Nonnull
    @Override
    public JsonObject getData() {
        return this.windowData;
    }

    @Override
    public boolean onOpen0(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // TODO: Implement:
        // 1. Set worldMemoriesLevel
        // 2. Store player reference
        //    this.player = store.getComponent(ref, Player.getComponentType());
        // 3. Invalidate
        this.invalidate();
        return true;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref,
                         @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        // TODO: Return the PlaceBlock from input slot to player's inventory
        //
        // List<ItemStack> items = this.inputContainer.dropAllItemStacks();
        // if (!items.isEmpty()) {
        //     Player player = componentAccessor.getComponent(ref, Player.getComponentType());
        //     if (player != null) {
        //         SimpleItemContainer.addOrDropItemStacks(
        //             componentAccessor, ref,
        //             player.getInventory().getCombinedHotbarFirst(),
        //             items
        //         );
        //     }
        // }
        this.player = null;
    }

    /**
     * Handles window actions. Repurposes {@link CraftRecipeAction} as an
     * "Assign" action — writes the recipe to the PlaceBlock instead of crafting.
     */
    @Override
    public void handleAction(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull WindowAction action) {
        // TODO: Implement action handling:
        //
        // Handle slot selection:
        // if (action instanceof SelectSlotAction selectAction) {
        //     this.selectedSlot = selectAction.slot;
        //     this.windowData.addProperty("selected", this.selectedSlot);
        //     this.invalidate();
        //     return;
        // }
        //
        // Handle "Assign" (repurposed CraftRecipeAction):
        // if (action instanceof CraftRecipeAction craftAction) {
        //     String recipeId = this.optionSlotToRecipeMap.get((short) this.selectedSlot);
        //     if (recipeId == null) return;
        //     assignRecipe(recipeId);
        //     return;
        // }
    }
}
