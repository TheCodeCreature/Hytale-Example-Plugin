package com.UnobstructedThirdPerson.portablebench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A blockless crafting window that opens a bench UI without requiring a placed bench block.
 * Follows the {@link com.hypixel.hytale.builtin.crafting.window.FieldCraftingWindow} pattern:
 * extends {@link Window} directly (not {@code BlockWindow}/{@code BenchWindow}) so there's
 * no block position validation, distance check, or inventory section requirement.
 *
 * <p>Uses {@link WindowType#PocketCrafting} — the client renders a flat recipe list per
 * category tab. Categories are defined by {@link PortableBenchConfig.CategoryDef} entries,
 * and recipes are populated at construction time from {@link CraftingPlugin}.</p>
 *
 * <p><strong>Why PocketCrafting instead of StructuralCrafting?</strong>
 * {@code StructuralCrafting} requires {@code ItemContainerWindow} (65-slot container)
 * and {@code MaterialContainerWindow} (nearby chests). Without a block, both are null
 * in the {@code OpenWindow} packet, causing a client crash.</p>
 */
public class PortableBenchWindow extends Window {

    private static final Logger LOGGER = Logger.getLogger(PortableBenchWindow.class.getSimpleName());
    private static final String CRAFTABLE_CATEGORY_ID = "__craftable__";

    @Nonnull
    private final PortableBenchConfig config;
    @Nonnull
    private final JsonObject windowData = new JsonObject();
    @Nullable
    private Player player;
    @Nullable
    private EventRegistration inventoryChangeReg;

    /**
     * Constructs a PocketCrafting window populated with the given bench's categories and recipes.
     * Starts with all recipes visible; the player can click the filter tab to toggle.
     *
     * @param config the portable bench configuration defining categories and their recipe mappings
     */
    public PortableBenchWindow(@Nonnull PortableBenchConfig config) {
        super(WindowType.PocketCrafting);
        this.config = config;
        this.windowData.addProperty("type", BenchType.Crafting.ordinal());
        this.windowData.addProperty("id", config.benchId());
        this.windowData.addProperty("name", config.benchName());
        buildCategories();
        LOGGER.info("[PortableBench] Window created — benchId=" + config.benchId()
                + ", benchName=" + config.benchName()
                + ", categories=" + config.categories().length);
    }

    /**
     * Builds the {@code "categories"} array in the window data with all recipes
     * and a "Craftable" filter tab that shows only recipes matching inventory.
     */
    private void buildCategories() {
        JsonArray categories = new JsonArray();

        // Add the Craftable filter tab (first position, populated dynamically in onOpen0)
        JsonObject craftableCategory = new JsonObject();
        craftableCategory.addProperty("id", CRAFTABLE_CATEGORY_ID);
        craftableCategory.addProperty("name", "\u2692 Craftable");
        craftableCategory.addProperty("icon", "");
        craftableCategory.add("craftableRecipes", new JsonArray());
        categories.add(craftableCategory);

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
            LOGGER.info("[PortableBench] Category '" + categoryDef.id() + "' (" + categoryDef.name()
                    + ") — " + craftableRecipes.size() + " recipes, recipeCategoryIds="
                    + java.util.Arrays.toString(categoryDef.recipeCategoryIds()));
        }
        this.windowData.add("categories", categories);
        LOGGER.info("[PortableBench] Total categories built: " + categories.size());
    }

    @Nonnull
    @Override
    public JsonObject getData() {
        return this.windowData;
    }

    @Override
    public boolean onOpen0(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        int memoriesLevel = com.hypixel.hytale.builtin.adventure.memories.
                MemoriesPlugin.get().getMemoriesLevel(world.getGameplayConfig());
        this.windowData.addProperty("worldMemoriesLevel", memoriesLevel);

        this.player = store.getComponent(ref, Player.getComponentType());
        rebuildCraftableCategory();

        // Listen for inventory changes to auto-update the Craftable tab
        if (this.player != null) {
            ItemContainer container = this.player.getInventory().getCombinedBackpackStorageHotbar();
            this.inventoryChangeReg = container.registerChangeEvent(e -> rebuildCraftableCategory());
        }

        LOGGER.info("[PortableBench] onOpen0 — worldMemoriesLevel=" + memoriesLevel
                + ", world=" + world.getClass().getSimpleName());
        this.invalidate();
        return true;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        if (this.inventoryChangeReg != null) {
            this.inventoryChangeReg.unregister();
            this.inventoryChangeReg = null;
        }
        this.player = null;
    }

    /**
     * Rebuilds the "Craftable" category to only include recipes whose inputs
     * the player currently has in their inventory.
     */
    private void rebuildCraftableCategory() {
        if (this.player == null) return;

        ItemContainer container = this.player.getInventory().getCombinedBackpackStorageHotbar();
        JsonArray craftableRecipeIds = new JsonArray();

        for (PortableBenchConfig.CategoryDef categoryDef : config.categories()) {
            for (String recipeCategoryId : categoryDef.recipeCategoryIds()) {
                Set<String> recipeIds = CraftingPlugin.getAvailableRecipesForCategory(
                        config.benchId(), recipeCategoryId);
                if (recipeIds == null) continue;
                for (String recipeId : recipeIds) {
                    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
                    if (recipe == null) continue;
                    List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe, 1);
                    if (container.canRemoveMaterials(inputs)) {
                        craftableRecipeIds.add(recipeId);
                    }
                }
            }
        }

        // Update the first category (Craftable tab)
        JsonArray categories = windowData.getAsJsonArray("categories");
        JsonObject craftableCategory = categories.get(0).getAsJsonObject();
        craftableCategory.add("craftableRecipes", craftableRecipeIds);

        setNeedRebuild();
        invalidate();
        LOGGER.info("[PortableBench] Craftable filter updated — " + craftableRecipeIds.size() + " recipes match inventory");
    }

    /**
     * Handles crafting actions by bypassing {@code CraftingManager.craftItem()} which
     * validates bench type/ID against the recipe's {@code BenchRequirement}. Since this
     * window has no placed bench block, the default validation would silently reject all
     * Builders recipes (they require {@code StructuralCrafting} + {@code "Builders"},
     * but null blockType defaults to {@code Crafting} + {@code "Fieldcraft"}).
     *
     * <p>Instead, we manually: validate materials → fire Pre event → consume inputs →
     * fire Post event → give outputs. All using public static APIs on {@link CraftingManager}.</p>
     */
    @SuppressWarnings("removal")
    @Override
    public void handleAction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull WindowAction action) {
        if (!(action instanceof CraftRecipeAction craftAction)) {
            return;
        }

        String recipeId = craftAction.recipeId;
        int quantity = craftAction.quantity;
        if (recipeId == null || quantity <= 0) {
            return;
        }

        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) {
            LOGGER.warning("Unknown recipe: " + recipeId);
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        ItemContainer container = inventory.getCombinedBackpackStorageHotbar();
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe, quantity);

        if (!container.canRemoveMaterials(inputs)) {
            return;
        }

        // Fire Pre event — other systems can cancel the craft
        CraftRecipeEvent.Pre preEvent = new CraftRecipeEvent.Pre(recipe, quantity);
        store.invoke(ref, preEvent);
        if (preEvent.isCancelled()) {
            return;
        }

        // Consume input materials
        ListTransaction<MaterialTransaction> transaction = container.removeMaterials(inputs);
        if (!transaction.succeeded()) {
            return;
        }

        // Fire Post event — other systems can prevent output (materials already consumed)
        CraftRecipeEvent.Post postEvent = new CraftRecipeEvent.Post(recipe, quantity);
        store.invoke(ref, postEvent);
        if (!postEvent.isCancelled()) {
            List<ItemStack> outputs = CraftingManager.getOutputItemStacks(recipe, quantity);
            LOGGER.info("Crafted " + recipeId + " — outputs: " + outputs.size());
            // Output priority: hotbar first, then storage (matches engine crafting behavior)
            SimpleItemContainer.addOrDropItemStacks(
                    store, ref,
                    inventory.getCombinedHotbarFirst(),
                    outputs
            );
        }

        SoundUtil.playSoundEvent2d(
                ref,
                TempAssetIdUtil.getSoundEventIndex("SFX_Workbench_Craft"),
                SoundCategory.UI,
                store
        );

        // Rebuild not needed — no filter state
        this.invalidate();
    }
}
