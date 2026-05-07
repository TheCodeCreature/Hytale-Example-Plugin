package com.UnobstructedThirdPerson.placeblock.ui;

import com.UnobstructedThirdPerson.placeblock.PlaceBlockCostUtil;
import com.UnobstructedThirdPerson.placeblock.RecipeAffordabilityResolver;
import com.UnobstructedThirdPerson.placeblock.ResolvedIngredient;
import com.UnobstructedThirdPerson.stencil.StencilMetadata;
import com.UnobstructedThirdPerson.resourcecollection.BenchCategory;
import com.UnobstructedThirdPerson.resourcecollection.FilteredRecipeEntry;
import com.UnobstructedThirdPerson.resourcecollection.RecipeFilterRegistry;
import com.UnobstructedThirdPerson.placeblock.ui.ingredienttree.IngredientTree;
import com.UnobstructedThirdPerson.placeblock.ui.ingredienttree.IngredientTreeBuilder;
import com.UnobstructedThirdPerson.placeblock.ui.ingredienttree.IngredientTreeGridController;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
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
import com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory;

public class BlueprintSelectionPage extends InteractiveCustomUIPage<BlueprintSelectionPage.EventPayload> {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("BlueprintSelectionPage");

    private static final int MAX_GROUP_BUTTONS = 30;
    private static final int MAX_COST_CELLS = 8;

    private static final Value<String> FILTER_ACTIVE =
            Value.ref("Styles/Buttons.ui", "FilterActiveStyle");
    private static final Value<String> FILTER_INACTIVE =
            Value.ref("Styles/Buttons.ui", "FilterInactiveStyle");

    // Selected cell highlight
    private static final Value<String> CELL_SELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "SelectedCellButtonStyle");
    private static final Value<String> CELL_UNSELECTED_STYLE =
            Value.ref("Styles/Buttons.ui", "TransparentButtonStyle");

    // Per-ingredient cost affordability
    private static final Value<String> COST_QTY_NORMAL =
            Value.ref("Styles/Labels.ui", "CostQuantityStyle");
    private static final Value<String> COST_QTY_INSUFFICIENT =
            Value.ref("Styles/Labels.ui", "CostQuantityInsufficientStyle");

    // Output detail panel states
    private static final Value<String> DETAIL_LABEL_NORMAL =
            Value.ref("Styles/Labels.ui", "DetailLabelStyle");
    private static final Value<String> DETAIL_LABEL_MUTED =
            Value.ref("Styles/Labels.ui", "DetailLabelMutedStyle");

    private static final String OUTPUT_BG_NORMAL = "Common/BlockSelectorSlotBackground.png";
    private static final String OUTPUT_BG_EMPTY = "Common/UnknownItemIcon.png";
    private static final String OUTPUT_BG_UNAFFORDABLE = "Common/Buttons/Destructive.png";

    private static final String ALL_TAB = "All";
    private static final String ALL_FILTER = "All";

    private final RecipeFilterPipeline pipeline = new RecipeFilterPipeline();
    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private final List<String> benchIds = new ArrayList<>();  // sorted bench IDs
    private String searchQuery = "";
    private String selectedRecipeId;
    private String activeTab = ALL_TAB;
    private final Set<String> activeSetFilters = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private AffordabilityMode affordabilityMode = AffordabilityMode.INVENTORY_DRIVEN;
    private IngredientTree ingredientTree;
    private IngredientTreeGridController ingredientController;
    private boolean categoriesExpanded = true;
    private boolean setsExpanded = true;
    private boolean selectAllSets = false;
    private boolean selectAllCategories = false;
    private final Set<String> activeMaterialGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private List<RecipeFilterPipeline.MaterialGroup> currentGroups = new ArrayList<>();
    private List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes = new ArrayList<>();
    private List<String> currentSets = new ArrayList<>();  // sets for active tab

    /** Computed at build time from unfiltered pipeline output. */
    private int totalSetCount;
    private String[] maxLayoutSetNames; // set name for each group slot, indexed 0..totalSetCount-1
    private int[] cellsPerSet;         // recipe count per set, indexed 0..totalSetCount-1
    private int[] groupCellOffset;     // prefix-sum: groupCellOffset[g] = sum(cellsPerSet[0..g-1])
    private int totalCellCount;        // sum of all cellsPerSet
    private int[] cellSlotToRecipeIndex; // flat index → displayedRecipes index; length = totalCellCount
    private Map<String, Integer> setNameToGroupIndex; // set name → max layout group index
    private Map<String, RecipeFilterPipeline.CategoryInfo> categoryInfoMap = Map.of();

    private Ref<EntityStore> playerRef_ref;
    private Store<EntityStore> playerStore;

    public BlueprintSelectionPage(@NonNull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
    }

    private record MaxLayoutInfo(int setCount, String[] setNames, int[] recipesPerSet, int totalCells) {}

    private MaxLayoutInfo computeMaxLayout() {
        // Convert allRecipes to InputRecipe list
        List<RecipeFilterPipeline.InputRecipe> inputs = new ArrayList<>();
        for (RecipeEntry entry : allRecipes) {
            inputs.add(new RecipeFilterPipeline.InputRecipe(
                    entry.recipeId(), entry.outputItemId(), entry.blockTypeId(),
                    entry.benchId(), entry.set(), entry.categoryIds()));
        }

        // Run pipeline with NO filtering — discover all sets and recipe counts
        RecipeFilterPipeline.PipelineResult maxResult = pipeline.execute(
                inputs, ALL_TAB, Set.of(), Set.of(), "", null, false, categoryInfoMap, null);

        // The result is sorted by effectiveSet — walk it to count recipes per set
        List<String> setNames = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        String currentSet = null;
        int count = 0;
        for (RecipeFilterPipeline.TaggedRecipe r : maxResult.displayedRecipes()) {
            if (!r.effectiveSet().equals(currentSet)) {
                if (currentSet != null) {
                    setNames.add(currentSet);
                    counts.add(count);
                }
                currentSet = r.effectiveSet();
                count = 0;
            }
            count++;
        }
        if (currentSet != null) {
            setNames.add(currentSet);
            counts.add(count);
        }

        int totalCells = counts.stream().mapToInt(Integer::intValue).sum();
        return new MaxLayoutInfo(
                setNames.size(),
                setNames.toArray(new String[0]),
                counts.stream().mapToInt(Integer::intValue).toArray(),
                totalCells
        );
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
                    primaryBench, fe.set(), fe.categoryIds()));
        }
        allRecipes.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.recipeId, b.recipeId));

        benchIds.clear();
        benchIds.addAll(benchSet);
        LOGGER.info("[BlueprintBench] Loaded bench IDs: " + benchIds);

        // Debug: log category coverage
        long withCats = allRecipes.stream().filter(r -> r.categoryIds() != null && !r.categoryIds().isEmpty()).count();
        LOGGER.info("[BlueprintBench] Recipes with categories: " + withCats + "/" + allRecipes.size());
        // Debug: log distinct category IDs from recipes
        Set<String> recipeCatIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (RecipeEntry r : allRecipes) {
            if (r.categoryIds() != null) recipeCatIds.addAll(r.categoryIds());
        }
        LOGGER.info("[BlueprintBench] Distinct recipe category IDs: " + recipeCatIds);

        this.categoryInfoMap = buildCategoryInfoMap();

        // Build ingredient tree from all recipes
        List<CraftingRecipe> craftingRecipes = new ArrayList<>();
        for (RecipeEntry entry : allRecipes) {
            CraftingRecipe cr = CraftingRecipe.getAssetMap().getAsset(entry.recipeId());
            if (cr != null) craftingRecipes.add(cr);
        }
        this.ingredientTree = IngredientTreeBuilder.build(craftingRecipes);
        this.ingredientController = new IngredientTreeGridController(ingredientTree);

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
        boolean affordableOnly = false;
        RecipeFilterPipeline.ResourceTypeChecker resourceTypeChecker = null;
        switch (affordabilityMode) {
            case ALL -> { checker = null; affordableOnly = false; }
            case INVENTORY_DRIVEN -> {
                if (container != null) {
                    final var inv = container;
                    checker = recipe -> isAffordable(recipe, inv);
                }
                affordableOnly = true;
            }
            case RESOURCE_DRIVEN -> {
                if (ingredientController != null) {
                    resourceTypeChecker = ingredientController.getFilterPredicate();
                    if (resourceTypeChecker != null) {
                        affordableOnly = true;
                    }
                }
            }
        }

        // Convert allRecipes to InputRecipe list
        List<RecipeFilterPipeline.InputRecipe> inputs = new ArrayList<>();
        for (RecipeEntry entry : allRecipes) {
            inputs.add(new RecipeFilterPipeline.InputRecipe(
                    entry.recipeId(), entry.outputItemId(), entry.blockTypeId(),
                    entry.benchId(), entry.set(), entry.categoryIds()));
        }

        // Execute pipeline
        Set<String> effectiveSets = selectAllSets ? Set.of() : activeSetFilters;
        Set<String> effectiveGroups = selectAllCategories ? Set.of() : activeMaterialGroups;
        RecipeFilterPipeline.PipelineResult result = pipeline.execute(
                inputs, activeTab, effectiveGroups, effectiveSets, searchQuery, checker,
                affordableOnly, categoryInfoMap, resourceTypeChecker);

        this.displayedRecipes = result.displayedRecipes();
        this.currentSets = result.currentSets();
        this.currentGroups = result.currentGroups();
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
        this.activeMaterialGroups.clear();
        this.activeMaterialGroups.addAll(prefs.activeMaterialGroups);
        this.affordabilityMode = AffordabilityMode.fromString(prefs.affordabilityMode);
        this.searchQuery = prefs.searchQuery != null ? prefs.searchQuery : "";
        this.selectedRecipeId = prefs.selectedRecipeId;
        this.selectAllSets = prefs.selectAllSets;
        this.selectAllCategories = prefs.selectAllCategories;
        // Ingredient filter selections restored after tree is built (see loadRecipes)

        loadRecipes();

        // ── Compute data-driven layout ──
        MaxLayoutInfo maxLayout = computeMaxLayout();
        this.totalSetCount = maxLayout.setCount();
        this.maxLayoutSetNames = maxLayout.setNames().clone();
        this.cellsPerSet = maxLayout.recipesPerSet().clone();
        this.totalCellCount = maxLayout.totalCells();

        // Build prefix-sum offset array
        this.groupCellOffset = new int[totalSetCount];
        for (int i = 1; i < totalSetCount; i++) {
            groupCellOffset[i] = groupCellOffset[i - 1] + cellsPerSet[i - 1];
        }

        // Build set name → group index lookup (case-insensitive)
        this.setNameToGroupIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < totalSetCount; i++) {
            setNameToGroupIndex.put(maxLayoutSetNames[i], i);
        }

        // Allocate indirection map
        this.cellSlotToRecipeIndex = new int[totalCellCount];

        // Load main template
        cmd.append("Pages/BlueprintBench/BlueprintBenchPage.ui");

        // ── Append reusable components into empty containers (one-time init) ──

        // Set filter buttons — one per set
        for (int i = 0; i < totalSetCount; i++) {
            cmd.append("#SetFilters", "Pages/BlueprintBench/SetFilterButton.ui");
        }

        // Material group icon buttons (keep MAX_GROUP_BUTTONS)
        for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
            cmd.append("#MaterialGroups", "Pages/BlueprintBench/GroupFilterButton.ui");
        }

        // Per-set group containers with VARIABLE cell counts
        for (int g = 0; g < totalSetCount; g++) {
            cmd.append("#RecipeGridArea", "Pages/BlueprintBench/SetGroupContainer.ui");
            for (int c = 0; c < cellsPerSet[g]; c++) {
                cmd.append("#RecipeGridArea[" + g + "] #GroupCells",
                           "Pages/BlueprintBench/RecipeIconCell.ui");
            }
        }

        // Cost cells (unchanged)
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            cmd.append("#CostGrid", "Pages/BlueprintBench/CostCell.ui");
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

        // Section expand/collapse headers
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#CategoriesHeader",
                EventData.of("Action", "ToggleCategories")
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#SetsHeader",
                EventData.of("Action", "ToggleSets")
        );

        // Give Blueprint button
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#GetPlaceholderBtn",
                EventData.of("Action", "GiveBlueprint")
        );

        buildBenchTabs(evt);
        buildSetFilterBindings(evt);
        buildMaterialGroupBindings(evt);
        buildRecipeGridBindings(evt);

        // Ingredient tree grid — dynamically appended
        if (ingredientController != null) {
            ingredientController.buildUI(cmd, evt);
            // Restore saved selections
            BlueprintBenchPrefs savedPrefs = BlueprintBenchPrefsStore.load(this.playerRef.getUuid());
            if (savedPrefs.selectedIngredientNodes != null && !savedPrefs.selectedIngredientNodes.isEmpty()) {
                ingredientController.restoreSelection(savedPrefs.selectedIngredientNodes);
            }
        }

        // Clear ingredients button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearIngredientsBtn",
                EventData.of("Action", "ClearIngredients"));

        // ── Set initial state ──
        updateAffordabilityToggle(cmd);

        updateBenchTabs(cmd);
        updateMaterialGroups(cmd);
        updateSetFilters(cmd);
        updateRecipeGrid(cmd);
        updateDetailPanel(cmd);
    }

    @Override
    public void onDismiss(@NonNull Ref<EntityStore> ref, @NonNull Store<EntityStore> store) {
        savePrefs();

        // Clear tooltip-triggering data so any visible tooltip is dismissed.
        // The engine's tooltip overlay is independent of the page lifecycle,
        // so we must explicitly remove the data that drives tooltips.
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#OutputIcon.ItemId", "");
        for (int g = 0; g < totalSetCount; g++) {
            cmd.set("#RecipeGridArea[" + g + "].Visible", false);
        }
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            cmd.set("#CostGrid[" + i + "].Visible", false);
        }

        sendUpdate(cmd, null, false);
    }

    private void savePrefs() {
        BlueprintBenchPrefs prefs = new BlueprintBenchPrefs();
        prefs.activeTab = this.activeTab;
        prefs.activeSetFilters = new ArrayList<>(this.activeSetFilters);
        prefs.activeMaterialGroups = new ArrayList<>(this.activeMaterialGroups);
        prefs.affordabilityMode = this.affordabilityMode.name();
        prefs.searchQuery = this.searchQuery;
        prefs.selectedRecipeId = this.selectedRecipeId;
        prefs.selectAllSets = this.selectAllSets;
        prefs.selectAllCategories = this.selectAllCategories;
        prefs.selectedIngredientNodes = ingredientController != null
                ? new ArrayList<>(ingredientController.getSelectedNodeIds()) : new ArrayList<>();
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
                this.selectAllSets = false;
                this.selectAllCategories = false;
                this.activeSetFilters.clear();
                this.activeMaterialGroups.clear();
                this.searchQuery = "";
                this.selectedRecipeId = null;
                applyFilter();
                cmd.set("#SearchInput.Value", "");
                updateBenchTabs(cmd);
                updateMaterialGroups(cmd);
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
                // Clear all set filters
                selectAllSets = false;
                activeSetFilters.clear();
            } else if (filterPayload.startsWith("idx:")) {
                int idx = -1;
                try {
                    idx = Integer.parseInt(filterPayload.substring(4));
                } catch (NumberFormatException ignored) {}
                if (idx >= 0 && idx < currentSets.size()) {
                    String setName = currentSets.get(idx);
                    if (selectAllSets) {
                        // Transitioning from Select All to individual: populate all EXCEPT toggled
                        selectAllSets = false;
                        activeSetFilters.clear();
                        activeSetFilters.addAll(currentSets);
                        activeSetFilters.remove(setName);
                    } else {
                        // Normal toggle
                        if (activeSetFilters.contains(setName)) {
                            activeSetFilters.remove(setName);
                        } else {
                            activeSetFilters.add(setName);
                        }
                    }
                }
            }
            this.selectedRecipeId = null;
            applyFilter();
            pruneInvalidMaterialGroups();
            updateSetFilters(cmd);
            updateMaterialGroups(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("MaterialGroup:")) {
            String payload = data.action.substring("MaterialGroup:".length());
            if ("All".equals(payload)) {
                // Clear all category filters
                selectAllCategories = false;
                activeMaterialGroups.clear();
            } else if (payload.startsWith("idx:")) {
                int idx = -1;
                try { idx = Integer.parseInt(payload.substring(4)); } catch (Exception ignored) {}
                if (idx >= 0 && idx < currentGroups.size()) {
                    String groupName = currentGroups.get(idx).categoryId();
                    if (selectAllCategories) {
                        // Transitioning from Select All to individual: populate all EXCEPT toggled
                        selectAllCategories = false;
                        activeMaterialGroups.clear();
                        for (RecipeFilterPipeline.MaterialGroup g : currentGroups) {
                            activeMaterialGroups.add(g.categoryId());
                        }
                        activeMaterialGroups.remove(groupName);
                    } else {
                        // Normal toggle
                        if (activeMaterialGroups.contains(groupName)) {
                            activeMaterialGroups.remove(groupName);
                        } else {
                            activeMaterialGroups.add(groupName);
                        }
                    }
                }
            }
            pruneIncompatibleSetFilters();
            this.selectedRecipeId = null;
            applyFilter();
            updateMaterialGroups(cmd);
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim();
            applyFilter();
            pruneInvalidMaterialGroups();
            this.selectedRecipeId = null;
            updateBenchTabs(cmd);
            updateMaterialGroups(cmd);
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if ("ToggleAffordable".equals(data.action)) {
            this.affordabilityMode = this.affordabilityMode.next();
            applyFilter();
            // Prune stale categories that no longer exist after affordability change
            pruneInvalidMaterialGroups();
            updateAffordabilityToggle(cmd);
            if (ingredientController != null) {
                ingredientController.updateUI(cmd);
            }
            updateMaterialGroups(cmd);
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);


        } else if ("ToggleCategories".equals(data.action)) {
            this.categoriesExpanded = !this.categoriesExpanded;
            cmd.set("#CategoriesHeader.Text", (categoriesExpanded ? "v " : "> ") + "Categories");
            cmd.set("#MaterialGroups.Visible", categoriesExpanded);
            sendUpdate(cmd, null, false);

        } else if ("ToggleSets".equals(data.action)) {
            this.setsExpanded = !this.setsExpanded;
            cmd.set("#SetsHeader.Text", (setsExpanded ? "v " : "> ") + "Sets");
            cmd.set("#SetFilters.Visible", setsExpanded);
            sendUpdate(cmd, null, false);

        } else if (data.action != null && (data.action.startsWith("IngredientCheckbox:") 
                || data.action.startsWith("IngredientExpand:") 
                || data.action.startsWith("IngredientToggle:"))) {
            if (ingredientController != null) {
                ingredientController.handleEvent(data.action);
                applyFilter();
                pruneInvalidMaterialGroups();
                ingredientController.updateUI(cmd);
                updateMaterialGroups(cmd);
                updateSetFilters(cmd);
                updateRecipeGrid(cmd);
                updateDetailPanel(cmd);
                savePrefs();
            }
            sendUpdate(cmd, null, false);

        } else if ("ClearIngredients".equals(data.action)) {
            if (ingredientController != null) {
                ingredientController.clearAll();
                applyFilter();
                pruneInvalidMaterialGroups();
                ingredientController.updateUI(cmd);
                updateMaterialGroups(cmd);
                updateSetFilters(cmd);
                updateRecipeGrid(cmd);
                updateDetailPanel(cmd);
                savePrefs();
            }
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("RecipeSelect:idx:")) {
            int slotIdx = -1;
            try { slotIdx = Integer.parseInt(data.action.substring("RecipeSelect:idx:".length())); } catch (NumberFormatException ignored) {}
            if (slotIdx >= 0 && slotIdx < totalCellCount) {
                int recipeIdx = cellSlotToRecipeIndex[slotIdx];
                if (recipeIdx >= 0 && recipeIdx < displayedRecipes.size()) {
                    RecipeFilterPipeline.TaggedRecipe entry = displayedRecipes.get(recipeIdx);
                    this.selectedRecipeId = entry.recipeId();
                    updateRecipeGrid(cmd);
                    updateDetailPanel(cmd);
                    savePrefs();
                }
            }
            sendUpdate(cmd, null, false);

        } else if ("GiveBlueprint".equals(data.action)) {
            giveSelectedBlueprint(store, ref, cmd);
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
        // "Clear Filters" trash icon button in the section header row
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#ClearSetsBtn",
                EventData.of("Action", "SetFilter:" + ALL_FILTER)
        );
        // Per-set filter buttons (indices 0..totalSetCount-1)
        for (int i = 0; i < totalSetCount; i++) {
            String idx = String.valueOf(i);
            EventData action = EventData.of("Action", "SetFilter:idx:" + i);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating, "#SetFilters[" + idx + "] #Btn",
                    action
            );
            // Bind checkbox so toggling it fires the same action as the button
            evt.addEventBinding(
                    CustomUIEventBindingType.ValueChanged, "#SetFilters[" + idx + "] #Check",
                    action
            );
        }
    }

    private void updateSetFilters(UICommandBuilder cmd) {
        // Per-set filter buttons (indices 0..totalSetCount-1)
        for (int i = 0; i < totalSetCount; i++) {
            String sel = "#SetFilters[" + i + "]";
            if (i < currentSets.size()) {
                String setName = currentSets.get(i);
                String label = RecipeFilterPipeline.setDisplayLabel(setName);
                boolean checked = selectAllSets || activeSetFilters.contains(setName);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + " #Btn.Text", label);
                cmd.set(sel + " #Btn.Style", checked ? FILTER_ACTIVE : FILTER_INACTIVE);
                cmd.set(sel + " #Check.Value", checked);
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }
    }

    private void buildRecipeGridBindings(UIEventBuilder evt) {
        for (int g = 0; g < totalSetCount; g++) {
            for (int c = 0; c < cellsPerSet[g]; c++) {
                int flatIdx = groupCellOffset[g] + c;
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        "#RecipeGridArea[" + g + "] #GroupCells[" + c + "] #CellBtn",
                        EventData.of("Action", "RecipeSelect:idx:" + flatIdx));
            }
        }
    }

    private void updateRecipeGrid(UICommandBuilder cmd) {
        // Reset indirection map
        Arrays.fill(cellSlotToRecipeIndex, -1);

        // Track which groups are used this frame
        boolean[] groupUsed = new boolean[totalSetCount];
        // Track how many cells were filled per group (for hiding remaining)
        int[] cellsFilled = new int[totalSetCount];

        // Walk displayedRecipes (sorted by effectiveSet) and detect set boundaries
        int currentGroupIdx = -1;
        String currentSet = null;

        for (int recipeIdx = 0; recipeIdx < displayedRecipes.size(); recipeIdx++) {
            RecipeFilterPipeline.TaggedRecipe entry = displayedRecipes.get(recipeIdx);

            // Set boundary → look up the fixed group slot for this set
            if (!entry.effectiveSet().equals(currentSet)) {
                currentSet = entry.effectiveSet();
                Integer mappedIdx = setNameToGroupIndex.get(currentSet);
                if (mappedIdx == null) continue; // unknown set — skip
                currentGroupIdx = mappedIdx;
                groupUsed[currentGroupIdx] = true;

                // Show group and set its label
                String groupSel = "#RecipeGridArea[" + currentGroupIdx + "]";
                cmd.set(groupSel + ".Visible", true);
                cmd.set(groupSel + " #SetGroupLabel.Text",
                        RecipeFilterPipeline.setDisplayLabel(currentSet));
            }

            if (currentGroupIdx < 0) continue;

            int cellInGroup = cellsFilled[currentGroupIdx];

            // Overflow within group — skip recipe (no cell slot available)
            if (cellInGroup >= cellsPerSet[currentGroupIdx]) continue;

            // Populate cell
            int globalIdx = groupCellOffset[currentGroupIdx] + cellInGroup;
            String cellSel = "#RecipeGridArea[" + currentGroupIdx + "] #GroupCells[" + cellInGroup + "]";
            cmd.set(cellSel + ".Visible", true);
            cmd.set(cellSel + " #CellIcon.ItemId", entry.outputItemId());
            cmd.set(cellSel + " #CellDim.Visible", !entry.affordable());
            boolean isSelected = entry.recipeId().equals(selectedRecipeId);
            cmd.set(cellSel + " #CellBtn.Style", isSelected ? CELL_SELECTED_STYLE : CELL_UNSELECTED_STYLE);

            cellSlotToRecipeIndex[globalIdx] = recipeIdx;
            cellsFilled[currentGroupIdx]++;
        }

        // Hide remaining cells in used groups and hide all unused groups
        for (int g = 0; g < totalSetCount; g++) {
            if (groupUsed[g]) {
                hideRemainingCells(cmd, g, cellsFilled[g]);
            } else {
                cmd.set("#RecipeGridArea[" + g + "].Visible", false);
            }
        }
    }

    /** Hide cells [startCell..cellsPerSet[groupIdx]) in the given group. */
    private void hideRemainingCells(UICommandBuilder cmd, int groupIdx, int startCell) {
        for (int c = startCell; c < cellsPerSet[groupIdx]; c++) {
            String cellSel = "#RecipeGridArea[" + groupIdx + "] #GroupCells[" + c + "]";
            cmd.set(cellSel + ".Visible", false);
            cmd.set(cellSel + " #CellBtn.Style", CELL_UNSELECTED_STYLE);
        }
    }

    private void updateDetailPanel(UICommandBuilder cmd) {
        if (selectedRecipeId != null) {
            RecipeEntry entry = findEntry(selectedRecipeId);
            if (entry != null) {
                cmd.set("#OutputIcon.ItemId", entry.outputItemId);
                cmd.set("#OutputName.Text", entry.blockTypeId != null
                        ? entry.blockTypeId.replace('_', ' ') : entry.outputItemId);

                // Get inventory container for per-ingredient checks
                boolean checkInventory = (affordabilityMode == AffordabilityMode.INVENTORY_DRIVEN);
                Player player = playerStore != null
                        ? playerStore.getComponent(playerRef_ref, Player.getComponentType()) : null;
                CombinedItemContainer container = player != null
                        ? player.getInventory().getCombinedBackpackStorageHotbar() : null;

                boolean allAffordable = true;
                int costIdx = 0;
                try {
                    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                    if (recipe != null) {
                        FilteredRecipeEntry fe = RecipeFilterRegistry.getEntry(entry.recipeId);
                        BenchCategory category = fe != null ? fe.benchCategory() : BenchCategory.BUILDERS_ONLY;

                        List<ResolvedIngredient> ingredients =
                            RecipeAffordabilityResolver.resolveIngredientCosts(recipe, category, container);

                        if (!ingredients.isEmpty()) {
                            for (ResolvedIngredient ing : ingredients) {
                                if (costIdx >= MAX_COST_CELLS) break;
                                String sel = "#CostGrid[" + costIdx + "]";
                                String itemId = ing.resolvedItemId();
                                int requiredQty = ing.requiredQty();
                                boolean sufficient = ing.sufficient();
                                if (!sufficient) allAffordable = false;

                                cmd.set(sel + ".Visible", true);
                                cmd.set(sel + " #CostIcon.ItemId", itemId);
                                cmd.set(sel + " #CostQty.Text", "x" + requiredQty);
                                if (checkInventory) {
                                    cmd.set(sel + " #CostDim.Visible", !sufficient);
                                    cmd.set(sel + " #CostQty.Style", sufficient ? COST_QTY_NORMAL : COST_QTY_INSUFFICIENT);
                                } else {
                                    cmd.set(sel + " #CostDim.Visible", false);
                                    cmd.set(sel + " #CostQty.Style", COST_QTY_NORMAL);
                                }
                                costIdx++;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("[BlueprintUI] Error populating cost grid: " + e.getMessage());
                }

                // Hide remaining cost cells and reset their state
                for (int i = costIdx; i < MAX_COST_CELLS; i++) {
                    String sel = "#CostGrid[" + i + "]";
                    cmd.set(sel + ".Visible", false);
                    cmd.set(sel + " #CostDim.Visible", false);
                    cmd.set(sel + " #CostQty.Style", COST_QTY_NORMAL);
                }

                // Output frame state — affordable vs unaffordable
                if (checkInventory) {
                    cmd.set("#OutputFrame.Background", allAffordable ? OUTPUT_BG_NORMAL : OUTPUT_BG_UNAFFORDABLE);
                    cmd.set("#OutputDim.Visible", !allAffordable);
                    cmd.set("#OutputName.Style", allAffordable ? DETAIL_LABEL_NORMAL : DETAIL_LABEL_MUTED);
                } else {
                    cmd.set("#OutputFrame.Background", OUTPUT_BG_NORMAL);
                    cmd.set("#OutputDim.Visible", false);
                    cmd.set("#OutputName.Style", DETAIL_LABEL_NORMAL);
                }
                return;
            }
        }
        // No recipe selected — empty state
        cmd.set("#OutputIcon.ItemId", "");
        cmd.set("#OutputName.Text", "No recipe selected");
        cmd.set("#OutputName.Style", DETAIL_LABEL_MUTED);
        cmd.set("#OutputFrame.Background", OUTPUT_BG_EMPTY);
        cmd.set("#OutputDim.Visible", false);
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            String sel = "#CostGrid[" + i + "]";
            cmd.set(sel + ".Visible", false);
            cmd.set(sel + " #CostDim.Visible", false);
            cmd.set(sel + " #CostQty.Style", COST_QTY_NORMAL);
        }
    }

    private void updateAffordabilityToggle(UICommandBuilder cmd) {
        cmd.set("#AffordableToggle.Text", affordabilityMode.label());
        cmd.set("#AffordableToggle.Style",
                affordabilityMode != AffordabilityMode.ALL ? FILTER_ACTIVE : FILTER_INACTIVE);
    }


    private void buildMaterialGroupBindings(UIEventBuilder evt) {
        // "Clear Filters" text button above the icon grid
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#ClearCategoriesBtn",
                EventData.of("Action", "MaterialGroup:All")
        );
        // Per-group icon buttons (indices 0..MAX_GROUP_BUTTONS-1)
        for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating, "#MaterialGroups[" + i + "] #GroupBtn",
                    EventData.of("Action", "MaterialGroup:idx:" + i)
            );
        }
    }

    private void updateMaterialGroups(UICommandBuilder cmd) {
        // Per-group icon buttons (indices 0..N-1)
        for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
            String sel = "#MaterialGroups[" + i + "]";
            if (i < currentGroups.size()) {
                RecipeFilterPipeline.MaterialGroup group = currentGroups.get(i);
                boolean active = selectAllCategories || activeMaterialGroups.contains(group.categoryId());
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + ".TooltipText", group.displayName());
                cmd.set(sel + " #ActiveOverlay.Visible", active);
                // Set icon
                String iconFile = group.iconPath();
                if (iconFile != null && iconFile.contains("/")) {
                    iconFile = iconFile.substring(iconFile.lastIndexOf('/') + 1);
                }
                if (iconFile != null && !iconFile.isEmpty()) {
                    cmd.set(sel + " #GroupIcon.Background", "Common/GroupIcons/" + iconFile);
                }
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }
    }

    private void pruneIncompatibleSetFilters() {
        if (selectAllSets || activeMaterialGroups.isEmpty()) return;
        // A set filter is compatible if any recipe with that set also has an active category
        activeSetFilters.removeIf(setName -> {
            for (RecipeEntry entry : allRecipes) {
                if (setName.equalsIgnoreCase(entry.set()) && entry.categoryIds() != null) {
                    for (String catId : entry.categoryIds()) {
                        if (activeMaterialGroups.contains(catId)) return false; // compatible
                    }
                }
            }
            return true; // no recipe with this set has an active category
        });
    }

    private void pruneInvalidMaterialGroups() {
        if (selectAllCategories || activeMaterialGroups.isEmpty()) return;
        Set<String> validCats = new HashSet<>();
        for (RecipeFilterPipeline.MaterialGroup g : currentGroups) {
            validCats.add(g.categoryId());
        }
        if (activeMaterialGroups.removeIf(c -> !validCats.contains(c))) {
            applyFilter(); // re-run pipeline with pruned selection
        }
    }

    private Map<String, RecipeFilterPipeline.CategoryInfo> buildCategoryInfoMap() {
        Map<String, ItemCategory> allCats = ItemCategory.getAssetMap().getAssetMap();

        // Item.getCategories() returns dot-notation strings like "Blocks.Metal",
        // "Furniture.Beds", but ItemCategory.getId() returns simple IDs like "Metal".
        // Build the map with dot-notation keys to match recipe category IDs.
        Map<String, RecipeFilterPipeline.CategoryInfo> map = new LinkedHashMap<>();
        for (ItemCategory topLevel : allCats.values()) {
            LOGGER.info("[BlueprintBench] Top-level category: id=" + topLevel.getId()
                    + " name=" + topLevel.getName() + " icon=" + topLevel.getIcon()
                    + " order=" + topLevel.getOrder());
            // Top-level entries (e.g. "Blocks", "Items", "Furniture")
            map.put(topLevel.getId(), new RecipeFilterPipeline.CategoryInfo(
                    topLevel.getId(), capitalize(topLevel.getId()), topLevel.getIcon(), topLevel.getOrder()));

            // Child entries with dot-notation keys (e.g. "Blocks.Metal", "Furniture.Beds")
            ItemCategory[] children = topLevel.getChildren();
            if (children != null) {
                for (ItemCategory child : children) {
                    String dotKey = topLevel.getId() + "." + child.getId();
                    LOGGER.info("[BlueprintBench]   Child category: dotKey=" + dotKey
                            + " name=" + child.getName() + " icon=" + child.getIcon()
                            + " order=" + child.getOrder());
                    map.put(dotKey, new RecipeFilterPipeline.CategoryInfo(
                            dotKey, capitalize(child.getId()), child.getIcon(), child.getOrder()));
                }
            }
        }
        LOGGER.info("[BlueprintBench] Built categoryInfoMap with " + map.size() + " categories: " + map.keySet());
        return map;
    }

    private static String capitalize(String id) {
        if (id == null || id.isEmpty()) return id;
        // "TechnicalBlocks" → "Technical Blocks", "Beds" → "Beds"
        String spaced = id.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private void giveSelectedBlueprint(Store<EntityStore> store, Ref<EntityStore> ref,
                                  UICommandBuilder cmd) {
        if (selectedRecipeId == null) {
            this.playerRef.sendMessage(
                    Message.raw("\u00a7c[BlueprintBench] No recipe selected."));
            return;
        }

        RecipeEntry entry = findEntry(selectedRecipeId);
        if (entry == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemStack item = StencilMetadata.createStencil(entry.outputItemId(), entry.recipeId());
        player.getInventory().getCombinedHotbarFirst().addItemStack(item);

        this.playerRef.sendMessage(
                Message.raw("\u00a7a[BlueprintBench] Given stencil: " + entry.outputItemId().replace('_', ' ')));
        LOGGER.info("[BlueprintUI] Gave player stencil for " + entry.outputItemId() + " (recipe: " + entry.recipeId() + ")");
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
                               String benchId, String set,
                               List<String> categoryIds) {}

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
