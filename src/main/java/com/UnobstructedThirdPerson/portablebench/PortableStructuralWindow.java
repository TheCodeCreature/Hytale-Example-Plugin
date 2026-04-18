package com.UnobstructedThirdPerson.portablebench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.ItemQuantity;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.SelectSlotAction;
import com.hypixel.hytale.protocol.packets.window.UpdateCategoryAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A blockless StructuralCrafting window that replicates the Builder's Bench UI
 * (1 input slot + 64 option slots) without requiring a placed bench block.
 *
 * <p>Implements {@link ItemContainerWindow} to provide the container slots and
 * {@link MaterialContainerWindow} with an empty section (no nearby chests).</p>
 *
 * <p>The player places a block/item into the input slot (slot 0). The window then
 * queries all matching recipes and populates the options container (slots 1–64)
 * with the output variants. The player selects a variant and clicks craft.</p>
 *
 * <p>Includes a "Recipe Browser" category tab that switches back to the
 * {@link PortableBenchWindow} (PocketCrafting) for browsing all recipes.</p>
 */
public class PortableStructuralWindow extends Window
        implements ItemContainerWindow, MaterialContainerWindow {

    private static final Logger LOGGER = Logger.getLogger(PortableStructuralWindow.class.getSimpleName());
    private static final String RECIPE_BROWSER_CATEGORY_ID = "__recipe_browser__";

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

    private int selectedSlot = 0;

    public PortableStructuralWindow(@Nonnull PortableBenchConfig config) {
        super(WindowType.StructuralCrafting);
        this.config = config;

        // 1 input slot — player places a block here
        this.inputContainer = new SimpleItemContainer((short) 1);
        this.inputContainer.registerChangeEvent(e -> this.updateRecipes());
        this.inputContainer.setSlotFilter(FilterActionType.ADD, (short) 0, this::isValidInput);

        // 64 output option slots — read-only for the client
        this.optionsContainer = new SimpleItemContainer((short) 64);
        this.optionsContainer.setGlobalFilter(FilterType.DENY_ALL);

        // Combined = input + options as one container for the protocol (65 slots total)
        this.combinedItemContainer = new CombinedItemContainer(this.inputContainer, this.optionsContainer);

        // Empty extra resources (no nearby chests for a portable bench)
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

        LOGGER.info("[PortableStructural] Window created — benchId=" + config.benchId());
    }

    private void buildCategories() {
        JsonArray categories = new JsonArray();

        // Add "Recipe Browser" tab to switch back to PocketCrafting
        JsonObject browserCategory = new JsonObject();
        browserCategory.addProperty("id", RECIPE_BROWSER_CATEGORY_ID);
        browserCategory.addProperty("name", "\u2630 Recipe Browser");
        browserCategory.addProperty("icon", "");
        browserCategory.add("craftableRecipes", new JsonArray());
        categories.add(browserCategory);

        // Add normal categories from config
        for (PortableBenchConfig.CategoryDef categoryDef : config.categories()) {
            JsonObject category = new JsonObject();
            category.addProperty("id", categoryDef.id());
            category.addProperty("name", categoryDef.name());
            category.addProperty("icon", categoryDef.icon());

            JsonArray craftableRecipes = new JsonArray();
            for (String recipeCategoryId : categoryDef.recipeCategoryIds()) {
                var recipeIds = CraftingPlugin.getAvailableRecipesForCategory(
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
     * Only accept items that match at least one recipe for this bench.
     */
    private boolean isValidInput(FilterActionType type, ItemContainer container, short slot, ItemStack stack) {
        if (type != FilterActionType.ADD) return true;
        if (stack == null) return true;
        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(
                BenchType.StructuralCrafting, config.benchId());
        if (recipes == null) return false;
        for (CraftingRecipe recipe : recipes) {
            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
            if (inputs.size() == 1 && CraftingManager.matches(inputs.getFirst(), stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called when the input container changes. Resolves matching recipes and
     * populates the options container with output variants.
     */
    private void updateRecipes() {
        this.optionsContainer.clear();
        this.optionSlotToRecipeMap.clear();

        ItemStack inputStack = this.inputContainer.getItemStack((short) 0);
        if (inputStack == null) {
            this.windowData.add("optionSlotRecipes", new JsonArray());
            this.setNeedRebuild();
            this.invalidate();
            return;
        }

        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(
                BenchType.StructuralCrafting, config.benchId());
        if (recipes == null) {
            this.windowData.add("optionSlotRecipes", new JsonArray());
            this.setNeedRebuild();
            this.invalidate();
            return;
        }

        List<CraftingRecipe> matching = new ArrayList<>();
        for (CraftingRecipe recipe : recipes) {
            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
            if (inputs.size() == 1 && CraftingManager.matches(inputs.getFirst(), inputStack)) {
                matching.add(recipe);
            }
        }

        JsonArray optionSlotRecipes = new JsonArray();
        short index = 0;
        for (CraftingRecipe match : matching) {
            if (index >= 64) break;
            List<ItemStack> output = CraftingManager.getOutputItemStacks(match);
            if (!output.isEmpty()) {
                this.optionsContainer.setItemStackForSlot(index, output.getFirst(), false);
                this.optionSlotToRecipeMap.put(index, match.getId());
                optionSlotRecipes.add(match.getId());
                index++;
            }
        }
        this.windowData.add("optionSlotRecipes", optionSlotRecipes);

        LOGGER.info("[PortableStructural] updateRecipes — input="
                + inputStack.getItem().getId() + ", matched=" + matching.size()
                + ", options=" + index);

        this.setNeedRebuild();
        this.invalidate();
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
        // No-op for portable bench (no nearby chests)
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
        World world = store.getExternalData().getWorld();
        int memoriesLevel = com.hypixel.hytale.builtin.adventure.memories.
                MemoriesPlugin.get().getMemoriesLevel(world.getGameplayConfig());
        this.windowData.addProperty("worldMemoriesLevel", memoriesLevel);
        LOGGER.info("[PortableStructural] onOpen0 — worldMemoriesLevel=" + memoriesLevel);
        this.invalidate();
        return true;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        // Return items from input slot to player's inventory
        List<ItemStack> items = this.inputContainer.dropAllItemStacks();
        if (!items.isEmpty()) {
            Player player = componentAccessor.getComponent(ref, Player.getComponentType());
            if (player != null) {
                SimpleItemContainer.addOrDropItemStacks(
                        componentAccessor, ref,
                        player.getInventory().getCombinedHotbarFirst(),
                        items
                );
                LOGGER.info("[PortableStructural] Returned " + items.size() + " item(s) to player on close");
            }
        }
    }

    @SuppressWarnings("removal")
    @Override
    public void handleAction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull WindowAction action) {
        LOGGER.info("[PortableStructural] handleAction — type=" + action.getClass().getSimpleName());

        // Handle "Recipe Browser" tab click — switch to PocketCrafting window
        if (action instanceof UpdateCategoryAction catAction) {
            if (RECIPE_BROWSER_CATEGORY_ID.equals(catAction.category)) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) return;

                // Return input items before switching
                List<ItemStack> items = this.inputContainer.dropAllItemStacks();
                if (!items.isEmpty()) {
                    SimpleItemContainer.addOrDropItemStacks(
                            store, ref,
                            player.getInventory().getCombinedHotbarFirst(),
                            items
                    );
                }

                LOGGER.info("[PortableStructural] Switching to Recipe Browser mode");
                PortableBenchWindow browserWindow = new PortableBenchWindow(config);
                player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, browserWindow);
            }
            return;
        }

        // Handle slot selection
        if (action instanceof SelectSlotAction selectAction) {
            this.selectedSlot = Math.max(0, Math.min(selectAction.slot, optionsContainer.getCapacity() - 1));
            this.windowData.addProperty("selected", this.selectedSlot);
            this.invalidate();
            return;
        }

        // Handle crafting
        if (action instanceof CraftRecipeAction craftAction) {
            String recipeId = this.optionSlotToRecipeMap.get((short) this.selectedSlot);
            if (recipeId == null) {
                LOGGER.warning("[PortableStructural] No recipe at selected slot " + this.selectedSlot);
                return;
            }

            int quantity = craftAction.quantity;
            if (quantity <= 0) return;

            CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
            if (recipe == null) {
                LOGGER.warning("[PortableStructural] Unknown recipe: " + recipeId);
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            // Check input container has the required materials
            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe, quantity);
            if (!this.inputContainer.canRemoveMaterials(inputs)) {
                LOGGER.info("[PortableStructural] Not enough materials in input slot for " + recipeId);
                return;
            }

            // Fire Pre event
            CraftRecipeEvent.Pre preEvent = new CraftRecipeEvent.Pre(recipe, quantity);
            store.invoke(ref, preEvent);
            if (preEvent.isCancelled()) return;

            // Consume input materials from the input slot
            ListTransaction<MaterialTransaction> transaction = this.inputContainer.removeMaterials(inputs);
            if (!transaction.succeeded()) return;

            // Fire Post event
            CraftRecipeEvent.Post postEvent = new CraftRecipeEvent.Post(recipe, quantity);
            store.invoke(ref, postEvent);
            if (!postEvent.isCancelled()) {
                List<ItemStack> outputs = CraftingManager.getOutputItemStacks(recipe, quantity);
                LOGGER.info("[PortableStructural] Crafted " + recipeId + " x" + quantity
                        + " — outputs: " + outputs.size());
                Inventory inventory = player.getInventory();
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
        }
    }
}
