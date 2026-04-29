package com.UnobstructedThirdPerson.placeblock.ui;

import com.UnobstructedThirdPerson.resourcecollection.BenchCategory;
import com.UnobstructedThirdPerson.resourcecollection.FilteredRecipeEntry;
import com.UnobstructedThirdPerson.resourcecollection.RecipeFilterRegistry;
import com.UnobstructedThirdPerson.resourcecollection.ResourceTypeResolver;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;

public class BlueprintSelectionPage extends InteractiveCustomUIPage<BlueprintSelectionPage.EventPayload> {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("BlueprintSelectionPage");

    private static final Value<String> FILTER_ACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "FilterActiveStyle");
    private static final Value<String> FILTER_INACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "FilterInactiveStyle");

    private static final String ALL_TAB = "All";
    private static final String ALL_FILTER = "All";

    /** Controls how inventory contents gate recipe/set visibility. */
    private enum CraftableFilter {
        /** Show all recipes regardless of inventory. */
        NONE("No Filter"),
        /** Show recipes where the player has at least one ingredient. */
        PARTIAL("Has Partial"),
        /** Show only recipes the player can fully craft. */
        FULL("Can Craft");

        final String label;
        CraftableFilter(String label) { this.label = label; }
        CraftableFilter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private final List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private final List<String> benchIds = new ArrayList<>();  // sorted bench IDs
    private String searchQuery = "";
    private String selectedRecipeId;
    private String placeholderItemId;  // item ID set in the placeholder input slot
    private String activeTab = ALL_TAB;
    private final Set<String> activeSetFilters = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private CraftableFilter craftableFilter = CraftableFilter.FULL;
    private List<RecipeEntry> displayedRecipes = new ArrayList<>();
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

    private void applyFilter() {
        filteredRecipes.clear();
        String query = searchQuery.toLowerCase();

        for (RecipeEntry entry : allRecipes) {
            // Tab filter
            if (!ALL_TAB.equals(activeTab)) {
                if (entry.benchId == null || !entry.benchId.equals(activeTab)) continue;
            }

            // Set filter — empty means "All"
            if (!activeSetFilters.isEmpty()) {
                if (entry.set == null || !activeSetFilters.contains(entry.set)) continue;
            }

            // Search filter
            if (!query.isEmpty()
                    && !entry.recipeId.toLowerCase().contains(query)
                    && !entry.blockTypeId.toLowerCase().contains(query)
                    && (entry.set == null || !entry.set.toLowerCase().contains(query))) {
                continue;
            }

            filteredRecipes.add(entry);
        }

        // Rebuild current sets for the active tab
        Player filterPlayer = playerStore != null
                ? playerStore.getComponent(playerRef_ref, Player.getComponentType()) : null;
        var filterContainer = filterPlayer != null
                ? filterPlayer.getInventory().getCombinedBackpackStorageHotbar() : null;

        Set<String> sets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (RecipeEntry entry : allRecipes) {
            if (!ALL_TAB.equals(activeTab)) {
                if (entry.benchId == null || !entry.benchId.equals(activeTab)) continue;
            }
            if (entry.set == null || entry.set.isEmpty()) continue;
            if (sets.contains(entry.set)) continue; // already qualified

            if (craftableFilter != CraftableFilter.NONE && filterContainer != null) {
                CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                if (recipe != null) {
                    List<MaterialQuantity> materials = CraftingManager.getInputMaterials(recipe, 1);
                    if (craftableFilter == CraftableFilter.FULL) {
                        // Include set only if at least one recipe in it is fully affordable
                        if (!filterContainer.canRemoveMaterials(materials)) continue;
                    } else {
                        // PARTIAL: include set if the player has at least one ingredient
                        boolean hasAny = materials.stream()
                                .anyMatch(mat -> filterContainer.canRemoveMaterials(List.of(mat)));
                        if (!hasAny) continue;
                    }
                    sets.add(entry.set);
                }
            } else {
                sets.add(entry.set);
            }
        }
        currentSets = new ArrayList<>(sets);
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        loadRecipes();

        // Load custom .ui template — no appendInline()
        cmd.append("Pages/BlueprintBench/BlueprintBenchPage.ui");

        // Bind search input
        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );

        // Set craftable filter dropdown value
        cmd.set("#CraftableDropdown.Value", craftableFilter.name());
        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#CraftableDropdown",
                EventData.of("@CraftableFilter", "#CraftableDropdown.Value"),
                false
        );

        // Build tabs, filters, recipe list, and detail panel
        bindBenchTabs(cmd, evt);
        buildSetFilters(cmd, evt);
        buildRecipeList(cmd, evt, store, ref);
        updateDetailPanel(cmd);

        // Initialize placeholder input slot
        initPlaceholderInputSlot(cmd, evt);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

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
                bindBenchTabs(cmd, evt);
                buildSetFilters(cmd, evt);
                buildRecipeList(cmd, evt, store, ref);
                updateDetailPanel(cmd);
                sendUpdate(cmd, evt, false);
            }

        } else if (data.action != null && data.action.startsWith("SetFilter:")) {
            // Set filter toggle — multi-select
            String setFilter = data.action.substring("SetFilter:".length());
            if (ALL_FILTER.equals(setFilter)) {
                activeSetFilters.clear();
            } else {
                if (activeSetFilters.contains(setFilter)) {
                    activeSetFilters.remove(setFilter);
                } else {
                    activeSetFilters.add(setFilter);
                }
            }
            this.selectedRecipeId = null;
            applyFilter();
            buildSetFilters(cmd, evt);
            buildRecipeList(cmd, evt, store, ref);
            updateDetailPanel(cmd);
            sendUpdate(cmd, evt, false);

        } else if (data.craftableFilter != null) {
            try {
                this.craftableFilter = CraftableFilter.valueOf(data.craftableFilter);
            } catch (IllegalArgumentException e) {
                return;
            }
            applyFilter();
            buildSetFilters(cmd, evt);
            buildRecipeList(cmd, evt, store, ref);
            updateDetailPanel(cmd);
            sendUpdate(cmd, evt, false);

        } else if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim();
            applyFilter();
            this.selectedRecipeId = null;
            bindBenchTabs(cmd, evt);
            buildSetFilters(cmd, evt);
            buildRecipeList(cmd, evt, store, ref);
            updateDetailPanel(cmd);
            sendUpdate(cmd, evt, false);

        } else if (data.recipeId != null) {
            this.selectedRecipeId = data.recipeId;
            buildRecipeList(cmd, evt, store, ref);
            updateDetailPanel(cmd);
            sendUpdate(cmd, evt, false);

        } else if ("ClearPlaceholder".equals(data.action)) {
            this.placeholderItemId = null;
            updatePlaceholderSlot(cmd, evt);
            sendUpdate(cmd, evt, false);

        } else if ("PlaceholderDrop".equals(data.action)) {
            // Dropped event — engine auto-provides ItemStackId from the dragged slot
            if (data.itemStackId != null && !data.itemStackId.isEmpty()) {
                this.placeholderItemId = data.itemStackId;
                updatePlaceholderSlot(cmd, evt);
                LOGGER.info("[BlueprintUI] Placeholder set via drop: " + data.itemStackId);
            }
            sendUpdate(cmd, evt, false);

        } else if ("RecipeHover".equals(data.action)) {
            // Hover over a recipe slot — preview details using slotIndex
            if (data.slotIndex != null && data.slotIndex >= 0 && data.slotIndex < displayedRecipes.size()) {
                RecipeEntry entry = displayedRecipes.get(data.slotIndex);
                this.selectedRecipeId = entry.recipeId;
                updateDetailPanel(cmd);
            }
            sendUpdate(cmd, evt, false);

        } else if ("RecipeSelect".equals(data.action)) {
            // Click-release on a recipe slot — confirm selection
            if (data.slotIndex != null && data.slotIndex >= 0 && data.slotIndex < displayedRecipes.size()) {
                RecipeEntry entry = displayedRecipes.get(data.slotIndex);
                this.selectedRecipeId = entry.recipeId;
                updateDetailPanel(cmd);
            }
            sendUpdate(cmd, evt, false);
        }
    }

    private void bindBenchTabs(UICommandBuilder cmd, UIEventBuilder evt) {
        // Set the active tab (tabs are static in .ui)
        cmd.set("#BenchTabs.SelectedTab", activeTab);
        cmd.set("#ActiveBenchLabel.Text", tabDisplayName(activeTab));

        // Bind tab change event
        evt.addEventBinding(
                CustomUIEventBindingType.SelectedTabChanged, "#BenchTabs",
                EventData.of("@SelectedTab", "#BenchTabs.SelectedTab"),
                false
        );
    }

    private static String tabDisplayName(String tabId) {
        if (tabId == null) return "";
        return tabId.replace('_', ' ');
    }

    private void buildSetFilters(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear("#SetFilters");

        if (currentSets.isEmpty()) return;

        // "All" filter
        cmd.appendInline("#SetFilters",
                "TextButton #FilterAll { Text: \"All\"; Anchor: (Height: 24); Padding: (Left: 6, Right: 6); }");
        cmd.set("#FilterAll.Style", activeSetFilters.isEmpty() ? FILTER_ACTIVE : FILTER_INACTIVE);
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#FilterAll",
                EventData.of("Action", "SetFilter:" + ALL_FILTER)
        );

        // One filter per set — vertical list, multi-select
        for (int i = 0; i < currentSets.size(); i++) {
            String setName = currentSets.get(i);
            String filterId = "Filter" + i;
            // Shorten set name for display (e.g. "Wood_Hardwood_Planks" -> "Hardwood Planks")
            String label = setName;
            if (label.contains("_")) {
                // Drop first segment (usually material category), replace underscores with spaces
                label = label.substring(label.indexOf('_') + 1).replace('_', ' ');
            }

            cmd.appendInline("#SetFilters",
                    "TextButton #" + filterId + " { Text: \"" + label + "\"; Anchor: (Height: 24); Padding: (Left: 6, Right: 6); }");
            cmd.set("#" + filterId + ".Style", activeSetFilters.contains(setName) ? FILTER_ACTIVE : FILTER_INACTIVE);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating, "#" + filterId,
                    EventData.of("Action", "SetFilter:" + setName)
            );
        }
    }

    private void buildRecipeList(UICommandBuilder cmd, UIEventBuilder evt,
                                 Store<EntityStore> store, Ref<EntityStore> ref) {

        // Compute affordability for each filtered recipe
        Player player = store.getComponent(ref, Player.getComponentType());
        var container = player != null ? player.getInventory().getCombinedBackpackStorageHotbar() : null;

        List<RecipeEntry> withAffordability = new ArrayList<>(filteredRecipes.size());
        for (RecipeEntry entry : filteredRecipes) {
            boolean affordable = true;
            if (container != null) {
                CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                if (recipe != null) {
                    List<MaterialQuantity> materials = CraftingManager.getInputMaterials(recipe, 1);
                    affordable = container.canRemoveMaterials(materials);
                }
            }
            // When craftable filter is active, skip recipes that don't meet the threshold
            if (craftableFilter == CraftableFilter.FULL && !affordable) continue;
            if (craftableFilter == CraftableFilter.PARTIAL && container != null) {
                CraftingRecipe partialRecipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                if (partialRecipe != null) {
                    List<MaterialQuantity> mats = CraftingManager.getInputMaterials(partialRecipe, 1);
                    boolean hasAny = mats.stream()
                            .anyMatch(mat -> container.canRemoveMaterials(List.of(mat)));
                    if (!hasAny) continue;
                }
            }

            withAffordability.add(new RecipeEntry(entry.recipeId, entry.outputItemId, entry.blockTypeId,
                    entry.benchId, entry.set, affordable));
        }

        // Sort: group by set (alphabetical), then affordable first, then recipe ID within each set
        withAffordability.sort(Comparator
                .comparing((RecipeEntry e) -> e.set != null ? e.set : "", String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> !e.affordable)
                .thenComparing(e -> e.recipeId, String.CASE_INSENSITIVE_ORDER));

        displayedRecipes = new ArrayList<>(withAffordability);

        // Build ItemGridSlot array for the recipe grid
        ItemGridSlot[] recipeSlots = new ItemGridSlot[displayedRecipes.size()];
        for (int i = 0; i < displayedRecipes.size(); i++) {
            RecipeEntry entry = displayedRecipes.get(i);
            ItemGridSlot slot = new ItemGridSlot(new ItemStack(entry.outputItemId, 1));
            slot.setActivatable(true);
            slot.setName(entry.blockTypeId != null
                    ? entry.blockTypeId.replace('_', ' ') : entry.outputItemId.replace('_', ' '));
            recipeSlots[i] = slot;
        }
        cmd.set("#RecipeGrid.Slots", recipeSlots);

        // Bind hover to preview recipe details
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#RecipeGrid",
                EventData.of("Action", "RecipeHover"), false);

        // Bind click-release to confirm recipe selection
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#RecipeGrid",
                EventData.of("Action", "RecipeSelect"), false);
    }

    private void updateDetailPanel(UICommandBuilder cmd) {
        cmd.clear("#CostGrid");

        if (selectedRecipeId != null) {
            RecipeEntry entry = findEntry(selectedRecipeId);
            if (entry != null) {
                cmd.set("#OutputIcon.ItemId", entry.outputItemId);
                cmd.set("#OutputName.Text", entry.blockTypeId != null
                        ? entry.blockTypeId.replace('_', ' ') : entry.outputItemId);

                // Populate ingredient grid with quantities
                try {
                    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                    if (recipe != null) {
                        MaterialQuantity[] inputs = recipe.getInput();
                        if (inputs != null) {
                            FilteredRecipeEntry fe = RecipeFilterRegistry.getEntry(entry.recipeId);
                            BenchCategory category = fe != null ? fe.benchCategory() : BenchCategory.BUILDERS_ONLY;

                            // Aggregate quantities by item ID
                            Map<String, Integer> ingredientMap = new LinkedHashMap<>();
                            for (MaterialQuantity mq : inputs) {
                                if (mq == null) continue;
                                String itemId = ResourceTypeResolver.resolveInputItemId(mq, category);
                                if (itemId == null || itemId.isEmpty()) continue;
                                ingredientMap.merge(itemId, mq.getQuantity(), Integer::sum);
                            }

                            int idx = 0;
                            for (var e : ingredientMap.entrySet()) {
                                cmd.append("#CostGrid", "Pages/BlueprintBench/CostCell.ui");
                                cmd.set("#CostGrid[" + idx + "] #CostIcon.ItemId", e.getKey());
                                cmd.set("#CostGrid[" + idx + "] #CostQty.Text", "x" + e.getValue());
                                idx++;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("[BlueprintUI] Error populating cost grid: " + e.getMessage());
                }
                return;
            }
        }
        cmd.set("#OutputIcon.ItemId", "");
        cmd.set("#OutputName.Text", "No recipe selected");
    }

    private void initPlaceholderInputSlot(UICommandBuilder cmd, UIEventBuilder evt) {
        this.placeholderItemId = null;
        updatePlaceholderSlot(cmd, evt);
    }

    private void updatePlaceholderSlot(UICommandBuilder cmd, UIEventBuilder evt) {
        if (placeholderItemId != null) {
            ItemGridSlot slot = new ItemGridSlot(new ItemStack(placeholderItemId, 1));
            slot.setActivatable(true);
            slot.setName(placeholderItemId.replace('_', ' '));
            cmd.set("#PlaceholderInputSlot.Slots", new ItemGridSlot[] { slot });
            cmd.set("#PlaceholderDropIndicator.Visible", false);
            cmd.set("#ClearPlaceholder.Visible", true);
        } else {
            ItemGridSlot emptySlot = new ItemGridSlot();
            emptySlot.setActivatable(true);
            cmd.set("#PlaceholderInputSlot.Slots", new ItemGridSlot[] { emptySlot });
            cmd.set("#PlaceholderDropIndicator.Visible", true);
            cmd.set("#ClearPlaceholder.Visible", false);
        }

        // Bind Dropped event on the input slot
        evt.addEventBinding(CustomUIEventBindingType.Dropped, "#PlaceholderInputSlot",
                EventData.of("Action", "PlaceholderDrop"), false);

        // Bind clear button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearPlaceholder",
                EventData.of("Action", "ClearPlaceholder"), false);
    }

    @Nullable
    private RecipeEntry findEntry(String recipeId) {
        for (RecipeEntry entry : allRecipes) {
            if (entry.recipeId.equals(recipeId)) return entry;
        }
        return null;
    }

    private record RecipeEntry(String recipeId, String outputItemId, String blockTypeId,
                               String benchId, String set, boolean affordable) {}

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (e, s) -> e.searchQuery = s, e -> e.searchQuery).add()
                .append(new KeyedCodec<>("@CraftableFilter", Codec.STRING), (e, s) -> e.craftableFilter = s, e -> e.craftableFilter).add()
                .append(new KeyedCodec<>("@SelectedTab", Codec.STRING), (e, s) -> e.selectedTab = s, e -> e.selectedTab).add()
                .append(new KeyedCodec<>("RecipeId", Codec.STRING), (e, s) -> e.recipeId = s, e -> e.recipeId).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (e, s) -> e.itemStackId = s, e -> e.itemStackId).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (e, i) -> e.slotIndex = i, e -> e.slotIndex).add()
                .build();

        String searchQuery;
        String craftableFilter;
        String selectedTab;
        String recipeId;
        String action;
        String itemStackId;
        Integer slotIndex;
    }
}
