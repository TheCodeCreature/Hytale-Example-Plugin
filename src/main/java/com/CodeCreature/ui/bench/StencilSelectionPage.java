package com.CodeCreature.ui.bench;

/**
 * @node    StencilSelectionPage
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Drives stencil recipe browsing/filtering and affordability visualization while routing
 *          primary affordability semantics through CraftingAffordabilityFacade.
 * @wave    5 (surface parity + regression hardening)
 * @status  Wave 5 - retained and isolated block-group affordability fallback as intentional UI-only path
 * @do-not  Treat local fallback checks as the parity source-of-truth for direct recipe affordability.
 *          Route direct recipe affordability semantics around generic matching through facade/resolver boundaries.
 */

import com.CodeCreature.crafting.CraftingAffordabilityFacade;
import com.CodeCreature.ui.common.IconPathResolver;
import com.CodeCreature.ui.ingredienttree.IngredientTree;
import com.CodeCreature.ui.ingredienttree.IngredientTreeBuilder;
import com.CodeCreature.ui.ingredienttree.IngredientTreeGridController;
import com.CodeCreature.registry.FilteredRecipeEntry;
import com.CodeCreature.registry.BenchRegistry;
import com.CodeCreature.registry.BenchTabGrouper;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;
import java.util.logging.Level;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory;

public class StencilSelectionPage extends InteractiveCustomUIPage<StencilSelectionPage.EventPayload> {

    private static final int MAX_GROUP_BUTTONS = 30;
    private static final int MAX_BENCH_TABS = 16;
    private static final int GRID_CELLS_PER_ROW = 12;
    private static final int MAX_TILES_PER_ROW = 12;
    private static final Value<String> FILTER_ACTIVE =
            Value.ref("Styles/Buttons.ui", "FilterActiveStyle");
    private static final Value<String> FILTER_INACTIVE =
            Value.ref("Styles/Buttons.ui", "FilterInactiveStyle");
    private static final Value<String> GIVE_BTN_PRIMARY =
            Value.ref("Styles/Buttons.ui", "WrapPrimaryButtonStyle");
        private static final Value<String> GIVE_STATUS_SUCCESS =
            Value.ref("Styles/Labels.ui", "StatusSuccessStyle");
        private static final Value<String> GIVE_STATUS_ERROR =
            Value.ref("Styles/Labels.ui", "StatusErrorStyle");

    private static final String ALL_TAB = "All";
    private static final String ALL_FILTER = "All";

    private enum FilterSelectionState {
        NONE,
        SOME,
        ALL
    }

    private final RecipeFilterPipeline pipeline = new RecipeFilterPipeline();
    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private final List<String> benchIds = new ArrayList<>();  // sorted bench IDs
    private String searchQuery = "";
    private String selectedRecipeId;
    private String activeTab = ALL_TAB;
    private final Set<String> activeSetFilters = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<String> ignoredSetFilters = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private AffordabilityMode affordabilityMode = AffordabilityMode.INVENTORY_DRIVEN;
    private IngredientTree ingredientTree;
    private IngredientTreeGridController ingredientController;
    private boolean categoriesExpanded = true;
    private boolean setsExpanded = true;
    private boolean ignoredSetsExpanded = true;
    private boolean selectAllSets = false;
    private boolean selectAllCategories = false;
    private final Set<String> activeMaterialGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private List<RecipeFilterPipeline.MaterialGroup> currentGroups = new ArrayList<>();
    private List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes = new ArrayList<>();
    private List<String> currentSets = new ArrayList<>();  // sets for active tab
    private List<String> currentIgnoredSets = new ArrayList<>();
    private List<RecipeFilterPipeline.InputRecipe> cachedInputs = List.of();

    /** Computed at build time from unfiltered pipeline output. */
    private int totalSetCount;
    private String[] maxLayoutSetNames; // set name for each group slot, indexed 0..totalSetCount-1
    private int[] cellsPerSet;         // recipe count per set, indexed 0..totalSetCount-1
    private int totalCellCount;        // sum of all cellsPerSet
    private int totalRecipeRows;
    private int totalGridRows;
    private GridLayoutController gridController;
    private DetailPanelController detailController;
    private Map<String, RecipeFilterPipeline.CategoryInfo> categoryInfoMap = Map.of();
    private final AtomicLong giveStencilFeedbackSeq = new AtomicLong();

    private Ref<EntityStore> playerRef_ref;
    private Store<EntityStore> playerStore;

    public StencilSelectionPage(@NonNull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
    }

    private record MaxLayoutInfo(int setCount, String[] setNames, int[] recipesPerSet, int totalCells) {}

    private MaxLayoutInfo computeMaxLayoutFromInputs(List<RecipeFilterPipeline.InputRecipe> inputs) {
        // Group by effectiveSet (normalize null/empty to Uncategorized), count per set
        Map<String, Integer> setCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (RecipeFilterPipeline.InputRecipe input : inputs) {
            String effectiveSet = (input.set() != null && !input.set().isEmpty())
                    ? input.set() : RecipeFilterPipeline.UNCATEGORIZED_SET;
            setCounts.merge(effectiveSet, 1, Integer::sum);
        }

        String[] setNames = setCounts.keySet().toArray(new String[0]);
        int[] recipesPerSet = new int[setNames.length];
        int totalCells = 0;
        for (int i = 0; i < setNames.length; i++) {
            recipesPerSet[i] = setCounts.get(setNames[i]);
            totalCells += recipesPerSet[i];
        }

        return new MaxLayoutInfo(setNames.length, setNames, recipesPerSet, totalCells);
    }

    private void loadRecipes() {
        allRecipes.clear();

        BenchTabGrouper grouper = BenchRegistry.getTabGrouper();
        // Debug: log grouper state
        DebugLogger.log(STENCIL_BOOK, Level.FINE, () ->
            "[Stencil Crafting][BenchTabs] Grouper tab IDs: " + grouper.getOrderedTabIds());

        Map<String, Integer> tabKeyCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FilteredRecipeEntry fe : RecipeFilterRegistry.getAllEntries()) {
            // Resolve ALL bench IDs through grouper into a set of group keys
            Set<String> resolvedKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String rawId : fe.benchIds()) {
                resolvedKeys.add(grouper.resolveTabId(rawId));
            }

            // Debug: log when any raw bench ID resolved to a different group key
            for (String rawId : fe.benchIds()) {
                String resolved = grouper.resolveTabId(rawId);
                if (!rawId.equals(resolved)) {
                    DebugLogger.log(STENCIL_BOOK, Level.FINE, () ->
                            "[Stencil Crafting][BenchTabs] Grouped: '" + rawId + "' -> '" + resolved +
                            "' (recipe: " + fe.recipeId() + ")");
                }
            }
            // Count this recipe once per resolved group key
            for (String key : resolvedKeys) {
                tabKeyCounts.merge(key, 1, Integer::sum);
            }

                String searchableName = resolveSearchableName(fe.outputItemId(), fe.blockTypeId());
                String searchableDescription = resolveSearchableDescription(fe.outputItemId());

                allRecipes.add(new RecipeEntry(fe.recipeId(), fe.outputItemId(), fe.blockTypeId(),
                    resolvedKeys, fe.set(), fe.categoryIds(), searchableName, searchableDescription));
        }
        allRecipes.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.recipeId, b.recipeId));

        // Build cached InputRecipe list (used by applyFilter and computeMaxLayoutFromInputs)
        List<RecipeFilterPipeline.InputRecipe> inputs = new ArrayList<>(allRecipes.size());
        for (RecipeEntry entry : allRecipes) {
            inputs.add(new RecipeFilterPipeline.InputRecipe(
                    entry.recipeId(), entry.outputItemId(), entry.blockTypeId(),
                    entry.benchIds(), entry.set(), entry.categoryIds(),
                    entry.searchableName(), entry.searchableDescription()));
        }
        this.cachedInputs = Collections.unmodifiableList(inputs);

        // Build tab list from recipes that actually exist, then remove excluded tabs
        Set<String> tabKeySet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (RecipeEntry r : allRecipes) {
            tabKeySet.addAll(r.benchIds());
        }
        tabKeySet.removeIf(grouper::isTabExcluded);
        benchIds.clear();
        benchIds.addAll(tabKeySet);
        DebugLogger.log(STENCIL_BOOK, Level.FINE, () ->
            "[Stencil Crafting][BenchTabs] Tab recipe counts: " + tabKeyCounts);
        DebugLogger.log(STENCIL_BOOK, Level.INFO, () -> "[StencilBook] Loaded bench IDs: " + benchIds);

        // Debug: log category coverage
        long withCats = allRecipes.stream().filter(r -> r.categoryIds() != null && !r.categoryIds().isEmpty()).count();
        DebugLogger.log(STENCIL_BOOK, Level.FINE, () -> "[StencilBook] Recipes with categories: " + withCats + "/" + allRecipes.size());
        // Debug: log distinct category IDs from recipes
        Set<String> recipeCatIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (RecipeEntry r : allRecipes) {
            if (r.categoryIds() != null) recipeCatIds.addAll(r.categoryIds());
        }
        DebugLogger.log(STENCIL_BOOK, Level.FINE, () -> "[StencilBook] Distinct recipe category IDs: " + recipeCatIds);

        this.categoryInfoMap = buildCategoryInfoMap();

        // Build ingredient tree from all recipes
        List<CraftingRecipe> craftingRecipes = new ArrayList<>();
        for (RecipeEntry entry : allRecipes) {
            CraftingRecipe cr = CraftingRecipe.getAssetMap().getAsset(entry.recipeId());
            if (cr != null) craftingRecipes.add(cr);
        }
        this.ingredientTree = IngredientTreeBuilder.build(craftingRecipes);
        this.ingredientController = new IngredientTreeGridController(ingredientTree);
    }

    /**
     * Delegates to {@link RecipeFilterPipeline#execute} and stores the result
     * in {@link #displayedRecipes} and {@link #currentSets}.
     */
    private void applyFilter() {
        List<RecipeFilterPipeline.InputRecipe> effectiveInputs = getInputsExcludingIgnoredSets();

        // Get player inventory for affordability checks
        Player filterPlayer = playerStore != null
                ? playerStore.getComponent(playerRef_ref, Player.getComponentType()) : null;
        boolean bypassAffordabilityChecks = shouldBypassAffordabilityChecks(filterPlayer);
        var container = filterPlayer != null
            ? filterPlayer.getInventory().getCombinedHotbarFirst() : null;

        // Build affordability checker â€” null when disabled or no inventory
        RecipeFilterPipeline.AffordabilityChecker checker = null;
        boolean affordableOnly = false;
        RecipeFilterPipeline.ResourceTypeChecker resourceTypeChecker = null;
        switch (affordabilityMode) {
            case INVENTORY_DRIVEN -> {
                if (bypassAffordabilityChecks) {
                    checker = recipe -> true;
                } else if (container != null) {
                    final var inv = container;
                    checker = recipe -> isAffordable(recipe, inv);
                }
                affordableOnly = true;
            }
            case RESOURCE_PLANNING -> {
                if (ingredientController != null) {
                    resourceTypeChecker = ingredientController.getFilterPredicate();
                    if (resourceTypeChecker != null) {
                        affordableOnly = true;
                    }
                }
            }
        }

        // Execute pipeline
        Set<String> effectiveSets = selectAllSets ? Set.of() : activeSetFilters;
        Set<String> effectiveGroups = selectAllCategories ? Set.of() : activeMaterialGroups;
        RecipeFilterPipeline.PipelineResult result = pipeline.execute(
                effectiveInputs, activeTab, effectiveGroups, effectiveSets, searchQuery, checker,
                affordableOnly, categoryInfoMap, resourceTypeChecker);

        this.displayedRecipes = result.displayedRecipes();
        this.currentSets = result.currentSets();
        this.currentGroups = result.currentGroups();
        this.currentIgnoredSets = new ArrayList<>(ignoredSetFilters);
        this.currentIgnoredSets.sort(String.CASE_INSENSITIVE_ORDER);

        normalizeSetSelectionState();
    }

    private List<RecipeFilterPipeline.InputRecipe> getInputsExcludingIgnoredSets() {
        if (ignoredSetFilters.isEmpty()) {
            return cachedInputs;
        }

        List<RecipeFilterPipeline.InputRecipe> filtered = new ArrayList<>(cachedInputs.size());
        for (RecipeFilterPipeline.InputRecipe input : cachedInputs) {
            String effectiveSet = (input.set() != null && !input.set().isEmpty())
                    ? input.set()
                    : RecipeFilterPipeline.UNCATEGORIZED_SET;
            if (!ignoredSetFilters.contains(effectiveSet)) {
                filtered.add(input);
            }
        }
        return filtered;
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        // Load persisted preferences
        StencilBookPrefs prefs = StencilBookPrefsStore.load(this.playerRef.getUuid());
        this.activeTab = prefs.activeTab != null ? prefs.activeTab : ALL_TAB;
        this.activeSetFilters.clear();
        this.activeSetFilters.addAll(prefs.activeSetFilters);
        this.ignoredSetFilters.clear();
        this.ignoredSetFilters.addAll(prefs.ignoredSetFilters);
        this.activeMaterialGroups.clear();
        this.activeMaterialGroups.addAll(prefs.activeMaterialGroups);
        this.affordabilityMode = AffordabilityMode.fromString(prefs.affordabilityMode);
        this.searchQuery = prefs.searchQuery != null ? prefs.searchQuery : "";
        this.selectedRecipeId = prefs.selectedRecipeId;
        this.selectAllSets = prefs.selectAllSets;
        this.selectAllCategories = prefs.selectAllCategories;
        // Ingredient filter selections restored after tree is built (see loadRecipes)

        loadRecipes();
        validateActiveTab();

        // ── Compute data-driven layout ──
        MaxLayoutInfo maxLayout = computeMaxLayoutFromInputs(cachedInputs);
        this.totalSetCount = maxLayout.setCount();
        this.maxLayoutSetNames = maxLayout.setNames().clone();
        this.cellsPerSet = maxLayout.recipesPerSet().clone();
        this.totalCellCount = maxLayout.totalCells();
        int totalGridCells = totalCellCount + totalSetCount;
        this.totalRecipeRows = Math.max(1, (totalGridCells + GRID_CELLS_PER_ROW - 1) / GRID_CELLS_PER_ROW);
        this.totalGridRows = totalRecipeRows;

        // Create grid layout controller
        this.gridController = new GridLayoutController(maxLayoutSetNames, cellsPerSet, MAX_TILES_PER_ROW);
        this.totalGridRows = gridController.getRowCount();

        // Create detail panel controller
        this.detailController = new DetailPanelController();

        // Load main template
        cmd.append("Pages/StencilBook/StencilBookPage.ui");

        //  Append reusable components into empty containers (one-time init) 

        // Set filter buttons â€” one per set
        for (int i = 0; i < totalSetCount; i++) {
            cmd.append("#SetFilters", "Pages/StencilBook/Components/SetFilterButton.ui");
            cmd.append("#IgnoredSetFilters", "Pages/StencilBook/Components/SetFilterButton.ui");
        }

        // Material group icon buttons (keep MAX_GROUP_BUTTONS)
        for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
            cmd.append("#MaterialGroups", "Pages/StencilBook/Components/GroupFilterButton.ui");
        }

        // Grouped recipe grid with sandbox-style row packing.
        for (int r = 0; r < gridController.getRowCount(); r++) {
            cmd.append("#RecipeGridArea", "Pages/StencilBook/Components/RecipeGridRow.ui");
            int groupCount = gridController.getGroupCountInRow(r);
            for (int g = 0; g < groupCount; g++) {
                cmd.append("#RecipeGridArea[" + r + "] #RowGroups",
                        "Pages/StencilBook/Components/SetGroupContainer.ui");
                int cellCount = gridController.getGroupCapacity(r, g);
                for (int c = 0; c < cellCount; c++) {
                    cmd.append("#RecipeGridArea[" + r + "] #RowGroups[" + g + "] #GroupCells",
                            "Pages/StencilBook/RecipeIconCell.ui");
                }
            }
        }

        // Cost grid rows (fixed) with fixed cells per row.
        for (int row = 0; row < DetailPanelController.MAX_COST_ROWS; row++) {
            cmd.append("#CostGrid", "Pages/StencilBook/Components/CostRow.ui");
            for (int col = 0; col < DetailPanelController.COST_CELLS_PER_ROW; col++) {
                cmd.append("#CostGrid[" + row + "] #CostRowCells", "Pages/StencilBook/Components/CostCell.ui");
            }
        }

        //  Bind ALL events (one-time) 

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
        evt.addEventBinding(
            CustomUIEventBindingType.Activating, "#IgnoredSetsHeader",
            EventData.of("Action", "ToggleIgnoredSets")
        );

        // Give Stencil button
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#GetPlaceholderBtn",
                EventData.of("Action", "GiveStencil")
        );

        buildBenchTabs(cmd, evt);
        buildSetFilterBindings(evt);
        buildMaterialGroupBindings(evt);
        gridController.buildBindings(evt);

        // Ingredient tree grid â€” dynamically appended
        if (ingredientController != null) {
            ingredientController.buildUI(cmd, evt);
            // Restore saved selections
            StencilBookPrefs savedPrefs = StencilBookPrefsStore.load(this.playerRef.getUuid());
            if (savedPrefs.selectedIngredientNodes != null && !savedPrefs.selectedIngredientNodes.isEmpty()) {
                ingredientController.restoreSelection(savedPrefs.selectedIngredientNodes);
                ingredientController.updateUI(cmd);
            }
        }

        // Clear ingredients button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearIngredientsBtn",
                EventData.of("Action", "ClearIngredients"));
        // Single pipeline run — after controllers exist, before UI update
        applyFilter();
        //  Set initial state 
        updateAffordabilityToggle(cmd);
        resetGiveStencilFeedback(cmd);

        updateBenchTabs(cmd);
        updateMaterialGroups(cmd);
        updateSetFilters(cmd);
        gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
        updateDetail(cmd);
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
        detailController.clearUI(cmd);

        sendUpdate(cmd, null, false);
    }

    private void savePrefs() {
        StencilBookPrefs prefs = new StencilBookPrefs();
        prefs.activeTab = this.activeTab;
        prefs.activeSetFilters = new ArrayList<>(this.activeSetFilters);
        prefs.ignoredSetFilters = new ArrayList<>(this.ignoredSetFilters);
        prefs.activeMaterialGroups = new ArrayList<>(this.activeMaterialGroups);
        prefs.affordabilityMode = this.affordabilityMode.name();
        prefs.searchQuery = this.searchQuery;
        prefs.selectedRecipeId = this.selectedRecipeId;
        prefs.selectAllSets = this.selectAllSets;
        prefs.selectAllCategories = this.selectAllCategories;
        prefs.selectedIngredientNodes = ingredientController != null
                ? new ArrayList<>(ingredientController.getSelectedNodeIds()) : new ArrayList<>();
        StencilBookPrefsStore.save(this.playerRef.getUuid(), prefs);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        UICommandBuilder cmd = new UICommandBuilder();
        // No UIEventBuilder â€” all events were bound in build()

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
                gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
                updateDetail(cmd);
            }
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("RecipeCell:")) {
            String recipeId = gridController.getRecipeIdForAction(data.action);
            if (recipeId != null && !recipeId.isEmpty()) {
                RecipeEntry entry = findEntry(recipeId);
                if (entry != null) {
                    this.selectedRecipeId = recipeId;
                    gridController.updateSelection(cmd, selectedRecipeId);
                    updateDetail(cmd);
                }
            }
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("SetFilter:")) {
            // Set filter toggle â€” index-based resolution
            String filterPayload = data.action.substring("SetFilter:".length());
            if (ALL_FILTER.equals(filterPayload)) {
                toggleAllSetsState();
            } else if (filterPayload.startsWith("idx:")) {
                int idx = Integer.parseInt(filterPayload.substring(4));
                if (idx >= 0 && idx < currentSets.size()) {
                    String setName = currentSets.get(idx);
                    toggleSetFilter(setName);
                }
            }
            this.selectedRecipeId = null;
            applyFilter();
            if (pruneInvalidMaterialGroups()) {
                applyFilter();
            }
            updateSetFilters(cmd);
            updateMaterialGroups(cmd);
            gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("IgnoreSet:idx:")) {
            int idx = Integer.parseInt(data.action.substring("IgnoreSet:idx:".length()));
            if (idx >= 0 && idx < currentSets.size()) {
                String setName = currentSets.get(idx);
                ignoreSet(setName);
            }
            this.selectedRecipeId = null;
            applyFilter();
            if (pruneInvalidMaterialGroups()) {
                applyFilter();
            }
            updateSetFilters(cmd);
            updateMaterialGroups(cmd);
            gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("RestoreIgnoredSet:idx:")) {
            int idx = Integer.parseInt(data.action.substring("RestoreIgnoredSet:idx:".length()));
            if (idx >= 0 && idx < currentIgnoredSets.size()) {
                String setName = currentIgnoredSets.get(idx);
                restoreIgnoredSet(setName);
            }
            this.selectedRecipeId = null;
            applyFilter();
            if (pruneInvalidMaterialGroups()) {
                applyFilter();
            }
            updateSetFilters(cmd);
            updateMaterialGroups(cmd);
            gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if ("ClearIgnoredSets".equals(data.action)) {
            clearIgnoredSets();
            this.selectedRecipeId = null;
            applyFilter();
            if (pruneInvalidMaterialGroups()) {
                applyFilter();
            }
            updateSetFilters(cmd);
            updateMaterialGroups(cmd);
            gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("MaterialGroup:")) {
            String payload = data.action.substring("MaterialGroup:".length());
            if ("All".equals(payload)) {
                toggleAllCategoriesState();
            } else if (payload.startsWith("idx:")) {
                int idx = Integer.parseInt(payload.substring(4));
                if (idx >= 0 && idx < currentGroups.size()) {
                    String groupName = currentGroups.get(idx).categoryId();
                    toggleCategoryFilter(groupName);
                }
            }
            pruneIncompatibleSetFilters();
            this.selectedRecipeId = null;
            applyFilter();
            updateMaterialGroups(cmd);
            updateSetFilters(cmd);
            gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            sendUpdate(cmd, null, false);

        } else if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim();
            applyFilter();
            if (pruneInvalidMaterialGroups()) {
                applyFilter();
            }
            this.selectedRecipeId = null;
            updateBenchTabs(cmd);
            updateMaterialGroups(cmd);
            updateSetFilters(cmd);
            gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            sendUpdate(cmd, null, false);

        } else if ("ToggleAffordable".equals(data.action)) {
            this.affordabilityMode = this.affordabilityMode.next();
            applyFilter();
            // Prune stale categories that no longer exist after affordability change
            if (pruneInvalidMaterialGroups()) {
                applyFilter();
            }
            updateAffordabilityToggle(cmd);
            if (ingredientController != null) {
                ingredientController.updateUI(cmd);
            }
            updateMaterialGroups(cmd);
            updateSetFilters(cmd);
            gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);


        } else if ("ToggleCategories".equals(data.action)) {
            this.categoriesExpanded = !this.categoriesExpanded;
            cmd.set("#CategoriesHeader.Text", Message.translation(categoriesExpanded
                    ? "server.ui.stencil.sidebar.categoriesExpanded"
                    : "server.ui.stencil.sidebar.categoriesCollapsed"));
            cmd.set("#MaterialGroupsContainer.Visible", categoriesExpanded);
            sendUpdate(cmd, null, false);

        } else if ("ToggleSets".equals(data.action)) {
            this.setsExpanded = !this.setsExpanded;
            cmd.set("#SetsHeader.Text", Message.translation(setsExpanded
                    ? "server.ui.stencil.sidebar.setsExpanded"
                    : "server.ui.stencil.sidebar.setsCollapsed"));
            cmd.set("#SetFilters.Visible", setsExpanded);
            sendUpdate(cmd, null, false);

        } else if ("ToggleIgnoredSets".equals(data.action)) {
            this.ignoredSetsExpanded = !this.ignoredSetsExpanded;
            cmd.set("#IgnoredSetsHeader.Text", Message.translation(ignoredSetsExpanded
                    ? "server.ui.stencil.sidebar.ignoredExpanded"
                    : "server.ui.stencil.sidebar.ignoredCollapsed"));
            cmd.set("#IgnoredSetFilters.Visible", ignoredSetsExpanded);
            sendUpdate(cmd, null, false);

        } else if (data.action != null && (data.action.startsWith("IngredientCheckbox:") 
                || data.action.startsWith("IngredientExpand:") 
                || data.action.startsWith("IngredientToggle:"))) {
            if (ingredientController != null) {
                ingredientController.handleEvent(data.action);
                applyFilter();
                if (pruneInvalidMaterialGroups()) {
                    applyFilter();
                }
                ingredientController.updateUI(cmd);
                updateMaterialGroups(cmd);
                updateSetFilters(cmd);
                gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
                updateDetail(cmd);
                savePrefs();
            }
            sendUpdate(cmd, null, false);

        } else if ("ClearIngredients".equals(data.action)) {
            if (ingredientController != null) {
                ingredientController.clearAll();
                applyFilter();
                if (pruneInvalidMaterialGroups()) {
                    applyFilter();
                }
                ingredientController.updateUI(cmd);
                updateMaterialGroups(cmd);
                updateSetFilters(cmd);
                gridController.updateUI(cmd, displayedRecipes, selectedRecipeId);
                updateDetail(cmd);
                savePrefs();
            }
            sendUpdate(cmd, null, false);

        } else if ("GiveStencil".equals(data.action)) {
            giveSelectedStencil(store, ref, cmd);
            sendUpdate(cmd, null, false);
        } else {
            // Always acknowledge events, even when payload is empty or unknown,
            // to prevent client-side loading overlays from hanging.
            sendUpdate(cmd, null, false);
        }
    }

    private void buildBenchTabs(UICommandBuilder cmd, UIEventBuilder evt) {
        DebugLogger.logGateStatus(STENCIL_BOOK, "StencilSelectionPage.buildBenchTabs");
        final int totalBenches = benchIds.size();
        DebugLogger.log(STENCIL_BOOK, Level.INFO, () ->
            "[Stencil Crafting][BenchTabs] build START: loading " + totalBenches +
                " benches (max slots: " + MAX_BENCH_TABS + "), benchIds=" + benchIds);

        // Tab 0: "All"
        int tabIndex = 0;

        cmd.set("#BenchTabs[" + tabIndex + "].Id", ALL_TAB);
        cmd.set("#BenchTabs[" + tabIndex + "].TooltipText", Message.translation("server.ui.stencil.tabs.all"));
        cmd.set("#BenchTabs[" + tabIndex + "].Visible", true);
        DebugLogger.log(STENCIL_BOOK, Level.FINE, () ->
            "[Stencil Crafting][BenchTabs] Tab #0 SET: id='All' (default, no icon override)");
        tabIndex++;

        // Tabs 1..N: one per bench ID
        for (String benchId : benchIds) {
            if (tabIndex >= MAX_BENCH_TABS) break;
            cmd.set("#BenchTabs[" + tabIndex + "].Id", benchId);
            cmd.set("#BenchTabs[" + tabIndex + "].TooltipText", BenchRegistry.getTabGrouper().getDisplayName(benchId));
            cmd.set("#BenchTabs[" + tabIndex + "].Visible", true);
            // Always override the icon so that the template's pre-seeded slot icons
            // (which are at fixed positions unrelated to the runtime bench order) do not
            // bleed through. resolveTabIcon falls back to DEFAULT_TAB_ICON when no
            // explicit mapping exists.
            String resolvedIcon = BenchRegistry.getTabGrouper().resolveTabIcon(benchId);
            cmd.set("#BenchTabs[" + tabIndex + "].Icon", resolvedIcon);
            final String tabKey = benchId;
            final String iconPath = resolvedIcon;
            final int index = tabIndex;
            DebugLogger.log(STENCIL_BOOK, Level.INFO, () ->
                "[Stencil Crafting][BenchTabs] Tab #" + index + " icon SET: tab='" + tabKey +
                    "' resolved_path='" + iconPath + "'");
            tabIndex++;
        }

        // Hide remaining unused slots
        for (int i = tabIndex; i < MAX_BENCH_TABS; i++) {
            cmd.set("#BenchTabs[" + i + "].Visible", false);
        }
        
        final int finalTabIndex = tabIndex;
        DebugLogger.log(STENCIL_BOOK, Level.INFO, () ->
            "[Stencil Crafting][BenchTabs] build COMPLETE: " + finalTabIndex + " tabs populated, " +
                (MAX_BENCH_TABS - finalTabIndex) + " slots hidden");

        // Bind the tab-change event
        evt.addEventBinding(
                CustomUIEventBindingType.SelectedTabChanged, "#BenchTabs",
                EventData.of("@SelectedTab", "#BenchTabs.SelectedTab"),
                false
        );
    }

    private void updateBenchTabs(UICommandBuilder cmd) {
        cmd.set("#BenchTabs.SelectedTab", activeTab);
        if (ALL_TAB.equals(activeTab)) {
            cmd.set("#ActiveBenchLabel.Text", Message.translation("server.ui.stencil.tabs.all"));
        } else {
            cmd.set("#ActiveBenchLabel.Text", tabDisplayName(activeTab));
        }
    }

    private static String tabDisplayName(String tabId) {
        if (tabId == null) return "";
        return tabId.replace('_', ' ');
    }

    /**
     * Validates that {@link #activeTab} corresponds to a currently known bench ID.
     * Falls back to {@link #ALL_TAB} if the saved tab is stale (e.g., a bench was
     * removed from the registry since the preference was saved).
     */
    private void validateActiveTab() {
        if (ALL_TAB.equals(activeTab)) return;
        if (!benchIds.contains(activeTab)) {
            DebugLogger.log(STENCIL_BOOK, Level.WARNING,
                    "[StencilBook] Saved activeTab '" + activeTab +
                    "' not in current benchIds " + benchIds + "; resetting to All");
            activeTab = ALL_TAB;
        }
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
            evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#SetFilters[" + idx + "] #IgnoreBtn",
                EventData.of("Action", "IgnoreSet:idx:" + i)
            );
            evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#IgnoredSetFilters[" + idx + "] #IgnoreBtn",
                EventData.of("Action", "RestoreIgnoredSet:idx:" + i)
            );
        }
        evt.addEventBinding(
            CustomUIEventBindingType.Activating, "#ClearIgnoredSetsBtn",
            EventData.of("Action", "ClearIgnoredSets")
        );
    }

    private void updateSetFilters(UICommandBuilder cmd) {
        cmd.set("#IgnoredSetsHeader.Text", Message.translation(ignoredSetsExpanded
            ? "server.ui.stencil.sidebar.ignoredExpanded"
            : "server.ui.stencil.sidebar.ignoredCollapsed"));
        cmd.set("#IgnoredSetFilters.Visible", ignoredSetsExpanded);

        FilterSelectionState selectionState = getSetSelectionState();
        boolean showSelectAllIcon = selectionState == FilterSelectionState.NONE;
        cmd.set("#ClearSetsIcon #IconCheck.Visible", showSelectAllIcon);
        cmd.set("#ClearSetsIcon #IconTrash.Visible", !showSelectAllIcon);
        cmd.set("#ClearSetsBtn.TooltipText", selectionState == FilterSelectionState.NONE
            ? "Select all sets"
            : "Clear selected sets");

        // Per-set filter buttons (indices 0..totalSetCount-1)
        for (int i = 0; i < totalSetCount; i++) {
            String sel = "#SetFilters[" + i + "]";
            if (i < currentSets.size()) {
                String setName = currentSets.get(i);
                String label = RecipeFilterPipeline.setDisplayLabel(setName);
                boolean checked = selectionState == FilterSelectionState.ALL
                        || activeSetFilters.contains(setName);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + " #Btn.Text", label);
                cmd.set(sel + " #Btn.Style", checked ? FILTER_ACTIVE : FILTER_INACTIVE);
                cmd.set(sel + " #Check.Value", checked);
                cmd.set(sel + " #Check.Visible", true);
                cmd.set(sel + " #IgnoreIconCross.Visible", true);
                cmd.set(sel + " #IgnoreBtn.TooltipText", "Ignore set");
                cmd.set(sel + " #IgnoreIconCheck.Visible", false);
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }

        cmd.set("#ClearIgnoredSetsBtn.Visible", !currentIgnoredSets.isEmpty());
        for (int i = 0; i < totalSetCount; i++) {
            String sel = "#IgnoredSetFilters[" + i + "]";
            if (i < currentIgnoredSets.size()) {
                String setName = currentIgnoredSets.get(i);
                String label = RecipeFilterPipeline.setDisplayLabel(setName);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + " #Btn.Text", label);
                cmd.set(sel + " #Btn.Style", FILTER_INACTIVE);
                cmd.set(sel + " #Check.Visible", false);
                cmd.set(sel + " #IgnoreIconCross.Visible", false);
                cmd.set(sel + " #IgnoreBtn.TooltipText", "Restore set");
                cmd.set(sel + " #IgnoreIconCheck.Visible", true);
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }
    }

    private void updateDetail(UICommandBuilder cmd) {
        resetGiveStencilFeedback(cmd);
        Player player = playerStore != null
                ? playerStore.getComponent(playerRef_ref, Player.getComponentType()) : null;
        CombinedItemContainer container = player != null
            ? player.getInventory().getCombinedHotbarFirst() : null;
        boolean checkInventoryAffordability = affordabilityMode == AffordabilityMode.INVENTORY_DRIVEN
            && !shouldBypassAffordabilityChecks(player)
            && container != null;
        detailController.updateUI(cmd, selectedRecipeId, allRecipes, checkInventoryAffordability, container);
    }

    private void updateAffordabilityToggle(UICommandBuilder cmd) {
        cmd.set("#AffordableToggle.Text", affordabilityMode.label());
        cmd.set("#AffordableToggle.Style", FILTER_ACTIVE);
        cmd.set("#IngredientSection.Visible", affordabilityMode == AffordabilityMode.RESOURCE_PLANNING);
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
                    CustomUIEventBindingType.Activating, "#MaterialGroups[" + i + "] #Btn",
                    EventData.of("Action", "MaterialGroup:idx:" + i)
            );
        }
    }

    private void updateMaterialGroups(UICommandBuilder cmd) {
        FilterSelectionState selectionState = getCategorySelectionState();
        boolean showSelectAllIcon = selectionState == FilterSelectionState.NONE;
        cmd.set("#ClearCategoriesIcon #IconCheck.Visible", showSelectAllIcon);
        cmd.set("#ClearCategoriesIcon #IconTrash.Visible", !showSelectAllIcon);
        cmd.set("#ClearCategoriesBtn.TooltipText", selectionState == FilterSelectionState.NONE
            ? "Select all categories"
            : "Clear selected categories");

        // Per-group icon buttons (indices 0..N-1)
        for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
            String sel = "#MaterialGroups[" + i + "]";
            if (i < currentGroups.size()) {
                RecipeFilterPipeline.MaterialGroup group = currentGroups.get(i);
                boolean active = selectionState == FilterSelectionState.ALL
                        || activeMaterialGroups.contains(group.categoryId());
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + ".TooltipText", group.displayName());
                cmd.set(sel + " #ActiveOverlay.Visible", active);
                // Set icon
                String iconFile = group.iconPath();
                if (iconFile != null && !iconFile.isEmpty()) {
                    cmd.set(sel + " #FilterIcon.Background", iconFile);
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
        normalizeSetSelectionState();
    }

    private boolean pruneInvalidMaterialGroups() {
        if (selectAllCategories || activeMaterialGroups.isEmpty()) return false;
        Set<String> validCats = new HashSet<>();
        for (RecipeFilterPipeline.MaterialGroup g : currentGroups) {
            validCats.add(g.categoryId());
        }
        boolean changed = activeMaterialGroups.removeIf(c -> !validCats.contains(c));
        if (changed) {
            normalizeCategorySelectionState();
        }
        return changed;
    }

    private void toggleSetFilter(String setName) {
        if (setName == null || setName.isEmpty()) return;
        if (ignoredSetFilters.contains(setName)) return;
        FilterSelectionState state = getSetSelectionState();
        if (state == FilterSelectionState.ALL) {
            selectAllSets = false;
            activeSetFilters.clear();
            activeSetFilters.addAll(currentSets);
            activeSetFilters.remove(setName);
            normalizeSetSelectionState();
            return;
        }

        if (activeSetFilters.contains(setName)) {
            activeSetFilters.remove(setName);
        } else {
            activeSetFilters.add(setName);
        }
        normalizeSetSelectionState();
    }

    private void toggleAllSetsState() {
        FilterSelectionState state = getSetSelectionState();
        if (state == FilterSelectionState.SOME || state == FilterSelectionState.ALL) {
            selectAllSets = false;
            activeSetFilters.clear();
            return;
        }
        if (currentSets.isEmpty()) {
            selectAllSets = false;
            activeSetFilters.clear();
            return;
        }
        selectAllSets = true;
        activeSetFilters.clear();
    }

    private void normalizeSetSelectionState() {
        if (currentSets.isEmpty()) {
            selectAllSets = false;
            activeSetFilters.clear();
            return;
        }

        activeSetFilters.removeIf(ignoredSetFilters::contains);
        activeSetFilters.retainAll(currentSets);
        if (activeSetFilters.size() >= currentSets.size()) {
            selectAllSets = true;
            activeSetFilters.clear();
            return;
        }

        if (selectAllSets && activeSetFilters.isEmpty()) {
            return;
        }
        selectAllSets = false;
    }

    private FilterSelectionState getSetSelectionState() {
        if (currentSets.isEmpty()) {
            return FilterSelectionState.NONE;
        }
        if (selectAllSets) {
            return FilterSelectionState.ALL;
        }
        int selectedCount = 0;
        for (String setName : currentSets) {
            if (activeSetFilters.contains(setName)) {
                selectedCount++;
            }
        }
        if (selectedCount <= 0) {
            return FilterSelectionState.NONE;
        }
        if (selectedCount >= currentSets.size()) {
            return FilterSelectionState.ALL;
        }
        return FilterSelectionState.SOME;
    }

    private void ignoreSet(String setName) {
        if (setName == null || setName.isEmpty()) return;
        ignoredSetFilters.add(setName);
        activeSetFilters.remove(setName);
        normalizeSetSelectionState();
    }

    private void restoreIgnoredSet(String setName) {
        if (setName == null || setName.isEmpty()) return;
        ignoredSetFilters.remove(setName);
    }

    private void clearIgnoredSets() {
        ignoredSetFilters.clear();
    }

    private void toggleCategoryFilter(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) return;
        FilterSelectionState state = getCategorySelectionState();
        if (state == FilterSelectionState.ALL) {
            selectAllCategories = false;
            activeMaterialGroups.clear();
            for (RecipeFilterPipeline.MaterialGroup group : currentGroups) {
                activeMaterialGroups.add(group.categoryId());
            }
            activeMaterialGroups.remove(categoryId);
            normalizeCategorySelectionState();
            return;
        }

        if (activeMaterialGroups.contains(categoryId)) {
            activeMaterialGroups.remove(categoryId);
        } else {
            activeMaterialGroups.add(categoryId);
        }
        normalizeCategorySelectionState();
    }

    private void toggleAllCategoriesState() {
        FilterSelectionState state = getCategorySelectionState();
        if (state == FilterSelectionState.SOME || state == FilterSelectionState.ALL) {
            selectAllCategories = false;
            activeMaterialGroups.clear();
            return;
        }
        if (currentGroups.isEmpty()) {
            selectAllCategories = false;
            activeMaterialGroups.clear();
            return;
        }
        selectAllCategories = true;
        activeMaterialGroups.clear();
    }

    private void normalizeCategorySelectionState() {
        if (currentGroups.isEmpty()) {
            selectAllCategories = false;
            activeMaterialGroups.clear();
            return;
        }

        Set<String> categoryIds = new HashSet<>();
        for (RecipeFilterPipeline.MaterialGroup group : currentGroups) {
            categoryIds.add(group.categoryId());
        }

        activeMaterialGroups.removeIf(categoryId -> !categoryIds.contains(categoryId));
        if (activeMaterialGroups.size() >= categoryIds.size()) {
            selectAllCategories = true;
            activeMaterialGroups.clear();
            return;
        }

        if (selectAllCategories && activeMaterialGroups.isEmpty()) {
            return;
        }
        selectAllCategories = false;
    }

    private FilterSelectionState getCategorySelectionState() {
        if (currentGroups.isEmpty()) {
            return FilterSelectionState.NONE;
        }
        if (selectAllCategories) {
            return FilterSelectionState.ALL;
        }

        int selectedCount = 0;
        for (RecipeFilterPipeline.MaterialGroup group : currentGroups) {
            if (activeMaterialGroups.contains(group.categoryId())) {
                selectedCount++;
            }
        }
        if (selectedCount <= 0) {
            return FilterSelectionState.NONE;
        }
        if (selectedCount >= currentGroups.size()) {
            return FilterSelectionState.ALL;
        }
        return FilterSelectionState.SOME;
    }

    private Map<String, RecipeFilterPipeline.CategoryInfo> buildCategoryInfoMap() {
        Map<String, ItemCategory> allCats = ItemCategory.getAssetMap().getAssetMap();

        // Item.getCategories() returns dot-notation strings like "Blocks.Metal",
        // "Furniture.Beds", but ItemCategory.getId() returns simple IDs like "Metal".
        // Build the map with dot-notation keys to match recipe category IDs.
        Map<String, RecipeFilterPipeline.CategoryInfo> map = new LinkedHashMap<>();
        for (ItemCategory topLevel : allCats.values()) {
            DebugLogger.log(STENCIL_BOOK, Level.FINE, () -> "[StencilBook] Top-level category: id=" + topLevel.getId()
                    + " name=" + topLevel.getName() + " icon=" + topLevel.getIcon()
                    + " order=" + topLevel.getOrder());
            // Top-level entries (e.g. "Blocks", "Items", "Furniture")
                map.put(topLevel.getId(), new RecipeFilterPipeline.CategoryInfo(
                    topLevel.getId(), capitalize(topLevel.getId()),
                    IconPathResolver.normalizeCategoryIcon(topLevel.getIcon()), topLevel.getOrder()));

            // Child entries with dot-notation keys (e.g. "Blocks.Metal", "Furniture.Beds")
            ItemCategory[] children = topLevel.getChildren();
            if (children != null) {
                for (ItemCategory child : children) {
                    String dotKey = topLevel.getId() + "." + child.getId();
                    DebugLogger.log(STENCIL_BOOK, Level.FINE, () -> "[StencilBook]   Child category: dotKey=" + dotKey
                            + " name=" + child.getName() + " icon=" + child.getIcon()
                            + " order=" + child.getOrder());
                    map.put(dotKey, new RecipeFilterPipeline.CategoryInfo(
                            dotKey, capitalize(child.getId()),
                            IconPathResolver.normalizeCategoryIcon(child.getIcon()), child.getOrder()));
                }
            }
        }
        DebugLogger.log(STENCIL_BOOK, Level.INFO, () -> "[StencilBook] Built categoryInfoMap with " + map.size() + " categories: " + map.keySet());
        return map;
    }

    private static String capitalize(String id) {
        if (id == null || id.isEmpty()) return id;
        // "TechnicalBlocks" â†’ "Technical Blocks", "Beds" â†’ "Beds"
        String spaced = id.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private void giveSelectedStencil(Store<EntityStore> store, Ref<EntityStore> ref,
                                     UICommandBuilder cmd) {
        if (selectedRecipeId == null) {
            showGiveStencilStatusFeedback(cmd, store, "Pick One", GIVE_STATUS_ERROR);
            DebugLogger.chat(this.playerRef, STENCIL_BOOK,
                    "\u00a7c[StencilBook] No recipe selected.");
            return;
        }

        RecipeEntry entry = findEntry(selectedRecipeId);
        if (entry == null) {
            showGiveStencilStatusFeedback(cmd, store, "Unavailable", GIVE_STATUS_ERROR);
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            showGiveStencilStatusFeedback(cmd, store, "Try Again", GIVE_STATUS_ERROR);
            return;
        }

        ItemStack item = StencilMetadata.createStencil(entry.outputItemId(), entry.recipeId());
        if (!upsertSingleStencilStack(player, item, entry.recipeId())) {
            showGiveStencilStatusFeedback(cmd, store, "No Space", GIVE_STATUS_ERROR);
            DebugLogger.chat(this.playerRef, STENCIL_BOOK,
                "\u00a7c[StencilBook] Could not give stencil: inventory is full.");
            return;
        }

        showGiveStencilStatusFeedback(cmd, store, "Given", GIVE_STATUS_SUCCESS);

        DebugLogger.chat(this.playerRef, STENCIL_BOOK,
                "\u00a7a[StencilBook] Given stencil: " + entry.outputItemId().replace('_', ' '));
        DebugLogger.log(STENCIL_BOOK, Level.INFO, "[StencilUI] Gave player stencil for " + entry.outputItemId() + " (recipe: " + entry.recipeId() + ")");
    }

    private int getStencilQuantity(Player player, String recipeId) {
        return getStencilQuantity(player.getInventory().getHotbar(), recipeId)
                + getStencilQuantity(player.getInventory().getBackpack(), recipeId)
                + getStencilQuantity(player.getInventory().getStorage(), recipeId);
    }

    private boolean upsertSingleStencilStack(Player player, ItemStack stencil, String recipeId) {
        ItemContainer combined = player.getInventory().getCombinedHotbarFirst();
        if (combined == null) {
            return false;
        }
        if (upsertStencilInContainer(combined, recipeId)) {
            return true;
        }
        return placeInFirstEmptySlot(combined, stencil);
    }

    private boolean upsertStencilInContainer(ItemContainer container, String recipeId) {
        if (container == null) return false;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null) continue;
            if (!StencilMetadata.isStencil(stack)) continue;
            if (!recipeId.equals(StencilMetadata.getRecipeId(stack))) continue;
            if (stack.getQuantity() != StencilMetadata.STENCIL_STACK_SIZE) {
                ItemStack normalized = new ItemStack(
                        stack.getItemId(),
                        StencilMetadata.STENCIL_STACK_SIZE,
                        stack.getMetadata());
                container.setItemStackForSlot(slot, normalized);
            }
            return true;
        }
        return false;
    }

    private boolean placeInFirstEmptySlot(ItemContainer container, ItemStack stackToPlace) {
        if (container == null) return false;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            if (container.getItemStack(slot) != null) continue;
            container.setItemStackForSlot(slot, stackToPlace);
            return true;
        }
        return false;
    }

    private int getStencilQuantity(ItemContainer container, String recipeId) {
        if (container == null) return 0;
        int total = 0;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null) continue;
            if (!StencilMetadata.isStencil(stack)) continue;
            if (!recipeId.equals(StencilMetadata.getRecipeId(stack))) continue;
            total += stack.getQuantity();
        }
        return total;
    }

    private void resetGiveStencilFeedback(UICommandBuilder cmd) {
        cmd.set("#GetPlaceholderBtn.Text", "Give Stencil");
        cmd.set("#GetPlaceholderBtn.Style", GIVE_BTN_PRIMARY);
        cmd.set("#GiveStencilStatusLabel.Text", "");
        cmd.set("#GiveStencilStatusLabel.Style", GIVE_STATUS_SUCCESS);
        cmd.set("#GiveStencilStatusLabel.Visible", false);
    }

    private void showGiveStencilStatusFeedback(UICommandBuilder cmd,
                                               Store<EntityStore> store,
                                               String message,
                                               Value<String> labelStyle) {
        cmd.set("#GetPlaceholderBtn.Text", "Give Stencil");
        cmd.set("#GetPlaceholderBtn.Style", GIVE_BTN_PRIMARY);
        cmd.set("#GiveStencilStatusLabel.Text", message);
        cmd.set("#GiveStencilStatusLabel.Style", labelStyle);
        cmd.set("#GiveStencilStatusLabel.Visible", true);
        long seq = giveStencilFeedbackSeq.incrementAndGet();
        scheduleGiveStencilButtonReset(store, seq);
    }

    private void scheduleGiveStencilButtonReset(Store<EntityStore> store, long seq) {
        if (store == null || store.getExternalData() == null) return;
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> {
            if (giveStencilFeedbackSeq.get() != seq) return;
            if (playerRef_ref == null || !playerRef_ref.isValid()) return;
            UICommandBuilder reset = new UICommandBuilder();
            resetGiveStencilFeedback(reset);
            sendUpdate(reset, null, false);
        }), 1, TimeUnit.SECONDS);
    }

    @Nullable
    private RecipeEntry findEntry(String recipeId) {
        for (RecipeEntry entry : allRecipes) {
            if (entry.recipeId.equals(recipeId)) return entry;
        }
        return null;
    }

    /** @intent Resolve stencil-list affordability through the shared facade first, then apply a legacy block-group fallback for UI parity only.
     *  @wave   5 - documented fallback-only path and kept facade as the semantic source-of-truth
     *  @status implemented
     *  @node   StencilSelectionPage#isAffordable
     */
    private boolean isAffordable(RecipeFilterPipeline.InputRecipe entry, CombinedItemContainer container) {
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId());
        if (recipe != null) {
            FilteredRecipeEntry filteredEntry = RecipeFilterRegistry.getEntry(entry.recipeId());
            boolean preferNatural = filteredEntry != null && filteredEntry.preferNatural();
            if (CraftingAffordabilityFacade.isAffordable(recipe, preferNatural, container)) return true;
        }

        return isBlockGroupFallbackAffordable(entry.outputItemId(), container);
    }

    /** @intent Keep FullBlocks block-group swap behavior as an explicit fallback-only UI affordance when direct recipe affordability is false.
     *  @wave   5 - isolated legacy fallback for auditability and parity rationale
     *  @status implemented
     *  @node   StencilSelectionPage#isBlockGroupFallbackAffordable
     */
    private boolean isBlockGroupFallbackAffordable(@Nullable String outputItemId,
                                                   CombinedItemContainer container) {
        // Intentional fallback-only path:
        // This check models free conversion among BlockGroup members for stencil browsing UX.
        // It is not the parity source-of-truth for direct recipe affordability semantics.
        // Keep this isolated so shared facade/resolver logic remains the primary boundary.
        Item outputItem = Item.getAssetMap().getAsset(outputItemId);
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

    private boolean shouldBypassAffordabilityChecks(@Nullable Player player) {
        // Adventure mode is the only mode where stencil affordability/resource checks are enforced.
        return player == null || player.getGameMode() != GameMode.Adventure;
    }

    private static String resolveSearchableName(@Nullable String outputItemId, @Nullable String blockTypeId) {
        StringBuilder searchText = new StringBuilder();
        appendSearchFragment(searchText, humanizeId(blockTypeId));
        appendSearchFragment(searchText, humanizeId(outputItemId));

        if (outputItemId != null && !outputItemId.isBlank()) {
            Item outputItem = Item.getAssetMap().getAsset(outputItemId);
            if (outputItem != null) {
                var packet = outputItem.toPacket();
                if (packet != null) {
                    var translationProperties = packet.translationProperties;
                    if (translationProperties != null) {
                        appendSearchFragment(searchText, translationProperties.name);
                        appendSearchFragment(searchText, translationKeyToSearchTerms(translationProperties.name));
                    }
                }
            }
        }

        return searchText.toString();
    }

    private static String resolveSearchableDescription(@Nullable String outputItemId) {
        if (outputItemId == null || outputItemId.isBlank()) {
            return "";
        }
        Item outputItem = Item.getAssetMap().getAsset(outputItemId);
        if (outputItem == null) {
            return "";
        }
        var packet = outputItem.toPacket();
        if (packet == null) {
            return "";
        }
        var translationProperties = packet.translationProperties;
        if (translationProperties == null) {
            return "";
        }

        StringBuilder searchText = new StringBuilder();
        appendSearchFragment(searchText, translationProperties.description);
        appendSearchFragment(searchText, translationKeyToSearchTerms(translationProperties.description));
        return searchText.toString();
    }

    private static String humanizeId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('_', ' ');
    }

    private static String translationKeyToSearchTerms(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String normalized = key;
        int itemsIdx = normalized.indexOf("items.");
        if (itemsIdx >= 0) {
            normalized = normalized.substring(itemsIdx + "items.".length());
        }
        normalized = normalized.replace(".name", "")
                .replace(".description", "")
                .replace('.', ' ')
                .replace('_', ' ');
        return normalized;
    }

    private static void appendSearchFragment(StringBuilder sb, @Nullable String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(fragment);
    }

    record RecipeEntry(String recipeId, String outputItemId, String blockTypeId,
                       Set<String> benchIds, String set,
                       List<String> categoryIds,
                       String searchableName,
                       String searchableDescription) {}

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (e, s) -> e.searchQuery = s, e -> e.searchQuery).add()
                .append(new KeyedCodec<>("@SelectedTab", Codec.STRING), (e, s) -> e.selectedTab = s, e -> e.selectedTab).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (e, s) -> e.itemStackId = s, e -> e.itemStackId).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (e, i) -> e.slotIndex = i, e -> e.slotIndex).add()
                .build();

        String searchQuery;
        String selectedTab;
        String action;
        String itemStackId;
        Integer slotIndex;
    }
}
