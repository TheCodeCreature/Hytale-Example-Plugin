package com.UnobstructedThirdPerson.placeblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.UnobstructedThirdPerson.portablebench.PortableBenchConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A PocketCrafting window that allows the player to browse recipes and
 * <strong>assign</strong> them to the held PlaceBlock item — without
 * consuming any materials or producing any output.
 *
 * <p>Opened when the player presses F while holding a PlaceBlock item
 * (via {@link PlaceBlockMenuInteraction}). Shows the same recipe
 * categories as the standard Pocket Bench but repurposes the
 * {@link CraftRecipeAction} as an "assign" action.</p>
 *
 * <h3>Differences from {@link com.UnobstructedThirdPerson.portablebench.PortableBenchWindow}</h3>
 * <ul>
 *   <li>Clicking a recipe assigns it to the PlaceBlock instead of crafting</li>
 *   <li>No materials are consumed on assignment</li>
 *   <li>A "Clear Recipe" tab allows removing the current assignment</li>
 *   <li>The PlaceBlock's quality variant is updated after assignment</li>
 * </ul>
 *
 * <p>Uses {@link WindowType#PocketCrafting} — same flat recipe list UI.</p>
 */
public class PlaceBlockSelectorWindow extends Window {

    private static final Logger LOGGER = Logger.getLogger(PlaceBlockSelectorWindow.class.getSimpleName());
    private static final String CLEAR_RECIPE_CATEGORY_ID = "__clear_recipe__";

    @Nonnull
    private final PortableBenchConfig config;
    @Nonnull
    private final JsonObject windowData = new JsonObject();
    @Nullable
    private Player player;

    /**
     * The active hotbar slot where the PlaceBlock is held.
     * Used to replace the item after assignment.
     */
    private byte placeBlockSlot;

    /**
     * Constructs a selector window for assigning recipes to a PlaceBlock.
     *
     * @param config    bench config defining available recipe categories
     * @param heldItem  the PlaceBlock item currently held by the player
     */
    public PlaceBlockSelectorWindow(@Nonnull PortableBenchConfig config,
                                     @Nonnull ItemStack heldItem) {
        super(WindowType.PocketCrafting);
        this.config = config;

        this.windowData.addProperty("type", BenchType.Crafting.ordinal());
        this.windowData.addProperty("id", config.benchId());
        this.windowData.addProperty("name", config.benchName());

        buildCategories();

        LOGGER.info("[PlaceBlockSelector] Window created — benchId=" + config.benchId()
                + ", currentRecipe=" + PlaceBlockMetadata.getRecipeId(heldItem));
    }

    /**
     * Builds the recipe categories array in the window data.
     * Includes a "Clear Recipe" tab as the first entry and all
     * configured recipe categories as browsable/assignable tabs.
     */
    private void buildCategories() {
        JsonArray categories = new JsonArray();

        // "Clear Recipe" tab — selecting any entry here clears the assignment
        JsonObject clearCategory = new JsonObject();
        clearCategory.addProperty("id", CLEAR_RECIPE_CATEGORY_ID);
        clearCategory.addProperty("name", "\u2716 Clear Recipe");
        clearCategory.addProperty("icon", "");
        clearCategory.add("craftableRecipes", new JsonArray());
        categories.add(clearCategory);

        // Build recipe categories from config
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

    @Nonnull
    @Override
    public JsonObject getData() {
        return this.windowData;
    }

    @Override
    public boolean onOpen0(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // TODO: Implement:
        // 1. Set worldMemoriesLevel (same as PortableBenchWindow)
        // 2. Resolve Player component and store reference
        // 3. Record the active hotbar slot (placeBlockSlot)
        //    this.player = store.getComponent(ref, Player.getComponentType());
        //    this.placeBlockSlot = player.getInventory().getActiveHotbarSlot();
        // 4. Invalidate to send initial data
        this.invalidate();
        return true;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref,
                         @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        this.player = null;
    }

    /**
     * Handles recipe selection actions. Instead of crafting, this method
     * assigns the selected recipe to the PlaceBlock item.
     *
     * <p>When a {@link CraftRecipeAction} is received:</p>
     * <ul>
     *   <li>If the active category is "Clear Recipe" → clear the PlaceBlock's recipe
     *       and swap to Default variant</li>
     *   <li>Otherwise → write the recipe ID and name to the PlaceBlock's metadata
     *       and swap to the appropriate quality variant</li>
     * </ul>
     */
    @Override
    public void handleAction(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull WindowAction action) {
        // TODO: Implement the following logic:
        //
        // 1. Only handle CraftRecipeAction:
        //    if (!(action instanceof CraftRecipeAction craftAction)) return;
        //    String recipeId = craftAction.recipeId;
        //
        // 2. Get current PlaceBlock from hotbar:
        //    ItemStack placeBlock = player.getInventory().getHotbar()
        //        .getItemStack(placeBlockSlot);
        //    if (!PlaceBlockMetadata.isPlaceBlock(placeBlock)) return;
        //
        // 3. If recipeId is null or empty, treat as "Clear Recipe":
        //    placeBlock = PlaceBlockMetadata.clearRecipe(placeBlock);
        //    placeBlock = PlaceBlockQualitySwapper.swapToDefault(placeBlock);
        //    player.getInventory().getHotbar()
        //        .setItemStackForSlot(placeBlockSlot, placeBlock, false);
        //    return;
        //
        // 4. Resolve recipe and get display name:
        //    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        //    if (recipe == null) return;
        //    String recipeName = recipe.getPrimaryOutput().getItemId(); // or localization
        //
        // 5. Write recipe to PlaceBlock metadata:
        //    placeBlock = PlaceBlockMetadata.setRecipeId(placeBlock, recipeId);
        //    placeBlock = PlaceBlockMetadata.setRecipeName(placeBlock, recipeName);
        //
        // 6. Evaluate resources and swap quality variant:
        //    PlaceBlockQualitySwapper.evaluateAndSwap(player, placeBlockSlot, placeBlock);
        //
        // 7. Close window or keep open for further selections:
        //    this.invalidate();
    }
}
