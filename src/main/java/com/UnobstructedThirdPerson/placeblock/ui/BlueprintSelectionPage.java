package com.UnobstructedThirdPerson.placeblock.ui;

import com.UnobstructedThirdPerson.placeblock.BlockPreviewReskinManager;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockCostUtil;
import com.UnobstructedThirdPerson.placeblock.PlaceBlockMetadata;
import com.UnobstructedThirdPerson.resourcecollection.BenchCategory;
import com.UnobstructedThirdPerson.resourcecollection.FilteredRecipeEntry;
import com.UnobstructedThirdPerson.resourcecollection.NaturalResourceRegistry;
import com.UnobstructedThirdPerson.resourcecollection.RecipeFilterRegistry;
import com.UnobstructedThirdPerson.resourcecollection.ResourceTypeResolver;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

public class BlueprintSelectionPage extends InteractiveCustomUIPage<BlueprintSelectionPage.EventPayload> {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("BlueprintSelectionPage");

    private static final int MAX_SET_FILTERS = 20;
    private static final int MAX_COST_CELLS = 8;
    private static final int MAX_PLACEHOLDER_ROWS = PlaceBlockMetadata.HOTBAR_SIZE; // 9

    private static final Value<String> FILTER_ACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "FilterActiveStyle");
    private static final Value<String> FILTER_INACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "FilterInactiveStyle");

    private static final String ALL_TAB = "All";
    private static final String ALL_FILTER = "All";

    private final RecipeFilterPipeline pipeline = new RecipeFilterPipeline();
    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private final List<String> benchIds = new ArrayList<>();  // sorted bench IDs
    private String searchQuery = "";
    private String selectedRecipeId;
    private String activeTab = ALL_TAB;
    private final Set<String> activeSetFilters = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private boolean affordabilityEnabled = true;
    private boolean showUncategorized = false;
    private List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes = new ArrayList<>();
    private List<String> currentSets = new ArrayList<>();  // sets for active tab

    private Ref<EntityStore> playerRef_ref;
    private Store<EntityStore> playerStore;

    public BlueprintSelectionPage(@NonNull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
    }

    private void loadRecipes() {
        allRecipes.clear();
        Set<String> benchSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (FilteredRecipeEntry fe : RecipeFilterRegistry.getAllEntries()) {
            // Use primary bench ID for tab display (first alphabetically)
            String primaryBench = fe.benchIds().stream()
                    .min(String.CASE_INSENSITIVE_ORDER)
                    .orElse(null);
            benchSet.addAll(fe.benchIds());

            allRecipes.add(new RecipeEntry(fe.recipeId(), fe.outputItemId(), fe.blockTypeId(),
                    primaryBench, fe.set(), true));
        }
        allRecipes.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.recipeId, b.recipeId));

        benchIds.clear();
        benchIds.addAll(benchSet);
        LOGGER.info("[BlueprintBench] Loaded bench IDs: " + benchIds);

        applyFilter();
    }

    /**
     * Delegates to {@link RecipeFilterPipeline#execute} and stores the result
     * in {@link #displayedRecipes} and {@link #currentSets}.
     */
    private void applyFilter() {
        // Get player inventory for affordability checks
        Player filterPlayer = playerStore != null
                ? playerStore.getComponent(playerRef_ref, Player.getComponentType()) : null;
        var container = filterPlayer != null
                ? filterPlayer.getInventory().getCombinedBackpackStorageHotbar() : null;

        // Build affordability checker — null when disabled or no inventory
        RecipeFilterPipeline.AffordabilityChecker checker = null;
        if (affordabilityEnabled && container != null) {
            final var inv = container;
            checker = recipe -> isAffordable(recipe, inv);
        }

        // Convert allRecipes to InputRecipe list
        List<RecipeFilterPipeline.InputRecipe> inputs = new ArrayList<>();
        for (RecipeEntry entry : allRecipes) {
            inputs.add(new RecipeFilterPipeline.InputRecipe(
                    entry.recipeId(), entry.outputItemId(), entry.blockTypeId(),
                    entry.benchId(), entry.set()));
        }

        // Execute pipeline
        RecipeFilterPipeline.PipelineResult result = pipeline.execute(
                inputs, activeTab, activeSetFilters, searchQuery, checker,
                affordabilityEnabled, showUncategorized);

        this.displayedRecipes = result.displayedRecipes();
        this.currentSets = result.currentSets();
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        // Load persisted preferences
        BlueprintBenchPrefs prefs = BlueprintBenchPrefsStore.load(this.playerRef.getUuid());
        this.activeTab = prefs.activeTab != null ? prefs.activeTab : ALL_TAB;
        this.activeSetFilters.clear();
        this.activeSetFilters.addAll(prefs.activeSetFilters);
        this.affordabilityEnabled = prefs.affordabilityEnabled;
        this.showUncategorized = prefs.showUncategorized;
        this.searchQuery = prefs.searchQuery != null ? prefs.searchQuery : "";
        this.selectedRecipeId = prefs.selectedRecipeId;

        loadRecipes();

        // Load main template
        cmd.append("Pages/BlueprintBench/BlueprintBenchPage.ui");

        // ── Append reusable components into empty containers (one-time init) ──

        // Set filter buttons: 1 "All" + MAX_SET_FILTERS indexed buttons
        for (int i = 0; i < 1 + MAX_SET_FILTERS; i++) {
            cmd.append("#SetFilters", "Pages/BlueprintBench/SetFilterButton.ui");
        }
        // Set "All" text on the first filter button
        cmd.set("#SetFilters[0].Text", "All");

        // Cost cells
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            cmd.append("#CostGrid", "Pages/BlueprintBench/CostCell.ui");
        }

        // Placeholder rows
        for (int i = 0; i < MAX_PLACEHOLDER_ROWS; i++) {
            cmd.append("#PlaceholderList", "Pages/BlueprintBench/PlaceholderRow.ui");
        }

        // ── Bind ALL events (one-time) ──

        // Search input
        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );

        // Toggle buttons
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#AffordableToggle",
                EventData.of("Action", "ToggleAffordable")
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#UncategorizedToggle",
                EventData.of("Action", "ToggleUncategorized")
        );

        // Get Placeholder button
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#GetPlaceholderBtn",
                EventData.of("Action", "GetPlaceholder")
        );

        buildBenchTabs(evt);
        buildSetFilterBindings(evt);
        buildRecipeGridBindings(evt);
        buildPlaceholderBindings(evt);

        // ── Set initial state ──
        cmd.set("#AffordableToggle.Style", affordabilityEnabled ? FILTER_ACTIVE : FILTER_INACTIVE);
        cmd.set("#UncategorizedToggle.Style", showUncategorized ? FILTER_ACTIVE : FILTER_INACTIVE);

        updateBenchTabs(cmd);
        updateSetFilters(cmd);
        updateRecipeGrid(cmd);
        updateDetailPanel(cmd);
        updatePlaceholderList(cmd, store, ref);
    }

    @Override
    public void onDismiss(@NonNull Ref<EntityStore> ref, @NonNull Store<EntityStore> store) {
        savePrefs();

        // Clear tooltip-triggering data so any visible tooltip is dismissed.
        // The engine's tooltip overlay is independent of the page lifecycle,
        // so we must explicitly remove the data that drives tooltips.
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#OutputIcon.ItemId", "");
        cmd.set("#RecipeGrid.Slots", new ItemGridSlot[0]);
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            cmd.set("#CostGrid[" + i + "].Visible", false);
        }
        sendUpdate(cmd, null, false);
    }

    private void savePrefs() {
        BlueprintBenchPrefs prefs = new BlueprintBenchPrefs();
        prefs.activeTab = this.activeTab;
        prefs.activeSetFilters = new ArrayList<>(this.activeSetFilters);
        prefs.affordabilityEnabled = this.affordabilityEnabled;
        prefs.showUncategorized = this.showUncategorized;
        prefs.searchQuery = this.searchQuery;
        prefs.selectedRecipeId = this.selectedRecipeId;
        BlueprintBenchPrefsStore.save(this.playerRef.getUuid(), prefs);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        UICommandBuilder cmd = new UICommandBuilder();
        // No UIEventBuilder — all events were bound in build()

        if (data.selectedTab != null) {
            // Tab switch
            String tab = data.selectedTab;
            if (!tab.equals(this.activeTab)) {
                this.activeTab = tab;
                this.activeSetFilters.clear();
                this.searchQuery = "";
                this.selectedRecipeId = null;
                applyFilter();
                cmd.set("#SearchInput.Value", "");
                updateBenchTabs(cmd);
                updateSetFilters(cmd);
                updateRecipeGrid(cmd);
                updateDetailPanel(cmd);
                savePrefs();
            }
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("SetFilter:")) {
            // Set filter toggle — index-based resolution
            String filterPayload = data.action.substring("SetFilter:".length());
            if (ALL_FILTER.equals(filterPayload)) {
                activeSetFilters.clear();
            } else if (filterPayload.startsWith("idx:")) {
                int idx = -1;
                try {
                    idx = Integer.parseInt(filterPayload.substring(4));
                } catch (NumberFormatException ignored) {}
                if (idx >= 0 && idx < currentSets.size()) {
                    String setName = currentSets.get(idx);
                    if (activeSetFilters.contains(setName)) {
                        activeSetFilters.remove(setName);
                    } else {
                        activeSetFilters.add(setName);
                    }
                }
            }
            this.selectedRecipeId = null;
            applyFilter();
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim();
            applyFilter();
            this.selectedRecipeId = null;
            updateBenchTabs(cmd);
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if ("ToggleAffordable".equals(data.action)) {
            this.affordabilityEnabled = !this.affordabilityEnabled;
            applyFilter();
            cmd.set("#AffordableToggle.Style", affordabilityEnabled ? FILTER_ACTIVE : FILTER_INACTIVE);
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if ("ToggleUncategorized".equals(data.action)) {
            this.showUncategorized = !this.showUncategorized;
            applyFilter();
            cmd.set("#UncategorizedToggle.Style", showUncategorized ? FILTER_ACTIVE : FILTER_INACTIVE);
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("PlaceholderDrop:")) {
            int hotbarSlot = -1;
            try {
                hotbarSlot = Integer.parseInt(data.action.substring("PlaceholderDrop:".length()));
            } catch (NumberFormatException ignored) {}

            if (hotbarSlot >= 0 && hotbarSlot < PlaceBlockMetadata.HOTBAR_SIZE
                    && data.itemStackId != null && !data.itemStackId.isEmpty()) {
                armPlaceholder(store, ref, hotbarSlot, data.itemStackId);
                selectRecipeByItemId(data.itemStackId);
                updateDetailPanel(cmd);
                updatePlaceholderList(cmd, store, ref);
                LOGGER.info("[BlueprintUI] Armed slot " + hotbarSlot + " with " + data.itemStackId);
            }
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("PlaceholderClear:")) {
            int hotbarSlot = -1;
            try {
                hotbarSlot = Integer.parseInt(data.action.substring("PlaceholderClear:".length()));
            } catch (NumberFormatException ignored) {}

            if (hotbarSlot >= 0 && hotbarSlot < PlaceBlockMetadata.HOTBAR_SIZE) {
                disarmPlaceholder(store, ref, hotbarSlot);
                updatePlaceholderList(cmd, store, ref);
                LOGGER.info("[BlueprintUI] Disarmed slot " + hotbarSlot);
            }
            sendUpdate(cmd, null, false);

        } else if ("RecipeHover".equals(data.action) || "RecipeSelect".equals(data.action)) {
            if (data.slotIndex != null && data.slotIndex >= 0 && data.slotIndex < displayedRecipes.size()) {
                RecipeFilterPipeline.TaggedRecipe entry = displayedRecipes.get(data.slotIndex);
                this.selectedRecipeId = entry.recipeId();
                updateDetailPanel(cmd);
                savePrefs();
                sendUpdate(cmd, null, false);
            }

        } else if ("RecipeDragSelect".equals(data.action)) {
            boolean selected = false;
            if (data.slotIndex != null && data.slotIndex >= 0 && data.slotIndex < displayedRecipes.size()) {
                RecipeFilterPipeline.TaggedRecipe entry = displayedRecipes.get(data.slotIndex);
                this.selectedRecipeId = entry.recipeId();
                selected = true;
            } else if (data.itemStackId != null) {
                selected = selectRecipeByItemId(data.itemStackId);
            }
            if (selected) {
                updateDetailPanel(cmd);
                savePrefs();
            }
            sendUpdate(cmd, null, false);

        } else if ("GetPlaceholder".equals(data.action)) {
            craftPlaceholder(store, ref, cmd);
            updatePlaceholderList(cmd, store, ref);
            sendUpdate(cmd, null, false);
        }
    }

    private void buildBenchTabs(UIEventBuilder evt) {
        evt.addEventBinding(
                CustomUIEventBindingType.SelectedTabChanged, "#BenchTabs",
                EventData.of("@SelectedTab", "#BenchTabs.SelectedTab"),
                false
        );
    }

    private void updateBenchTabs(UICommandBuilder cmd) {
        cmd.set("#BenchTabs.SelectedTab", activeTab);
        cmd.set("#ActiveBenchLabel.Text", tabDisplayName(activeTab));
    }

    private static String tabDisplayName(String tabId) {
        if (tabId == null) return "";
        return tabId.replace('_', ' ');
    }

    private void buildSetFilterBindings(UIEventBuilder evt) {
        // Index 0 is the "All" button
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#SetFilters[0]",
                EventData.of("Action", "SetFilter:" + ALL_FILTER)
        );
        // Indices 1..MAX_SET_FILTERS are the per-set buttons
        for (int i = 0; i < MAX_SET_FILTERS; i++) {
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating, "#SetFilters[" + (i + 1) + "]",
                    EventData.of("Action", "SetFilter:idx:" + i)
            );
        }
    }

    private void updateSetFilters(UICommandBuilder cmd) {
        // "All" filter button — index 0
        cmd.set("#SetFilters[0].Visible", !currentSets.isEmpty());
        cmd.set("#SetFilters[0].Style", activeSetFilters.isEmpty() ? FILTER_ACTIVE : FILTER_INACTIVE);

        // Per-set filter buttons — indices 1..MAX_SET_FILTERS
        for (int i = 0; i < MAX_SET_FILTERS; i++) {
            String sel = "#SetFilters[" + (i + 1) + "]";
            if (i < currentSets.size()) {
                String setName = currentSets.get(i);
                String label = RecipeFilterPipeline.setDisplayLabel(setName);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + ".Text", label);
                cmd.set(sel + ".Style", activeSetFilters.contains(setName) ? FILTER_ACTIVE : FILTER_INACTIVE);
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }
    }

    private void buildRecipeGridBindings(UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#RecipeGrid",
                EventData.of("Action", "RecipeHover"), false);
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#RecipeGrid",
                EventData.of("Action", "RecipeSelect"), false);
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseDragCompleted, "#RecipeGrid",
                EventData.of("Action", "RecipeDragSelect"), false);
        evt.addEventBinding(CustomUIEventBindingType.DragCancelled, "#RecipeGrid",
                EventData.of("Action", "RecipeDragSelect"), false);
    }

    private void updateRecipeGrid(UICommandBuilder cmd) {
        ItemGridSlot[] recipeSlots = new ItemGridSlot[displayedRecipes.size()];
        for (int i = 0; i < displayedRecipes.size(); i++) {
            RecipeFilterPipeline.TaggedRecipe entry = displayedRecipes.get(i);
            ItemGridSlot slot = new ItemGridSlot(new ItemStack(entry.outputItemId(), 1));
            slot.setActivatable(true);
            slot.setName(entry.blockTypeId() != null
                    ? entry.blockTypeId().replace('_', ' ') : entry.outputItemId().replace('_', ' '));
            if (!entry.affordable()) {
                slot.setItemUncraftable(true);
                slot.setItemIncompatible(true);
            }
            recipeSlots[i] = slot;
        }
        cmd.set("#RecipeGrid.Slots", recipeSlots);
    }

    private void updateDetailPanel(UICommandBuilder cmd) {
        if (selectedRecipeId != null) {
            RecipeEntry entry = findEntry(selectedRecipeId);
            if (entry != null) {
                cmd.set("#OutputIcon.ItemId", entry.outputItemId);
                cmd.set("#OutputName.Text", entry.blockTypeId != null
                        ? entry.blockTypeId.replace('_', ' ') : entry.outputItemId);

                int costIdx = 0;
                try {
                    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                    if (recipe != null) {
                        List<MaterialQuantity> perUnitInputs = PlaceBlockCostUtil.getPerUnitCost(recipe);
                        if (!perUnitInputs.isEmpty()) {
                            FilteredRecipeEntry fe = RecipeFilterRegistry.getEntry(entry.recipeId);
                            BenchCategory category = fe != null ? fe.benchCategory() : BenchCategory.BUILDERS_ONLY;

                            Map<String, Integer> ingredientMap = new LinkedHashMap<>();
                            for (MaterialQuantity mq : perUnitInputs) {
                                if (mq == null) continue;
                                String itemId = ResourceTypeResolver.resolveInputItemId(mq, category);
                                if (itemId == null || itemId.isEmpty()) continue;
                                itemId = NaturalResourceRegistry.resolveToGatherableForm(itemId);
                                ingredientMap.merge(itemId, mq.getQuantity(), Integer::sum);
                            }

                            for (var e : ingredientMap.entrySet()) {
                                if (costIdx >= MAX_COST_CELLS) break;
                                String sel = "#CostGrid[" + costIdx + "]";
                                cmd.set(sel + ".Visible", true);
                                cmd.set(sel + " #CostIcon.ItemId", e.getKey());
                                cmd.set(sel + " #CostQty.Text", "x" + e.getValue());
                                costIdx++;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("[BlueprintUI] Error populating cost grid: " + e.getMessage());
                }

                // Hide remaining cost cells
                for (int i = costIdx; i < MAX_COST_CELLS; i++) {
                    cmd.set("#CostGrid[" + i + "].Visible", false);
                }
                return;
            }
        }
        cmd.set("#OutputIcon.ItemId", "");
        cmd.set("#OutputName.Text", "No recipe selected");
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            cmd.set("#CostGrid[" + i + "].Visible", false);
        }
    }

    // ─── Placeholder list ─────────────────────────────────────

    private void buildPlaceholderBindings(UIEventBuilder evt) {
        for (int i = 0; i < MAX_PLACEHOLDER_ROWS; i++) {
            evt.addEventBinding(CustomUIEventBindingType.Dropped,
                    "#PlaceholderList[" + i + "] #RowInputSlot",
                    EventData.of("Action", "PlaceholderDrop:" + i), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                    "#PlaceholderList[" + i + "] #RowClearBtn",
                    EventData.of("Action", "PlaceholderClear:" + i));
        }
    }

    private void updatePlaceholderList(UICommandBuilder cmd,
                                       Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean anyPlaceholder = false;

        if (player != null) {
            ItemContainer hotbar = player.getInventory().getHotbar();
            for (int i = 0; i < MAX_PLACEHOLDER_ROWS; i++) {
                String rowSel = "#PlaceholderList[" + i + "]";
                ItemStack stack = hotbar.getItemStack((short) i);

                if (PlaceBlockMetadata.isPlaceBlock(stack)) {
                    anyPlaceholder = true;
                    cmd.set(rowSel + ".Visible", true);
                    cmd.set(rowSel + " #RowSlotLabel.Text", String.valueOf(i + 1));

                    boolean armed = PlaceBlockMetadata.isArmed(stack);
                    if (armed) {
                        String blockTypeId = PlaceBlockMetadata.getOutputBlockTypeId(stack);
                        String blockName = blockTypeId != null ? blockTypeId.replace('_', ' ') : "";
                        String outputItemId = null;
                        if (blockTypeId != null) {
                            for (RecipeEntry entry : allRecipes) {
                                if (blockTypeId.equals(entry.blockTypeId())) {
                                    outputItemId = entry.outputItemId();
                                    break;
                                }
                            }
                        }
                        if (outputItemId != null) {
                            ItemGridSlot slot = new ItemGridSlot(new ItemStack(outputItemId, 1));
                            slot.setActivatable(true);
                            slot.setName(blockName);
                            cmd.set(rowSel + " #RowInputSlot.Slots", new ItemGridSlot[]{slot});
                        } else {
                            ItemGridSlot emptySlot = new ItemGridSlot();
                            emptySlot.setActivatable(true);
                            cmd.set(rowSel + " #RowInputSlot.Slots", new ItemGridSlot[]{emptySlot});
                        }
                        cmd.set(rowSel + " #RowBlockName.Text", blockName);
                        cmd.set(rowSel + " #RowClearBtn.Visible", true);
                    } else {
                        ItemGridSlot emptySlot = new ItemGridSlot();
                        emptySlot.setActivatable(true);
                        cmd.set(rowSel + " #RowInputSlot.Slots", new ItemGridSlot[]{emptySlot});
                        cmd.set(rowSel + " #RowBlockName.Text", "Empty");
                        cmd.set(rowSel + " #RowClearBtn.Visible", false);
                    }
                } else {
                    cmd.set(rowSel + ".Visible", false);
                }
            }
        } else {
            for (int i = 0; i < MAX_PLACEHOLDER_ROWS; i++) {
                cmd.set("#PlaceholderList[" + i + "].Visible", false);
            }
        }

        cmd.set("#NoPlaceholdersLabel.Visible", !anyPlaceholder);
    }

    private void armPlaceholder(Store<EntityStore> store, Ref<EntityStore> ref,
                                int hotbarSlot, String droppedItemId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // Find the recipe and block type for the dropped item
        String recipeId = null;
        String blockTypeId = null;
        for (RecipeEntry entry : allRecipes) {
            if (droppedItemId.equals(entry.outputItemId())) {
                recipeId = entry.recipeId();
                blockTypeId = entry.blockTypeId();
                break;
            }
        }
        if (recipeId == null || blockTypeId == null) {
            LOGGER.warning("[BlueprintUI] No recipe found for dropped item: " + droppedItemId);
            return;
        }

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        ItemStack stack = hotbar.getItemStack((short) hotbarSlot);
        if (!PlaceBlockMetadata.isPlaceBlock(stack)) return;

        ItemStack armed = PlaceBlockMetadata.arm(stack, recipeId, blockTypeId, hotbarSlot);
        hotbar.setItemStackForSlot((short) hotbarSlot, armed);

        // Sync ghost preview for the updated slot
        BlockPreviewReskinManager.syncPlaceholder(this.playerRef, inventory);
    }

    private void disarmPlaceholder(Store<EntityStore> store, Ref<EntityStore> ref,
                                   int hotbarSlot) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        ItemStack stack = hotbar.getItemStack((short) hotbarSlot);
        if (!PlaceBlockMetadata.isPlaceBlock(stack)) return;

        ItemStack disarmed = PlaceBlockMetadata.disarm(stack);
        hotbar.setItemStackForSlot((short) hotbarSlot, disarmed);

        // Sync ghost preview
        BlockPreviewReskinManager.syncPlaceholder(this.playerRef, inventory);
    }

    private static final String LIFE_ESSENCE_ID = "Ingredient_Life_Essence";

    private void craftPlaceholder(Store<EntityStore> store, Ref<EntityStore> ref,
                                  UICommandBuilder cmd) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        Inventory inventory = player.getInventory();
        ItemContainer container = inventory.getCombinedBackpackStorageHotbar();

        // Check for 1x Life Essence
        List<MaterialQuantity> cost = List.of(
                new MaterialQuantity(LIFE_ESSENCE_ID, null, null, 1, null));

        if (!container.canRemoveMaterials(cost)) {
            this.playerRef.sendMessage(
                    Message.raw("\u00a7c[BlueprintBench] Not enough Life Essence."));
            return;
        }

        // Consume 1x Life Essence
        var txn = container.removeMaterials(cost, true, true, true);
        if (!txn.succeeded()) return;

        // Give 1x Block_Placeholder to hotbar-first
        ItemStack placeholder = new ItemStack(PlaceBlockMetadata.PLACEHOLDER_ID, 1);
        inventory.getCombinedHotbarFirst().addItemStack(placeholder);

        this.playerRef.sendMessage(
                Message.raw("\u00a7a[BlueprintBench] Acquired Block Placeholder."));
        LOGGER.info("[BlueprintUI] Player crafted placeholder from Life Essence");
    }

    private boolean selectRecipeByItemId(String itemId) {
        for (RecipeEntry entry : allRecipes) {
            if (itemId.equals(entry.outputItemId())) {
                this.selectedRecipeId = entry.recipeId();
                return true;
            }
        }
        return false;
    }

    @Nullable
    private RecipeEntry findEntry(String recipeId) {
        for (RecipeEntry entry : allRecipes) {
            if (entry.recipeId.equals(recipeId)) return entry;
        }
        return null;
    }

    /**
     * Checks if a recipe is affordable, accounting for both raw material
     * availability and BlockGroup interchangeability (FullBlocks cycling).
     *
     * <p>A recipe is affordable if:
     * <ol>
     *   <li>The player can directly craft it (has raw materials), OR</li>
     *   <li>The output belongs to a BlockGroup and the player has any
     *       member of that group in inventory (free conversion)</li>
     * </ol>
     */
    private boolean isAffordable(RecipeFilterPipeline.InputRecipe entry, CombinedItemContainer container) {
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId());
        if (recipe != null) {
            List<MaterialQuantity> materials = PlaceBlockCostUtil.getPerUnitCost(recipe);
            if (container.canRemoveMaterials(materials)) return true;
        }

        Item outputItem = Item.getAssetMap().getAsset(entry.outputItemId());
        if (outputItem != null) {
            BlockGroup group = BlockGroup.findItemGroup(outputItem);
            if (group != null) {
                for (int i = 0; i < group.size(); i++) {
                    String memberId = group.get(i);
                    if (container.canRemoveMaterials(List.of(
                            new MaterialQuantity(memberId, null, null, 1, null)))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private record RecipeEntry(String recipeId, String outputItemId, String blockTypeId,
                               String benchId, String set, boolean affordable) {}

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (e, s) -> e.searchQuery = s, e -> e.searchQuery).add()
                .append(new KeyedCodec<>("@SelectedTab", Codec.STRING), (e, s) -> e.selectedTab = s, e -> e.selectedTab).add()
                .append(new KeyedCodec<>("RecipeId", Codec.STRING), (e, s) -> e.recipeId = s, e -> e.recipeId).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (e, s) -> e.itemStackId = s, e -> e.itemStackId).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (e, i) -> e.slotIndex = i, e -> e.slotIndex).add()
                .build();

        String searchQuery;
        String selectedTab;
        String recipeId;
        String action;
        String itemStackId;
        Integer slotIndex;
    }
}
