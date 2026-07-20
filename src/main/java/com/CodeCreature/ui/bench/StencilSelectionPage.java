package com.CodeCreature.ui.bench;

/**
 * @node    StencilSelectionPage
 * @wiki    docs/wiki/StencilBook/StencilSelectionPage.md
 * @intent  Orchestrates stencil book filtering, selection, and details while
 *          delegating center rendering to a feature-flagged renderer contract.
 * @wave    1 (dual renderer migration)
 * @status  Wave 1 - renderer abstraction integrated
 * @do-not  Move pipeline or detail-panel behavior authority into renderers.
 */

import com.CodeCreature.crafting.PlaceBlockCostUtil;
import com.CodeCreature.ui.common.IconPathResolver;
import com.CodeCreature.ui.ingredienttree.IngredientTree;
import com.CodeCreature.ui.ingredienttree.IngredientTreeBuilder;
import com.CodeCreature.ui.ingredienttree.IngredientTreeGridController;
import com.CodeCreature.registry.FilteredRecipeEntry;
import com.CodeCreature.registry.BenchRegistry;
import com.CodeCreature.registry.BenchTabGrouper;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.CodeCreature.ui.bench.render.BlankCenterRenderer;
import com.CodeCreature.ui.bench.render.GroupedCenterRenderer;
import com.CodeCreature.ui.bench.render.LegacyGridCenterRenderer;
import com.CodeCreature.ui.bench.render.SandboxCenterRenderer;
import com.CodeCreature.ui.bench.render.StencilCenterRenderer;
import com.CodeCreature.util.StencilMetadata;
import com.CodeCreature.util.FeatureFlags;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;
import java.util.logging.Level;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory;

public class StencilSelectionPage extends InteractiveCustomUIPage<StencilSelectionPage.EventPayload> {

    private static final int MAX_GROUP_BUTTONS = 30;
    private static final int MAX_BENCH_TABS = 16;
    private static final int GRID_CELLS_PER_ROW = 12;
    private static final String GROUPED_CENTER_RENDERER_FLAG = "ui.stencil_book.grouped_center_renderer";
    private static final Value<String> FILTER_ACTIVE =
            Value.ref("Styles/Buttons.ui", "FilterActiveStyle");
    private static final Value<String> FILTER_INACTIVE =
            Value.ref("Styles/Buttons.ui", "FilterInactiveStyle");

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
    private List<RecipeFilterPipeline.InputRecipe> cachedInputs = List.of();

    /** Computed at build time from unfiltered pipeline output. */
    private int totalSetCount;
    private String[] maxLayoutSetNames; // set name for each group slot, indexed 0..totalSetCount-1
    private int[] cellsPerSet;         // recipe count per set, indexed 0..totalSetCount-1
    private int totalCellCount;        // sum of all cellsPerSet
    private int totalRecipeRows;
    private int totalGridRows;
    private StencilCenterRenderer centerRenderer;
    private DetailPanelController detailController;
    private Map<String, RecipeFilterPipeline.CategoryInfo> categoryInfoMap = Map.of();

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

            allRecipes.add(new RecipeEntry(fe.recipeId(), fe.outputItemId(), fe.blockTypeId(),
                    resolvedKeys, fe.set(), fe.categoryIds()));
        }
        allRecipes.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.recipeId, b.recipeId));

        // Build cached InputRecipe list (used by applyFilter and computeMaxLayoutFromInputs)
        List<RecipeFilterPipeline.InputRecipe> inputs = new ArrayList<>(allRecipes.size());
        for (RecipeEntry entry : allRecipes) {
            inputs.add(new RecipeFilterPipeline.InputRecipe(
                    entry.recipeId(), entry.outputItemId(), entry.blockTypeId(),
                    entry.benchIds(), entry.set(), entry.categoryIds()));
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
        // Get player inventory for affordability checks
        Player filterPlayer = playerStore != null
                ? playerStore.getComponent(playerRef_ref, Player.getComponentType()) : null;
        var container = filterPlayer != null
                ? filterPlayer.getInventory().getCombinedBackpackStorageHotbar() : null;

        // Build affordability checker â€” null when disabled or no inventory
        RecipeFilterPipeline.AffordabilityChecker checker = null;
        boolean affordableOnly = false;
        RecipeFilterPipeline.ResourceTypeChecker resourceTypeChecker = null;
        switch (affordabilityMode) {
            case INVENTORY_DRIVEN -> {
                if (container != null) {
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
                this.cachedInputs, activeTab, effectiveGroups, effectiveSets, searchQuery, checker,
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
        StencilBookPrefs prefs = StencilBookPrefsStore.load(this.playerRef.getUuid());
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

        this.centerRenderer = new BlankCenterRenderer(maxLayoutSetNames, cellsPerSet);

        // Create detail panel controller
        this.detailController = new DetailPanelController();

        // Load main template
        cmd.append("Pages/StencilBook/StencilBookPage.ui");

        //  Append reusable components into empty containers (one-time init) 

        // Set filter buttons â€” one per set
        for (int i = 0; i < totalSetCount; i++) {
            cmd.append("#SetFilters", "Pages/StencilBook/Components/SetFilterButton.ui");
        }

        // Material group icon buttons (keep MAX_GROUP_BUTTONS)
        for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
            cmd.append("#MaterialGroups", "Pages/StencilBook/Components/GroupFilterButton.ui");
        }

        centerRenderer.appendStructure(cmd);

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

        // Give Stencil button
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#GetPlaceholderBtn",
                EventData.of("Action", "GiveStencil")
        );

        buildBenchTabs(cmd, evt);
        buildSetFilterBindings(evt);
        buildMaterialGroupBindings(evt);
        centerRenderer.buildBindings(evt);

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

        updateBenchTabs(cmd);
        updateMaterialGroups(cmd);
        updateSetFilters(cmd);
        centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
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
        centerRenderer.clearOnDismiss(cmd);
        detailController.clearUI(cmd);

        sendUpdate(cmd, null, false);
    }

    private void savePrefs() {
        StencilBookPrefs prefs = new StencilBookPrefs();
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
                centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
                updateDetail(cmd);
            }
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("SetFilter:")) {
            // Set filter toggle â€” index-based resolution
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
            if (pruneInvalidMaterialGroups()) {
                applyFilter();
            }
            updateSetFilters(cmd);
            updateMaterialGroups(cmd);
            centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
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
            centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
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
            centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
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
            centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
            updateDetail(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);


        } else if ("ToggleCategories".equals(data.action)) {
            this.categoriesExpanded = !this.categoriesExpanded;
            cmd.set("#CategoriesHeader.Text", Message.translation(categoriesExpanded
                    ? "server.ui.stencil.sidebar.categoriesExpanded"
                    : "server.ui.stencil.sidebar.categoriesCollapsed"));
            cmd.set("#MaterialGroups.Visible", categoriesExpanded);
            sendUpdate(cmd, null, false);

        } else if ("ToggleSets".equals(data.action)) {
            this.setsExpanded = !this.setsExpanded;
            cmd.set("#SetsHeader.Text", Message.translation(setsExpanded
                    ? "server.ui.stencil.sidebar.setsExpanded"
                    : "server.ui.stencil.sidebar.setsCollapsed"));
            cmd.set("#SetFilters.Visible", setsExpanded);
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
                centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
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
                centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
                updateDetail(cmd);
                savePrefs();
            }
            sendUpdate(cmd, null, false);

        } else if (data.action != null && data.action.startsWith("RecipeSelect:rid:")) {
            String recipeId = data.action.substring("RecipeSelect:rid:".length());
            if (recipeId != null && !recipeId.isEmpty()) {
                boolean visible = false;
                for (RecipeFilterPipeline.TaggedRecipe entry : displayedRecipes) {
                    if (entry.recipeId().equals(recipeId)) {
                        visible = true;
                        break;
                    }
                }

                if (visible) {
                    this.selectedRecipeId = recipeId;
                    centerRenderer.updateUI(cmd, displayedRecipes, selectedRecipeId);
                    updateDetail(cmd);
                }
            }
            sendUpdate(cmd, null, false);

        } else if ("GiveStencil".equals(data.action)) {
            giveSelectedStencil(store, ref, cmd);
            sendUpdate(cmd, null, false);
        }
    }

    private void buildBenchTabs(UICommandBuilder cmd, UIEventBuilder evt) {
        // Populate pre-declared tab slots (max-slots pattern — same as updateMaterialGroups)
        int tabIndex = 0;

        DebugLogger.logGateStatus(STENCIL_BOOK, "StencilSelectionPage.buildBenchTabs");
        final int totalBenches = benchIds.size();
        DebugLogger.log(STENCIL_BOOK, Level.INFO, () ->
            "[Stencil Crafting][BenchTabs] build START: loading " + totalBenches +
                " benches (max slots: " + MAX_BENCH_TABS + "), benchIds=" + benchIds);

        // Tab 0: "All"
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

    private void updateDetail(UICommandBuilder cmd) {
        Player player = playerStore != null
                ? playerStore.getComponent(playerRef_ref, Player.getComponentType()) : null;
        CombinedItemContainer container = player != null
                ? player.getInventory().getCombinedBackpackStorageHotbar() : null;
        detailController.updateUI(cmd, selectedRecipeId, allRecipes, affordabilityMode, container);
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
    }

    private boolean pruneInvalidMaterialGroups() {
        if (selectAllCategories || activeMaterialGroups.isEmpty()) return false;
        Set<String> validCats = new HashSet<>();
        for (RecipeFilterPipeline.MaterialGroup g : currentGroups) {
            validCats.add(g.categoryId());
        }
        return activeMaterialGroups.removeIf(c -> !validCats.contains(c));
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
            DebugLogger.chat(this.playerRef, STENCIL_BOOK,
                    "\u00a7c[StencilBook] No recipe selected.");
            return;
        }

        RecipeEntry entry = findEntry(selectedRecipeId);
        if (entry == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemStack item = StencilMetadata.createStencil(entry.outputItemId(), entry.recipeId());
        player.getInventory().getCombinedHotbarFirst().addItemStack(item);

        DebugLogger.chat(this.playerRef, STENCIL_BOOK,
                "\u00a7a[StencilBook] Given stencil: " + entry.outputItemId().replace('_', ' '));
        DebugLogger.log(STENCIL_BOOK, Level.INFO, "[StencilUI] Gave player stencil for " + entry.outputItemId() + " (recipe: " + entry.recipeId() + ")");
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

    record RecipeEntry(String recipeId, String outputItemId, String blockTypeId,
                       Set<String> benchIds, String set,
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
