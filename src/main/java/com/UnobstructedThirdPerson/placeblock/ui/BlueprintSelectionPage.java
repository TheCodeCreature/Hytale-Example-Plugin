package com.UnobstructedThirdPerson.placeblock.ui;

import com.UnobstructedThirdPerson.placeblock.PlaceBlockCostUtil;
import com.UnobstructedThirdPerson.stencil.StencilMetadata;
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
import com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory;

public class BlueprintSelectionPage extends InteractiveCustomUIPage<BlueprintSelectionPage.EventPayload> {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("BlueprintSelectionPage");

    private static final int MAX_SET_FILTERS = 20;
    private static final int MAX_GROUP_BUTTONS = 25;
    private static final int MAX_COST_CELLS = 8;
    private static final int MAX_SET_GROUPS = 20;      // matches MAX_SET_FILTERS
    private static final int CELLS_PER_GROUP = 9;       // one row of 9 columns per group
    private static final int MAX_RECIPE_CELLS = MAX_SET_GROUPS * CELLS_PER_GROUP;  // 180

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
    private boolean categoriesExpanded = true;
    private boolean setsExpanded = true;
    private boolean selectAllSets = false;
    private boolean selectAllCategories = false;
    private final Set<String> activeMaterialGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private List<RecipeFilterPipeline.MaterialGroup> currentGroups = new ArrayList<>();
    private List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes = new ArrayList<>();
    private List<String> currentSets = new ArrayList<>();  // sets for active tab

    /** Maps cell slot index (0..MAX_RECIPE_CELLS-1) to displayedRecipes index. -1 = unused. */
    private final int[] cellSlotToRecipeIndex = new int[MAX_RECIPE_CELLS];
    private Map<String, RecipeFilterPipeline.CategoryInfo> categoryInfoMap = Map.of();

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
                    primaryBench, fe.set(), true, fe.categoryIds()));
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
                    entry.benchId(), entry.set(), entry.categoryIds()));
        }

        // Execute pipeline
        Set<String> effectiveSets = selectAllSets ? Set.of() : activeSetFilters;
        Set<String> effectiveGroups = selectAllCategories ? Set.of() : activeMaterialGroups;
        RecipeFilterPipeline.PipelineResult result = pipeline.execute(
                inputs, activeTab, effectiveGroups, effectiveSets, searchQuery, checker,
                affordabilityEnabled, showUncategorized, categoryInfoMap);

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
        this.affordabilityEnabled = prefs.affordabilityEnabled;
        this.showUncategorized = prefs.showUncategorized;
        this.searchQuery = prefs.searchQuery != null ? prefs.searchQuery : "";
        this.selectedRecipeId = prefs.selectedRecipeId;
        this.selectAllSets = prefs.selectAllSets;
        this.selectAllCategories = prefs.selectAllCategories;

        loadRecipes();

        // Load main template
        cmd.append("Pages/BlueprintBench/BlueprintBenchPage.ui");

        // ── Append reusable components into empty containers (one-time init) ──

        // Set filter buttons
        for (int i = 0; i < MAX_SET_FILTERS; i++) {
            cmd.append("#SetFilters", "Pages/BlueprintBench/SetFilterButton.ui");
        }

        // Material group icon buttons
        for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
            cmd.append("#MaterialGroups", "Pages/BlueprintBench/GroupFilterButton.ui");
        }

        // Per-set group containers (each contains a label + wrapping cell grid)
        for (int g = 0; g < MAX_SET_GROUPS; g++) {
            cmd.append("#RecipeGridArea", "Pages/BlueprintBench/SetGroupContainer.ui");
            for (int c = 0; c < CELLS_PER_GROUP; c++) {
                cmd.append("#RecipeGridArea[" + g + "] #GroupCells",
                           "Pages/BlueprintBench/RecipeIconCell.ui");
            }
        }

        // Cost cells
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
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#UncategorizedToggle",
                EventData.of("Action", "ToggleUncategorized")
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

        // ── Set initial state ──
        cmd.set("#AffordableToggle.Style", affordabilityEnabled ? FILTER_ACTIVE : FILTER_INACTIVE);
        cmd.set("#UncategorizedToggle.Style", showUncategorized ? FILTER_ACTIVE : FILTER_INACTIVE);

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
        for (int g = 0; g < MAX_SET_GROUPS; g++) {
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
        prefs.affordabilityEnabled = this.affordabilityEnabled;
        prefs.showUncategorized = this.showUncategorized;
        prefs.searchQuery = this.searchQuery;
        prefs.selectedRecipeId = this.selectedRecipeId;
        prefs.selectAllSets = this.selectAllSets;
        prefs.selectAllCategories = this.selectAllCategories;
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
            this.affordabilityEnabled = !this.affordabilityEnabled;
            applyFilter();
            // Prune stale categories that no longer exist after affordability change
            pruneInvalidMaterialGroups();
            cmd.set("#AffordableToggle.Style", affordabilityEnabled ? FILTER_ACTIVE : FILTER_INACTIVE);
            updateMaterialGroups(cmd);
            updateSetFilters(cmd);
            updateRecipeGrid(cmd);
            updateDetailPanel(cmd);
            savePrefs();
            sendUpdate(cmd, null, false);

        } else if ("ToggleUncategorized".equals(data.action)) {
            this.showUncategorized = !this.showUncategorized;
            applyFilter();
            cmd.set("#UncategorizedToggle.Style", showUncategorized ? FILTER_ACTIVE : FILTER_INACTIVE);
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

        } else if (data.action != null && data.action.startsWith("RecipeSelect:idx:")) {
            int slotIdx = -1;
            try { slotIdx = Integer.parseInt(data.action.substring("RecipeSelect:idx:".length())); } catch (NumberFormatException ignored) {}
            if (slotIdx >= 0 && slotIdx < MAX_RECIPE_CELLS) {
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
        // Per-set filter buttons (indices 0..MAX_SET_FILTERS-1)
        for (int i = 0; i < MAX_SET_FILTERS; i++) {
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
        // Per-set filter buttons (indices 0..MAX_SET_FILTERS-1)
        for (int i = 0; i < MAX_SET_FILTERS; i++) {
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
        int flatIdx = 0;
        for (int g = 0; g < MAX_SET_GROUPS; g++) {
            for (int c = 0; c < CELLS_PER_GROUP; c++) {
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        "#RecipeGridArea[" + g + "] #GroupCells[" + c + "] #CellBtn",
                        EventData.of("Action", "RecipeSelect:idx:" + flatIdx));
                flatIdx++;
            }
        }
    }

    private void updateRecipeGrid(UICommandBuilder cmd) {
        // Reset indirection map
        Arrays.fill(cellSlotToRecipeIndex, -1);

        // Walk displayedRecipes (sorted by effectiveSet) and detect set boundaries
        int groupIdx = -1;
        int cellInGroup = 0;
        String currentSet = null;

        for (int recipeIdx = 0; recipeIdx < displayedRecipes.size(); recipeIdx++) {
            RecipeFilterPipeline.TaggedRecipe entry = displayedRecipes.get(recipeIdx);

            // Set boundary → advance to next group
            if (!entry.effectiveSet().equals(currentSet)) {
                // Hide remaining cells in the previous group
                if (groupIdx >= 0) {
                    hideRemainingCells(cmd, groupIdx, cellInGroup);
                }
                groupIdx++;
                if (groupIdx >= MAX_SET_GROUPS) break;  // overflow — no more group slots

                currentSet = entry.effectiveSet();
                cellInGroup = 0;

                // Show group and set its label
                String groupSel = "#RecipeGridArea[" + groupIdx + "]";
                cmd.set(groupSel + ".Visible", true);
                cmd.set(groupSel + " #SetGroupLabel.Text",
                        RecipeFilterPipeline.setDisplayLabel(currentSet));
            }

            // Overflow within group — skip recipe (no cell slot available)
            if (cellInGroup >= CELLS_PER_GROUP) continue;

            // Populate cell
            int globalIdx = groupIdx * CELLS_PER_GROUP + cellInGroup;
            String cellSel = "#RecipeGridArea[" + groupIdx + "] #GroupCells[" + cellInGroup + "]";
            cmd.set(cellSel + ".Visible", true);
            cmd.set(cellSel + " #CellIcon.ItemId", entry.outputItemId());
            cmd.set(cellSel + " #CellDim.Visible", !entry.affordable());

            cellSlotToRecipeIndex[globalIdx] = recipeIdx;
            cellInGroup++;
        }

        // Hide remaining cells in the last populated group
        if (groupIdx >= 0 && groupIdx < MAX_SET_GROUPS) {
            hideRemainingCells(cmd, groupIdx, cellInGroup);
        }

        // Hide all unused groups
        for (int g = groupIdx + 1; g < MAX_SET_GROUPS; g++) {
            cmd.set("#RecipeGridArea[" + g + "].Visible", false);
        }
    }

    /** Hide cells [startCell..CELLS_PER_GROUP) in the given group. */
    private void hideRemainingCells(UICommandBuilder cmd, int groupIdx, int startCell) {
        for (int c = startCell; c < CELLS_PER_GROUP; c++) {
            cmd.set("#RecipeGridArea[" + groupIdx + "] #GroupCells[" + c + "].Visible", false);
        }
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
                               String benchId, String set, boolean affordable,
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
