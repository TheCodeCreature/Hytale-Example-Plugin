package com.UnobstructedThirdPerson.placeblock.ui;

import com.UnobstructedThirdPerson.placeblock.PlaceBlockMetadata;
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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
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

    private static final int PAGE_SIZE = 64;

    private static final Value<String> SLOT_STYLE_ARMED =
            Value.ref("Pages/BlueprintBench/PlaceholderSlot.ui", "ArmedStyle");
    private static final Value<String> SLOT_STYLE_DISABLED =
            Value.ref("Pages/BlueprintBench/PlaceholderSlot.ui", "DisabledSlotStyle");

    private static final Value<String> TAB_ACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "TabActiveStyle");
    private static final Value<String> TAB_INACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "TabInactiveStyle");
    private static final Value<String> FILTER_ACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "FilterActiveStyle");
    private static final Value<String> FILTER_INACTIVE =
            Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "FilterInactiveStyle");

    private static final String LIFE_ESSENCE_ITEM_ID = "Ingredient_Life_Essence";
    private static final String PLACEHOLDER_ITEM_ID = "Block_Placeholder";
    private static final int ACQUIRE_COST = 1;

    private static final String ALL_TAB = "All";
    private static final String ALL_FILTER = "All";

    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private final List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private final List<String> benchIds = new ArrayList<>();  // sorted bench IDs
    private String searchQuery = "";
    private String selectedRecipeId;
    private String activeTab = ALL_TAB;
    private String activeSetFilter = ALL_FILTER;
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

            // Set filter
            if (!ALL_FILTER.equals(activeSetFilter)) {
                if (entry.set == null || !entry.set.equals(activeSetFilter)) continue;
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

        // Rebuild current sets for the active tab — only sets where the player has at least one ingredient
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

            if (filterContainer != null) {
                CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                if (recipe != null) {
                    List<MaterialQuantity> materials = CraftingManager.getInputMaterials(recipe, 1);
                    // Include set if the player can afford at least one ingredient individually
                    boolean hasAny = materials.stream()
                            .anyMatch(mat -> filterContainer.canRemoveMaterials(List.of(mat)));
                    if (hasAny) {
                        sets.add(entry.set);
                    }
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

        // Build tabs, filters, recipe list, detail panel, and placeholder slots
        buildBenchTabs(cmd, evt);
        buildSetFilters(cmd, evt);
        buildRecipeList(cmd, evt, store, ref);
        updateDetailPanel(cmd);
        buildPlaceholderSlots(cmd, evt, store, ref);

        // Bind "Get Placeholder" button
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AcquireButton",
                EventData.of("Action", "GetPlaceholder")
        );
        updateAcquireButton(cmd, store, ref);
    }

    @Override
    public void handleDataEvent(@NonNull Ref<EntityStore> ref,
                                @NonNull Store<EntityStore> store,
                                @NonNull EventPayload data) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        if (data.action != null && data.action.startsWith("Tab:")) {
            // Tab switch
            String tab = data.action.substring("Tab:".length());
            if (!tab.equals(this.activeTab)) {
                this.activeTab = tab;
                this.activeSetFilter = ALL_FILTER;
                this.searchQuery = "";
                this.selectedRecipeId = null;
                applyFilter();
                cmd.set("#SearchInput.Value", "");
                buildBenchTabs(cmd, evt);
                buildSetFilters(cmd, evt);
                buildRecipeList(cmd, evt, store, ref);
                updateDetailPanel(cmd);
                buildPlaceholderSlots(cmd, evt, store, ref);
                updateAcquireButton(cmd, store, ref);
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#AcquireButton",
                        EventData.of("Action", "GetPlaceholder")
                );
                sendUpdate(cmd, evt, false);
            }

        } else if (data.action != null && data.action.startsWith("SetFilter:")) {
            // Set filter switch
            String setFilter = data.action.substring("SetFilter:".length());
            if (!setFilter.equals(this.activeSetFilter)) {
                this.activeSetFilter = setFilter;
                this.selectedRecipeId = null;
                applyFilter();
                buildSetFilters(cmd, evt);
                buildRecipeList(cmd, evt, store, ref);
                updateDetailPanel(cmd);
                buildPlaceholderSlots(cmd, evt, store, ref);
                updateAcquireButton(cmd, store, ref);
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#AcquireButton",
                        EventData.of("Action", "GetPlaceholder")
                );
                sendUpdate(cmd, evt, false);
            }

        } else if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim();
            applyFilter();
            this.selectedRecipeId = null;
            buildBenchTabs(cmd, evt);
            buildSetFilters(cmd, evt);
            buildRecipeList(cmd, evt, store, ref);
            updateDetailPanel(cmd);
            buildPlaceholderSlots(cmd, evt, store, ref);
            updateAcquireButton(cmd, store, ref);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AcquireButton",
                    EventData.of("Action", "GetPlaceholder")
            );
            sendUpdate(cmd, evt, false);

        } else if (data.recipeId != null) {
            this.selectedRecipeId = data.recipeId;
            buildRecipeList(cmd, evt, store, ref);
            updateDetailPanel(cmd);
            buildPlaceholderSlots(cmd, evt, store, ref);
            updateAcquireButton(cmd, store, ref);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AcquireButton",
                    EventData.of("Action", "GetPlaceholder")
            );
            sendUpdate(cmd, evt, false);

        } else if (data.action != null && data.action.startsWith("Assign:")) {
            // Action format: "Assign:<slotIndex>"
            String slotIndexStr = data.action.substring("Assign:".length());
            short slotIndex;
            try {
                slotIndex = Short.parseShort(slotIndexStr);
            } catch (NumberFormatException e) {
                return;
            }

            if (this.selectedRecipeId == null) {
                cmd.set("#StatusMessage.Text", "§eSelect a recipe first.");
                sendUpdate(cmd, evt, false);
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            Inventory inventory = player.getInventory();
            var combined = inventory.getCombinedHotbarFirst();

            ItemStack stack = combined.getItemStack(slotIndex);
            if (stack == null || !PlaceBlockMetadata.isPlaceBlock(stack)) {
                cmd.set("#StatusMessage.Text", "§cSlot no longer contains a placeholder.");
                buildPlaceholderSlots(cmd, evt, store, ref);
                sendUpdate(cmd, evt, false);
                return;
            }

            RecipeEntry entry = findEntry(this.selectedRecipeId);
            if (entry == null) {
                cmd.set("#StatusMessage.Text", "§cRecipe not found.");
                sendUpdate(cmd, evt, false);
                return;
            }

            // Arm the placeholder — slotIndex is the combined container index (hotbar-first)
            ItemStack armed = PlaceBlockMetadata.arm(stack, entry.recipeId, entry.blockTypeId, (int) slotIndex);
            combined.setItemStackForSlot(slotIndex, armed);

            cmd.set("#StatusMessage.Text", "§aAssigned " + entry.blockTypeId + " to placeholder.");

            // Refresh placeholder slots to show updated status — stay open
            buildPlaceholderSlots(cmd, evt, store, ref);
            updateAcquireButton(cmd, store, ref);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AcquireButton",
                    EventData.of("Action", "GetPlaceholder")
            );
            sendUpdate(cmd, evt, false);

        } else if (data.action != null && data.action.equals("SelectSlot")) {
            // SlotClicking on ItemGrid — slotIndex from engine event data
            int idx = data.slotIndex;
            System.out.println("[BlueprintUI] SlotClicking fired, slotIndex=" + idx
                    + ", displayedRecipes.size=" + displayedRecipes.size());
            if (idx >= 0 && idx < displayedRecipes.size()) {
                this.selectedRecipeId = displayedRecipes.get(idx).recipeId;
                System.out.println("[BlueprintUI] Selected recipe: " + this.selectedRecipeId);
            } else {
                System.out.println("[BlueprintUI] SlotIndex out of range, ignoring");
            }
            buildRecipeList(cmd, evt, store, ref);
            updateDetailPanel(cmd);
            buildPlaceholderSlots(cmd, evt, store, ref);
            updateAcquireButton(cmd, store, ref);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AcquireButton",
                    EventData.of("Action", "GetPlaceholder")
            );
            sendUpdate(cmd, evt, false);

        } else if (data.action != null && data.action.equals("GetPlaceholder")) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            ItemContainer combined = player.getInventory().getCombinedHotbarFirst();
            ItemStack costStack = new ItemStack(LIFE_ESSENCE_ITEM_ID, ACQUIRE_COST);

            if (!combined.canRemoveItemStack(costStack)) {
                cmd.set("#StatusMessage.Text", "§cNot enough Ingredient_Life_Essence.");
                updateAcquireButton(cmd, store, ref);
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#AcquireButton",
                        EventData.of("Action", "GetPlaceholder")
                );
                sendUpdate(cmd, evt, false);
                return;
            }

            combined.removeItemStack(costStack);

            SimpleItemContainer.addOrDropItemStacks(
                    store, ref, combined,
                    List.of(new ItemStack(PLACEHOLDER_ITEM_ID, 1))
            );

            cmd.set("#StatusMessage.Text", "§aPlaceholder acquired!");

            // Refresh placeholder slots and acquire button
            buildPlaceholderSlots(cmd, evt, store, ref);
            updateAcquireButton(cmd, store, ref);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AcquireButton",
                    EventData.of("Action", "GetPlaceholder")
            );
            sendUpdate(cmd, evt, false);
        }
    }

    private void buildBenchTabs(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear("#BenchTabs");

        // "All" tab
        cmd.appendInline("#BenchTabs",
                "TextButton #TabAll { Text: \"All\"; Anchor: (Width: 60, Height: 30); Padding: (Left: 8, Right: 8); }");
        cmd.set("#TabAll.Style", ALL_TAB.equals(activeTab) ? TAB_ACTIVE : TAB_INACTIVE);
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#TabAll",
                EventData.of("Action", "Tab:" + ALL_TAB)
        );

        // One tab per bench ID
        for (int i = 0; i < benchIds.size(); i++) {
            String benchId = benchIds.get(i);
            String tabId = "Tab" + i;
            // Clean bench ID for display (e.g. "Furniture_Bench" -> "Furniture Bench")
            String label = benchId.replace('_', ' ');

            cmd.appendInline("#BenchTabs",
                    "TextButton #" + tabId + " { Text: \"" + label + "\"; Anchor: (Height: 30); Padding: (Left: 10, Right: 10); }");
            cmd.set("#" + tabId + ".Style", benchId.equals(activeTab) ? TAB_ACTIVE : TAB_INACTIVE);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating, "#" + tabId,
                    EventData.of("Action", "Tab:" + benchId)
            );
        }
    }

    private void buildSetFilters(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear("#SetFilters");

        if (currentSets.isEmpty()) return;

        // "All" filter
        cmd.appendInline("#SetFilters",
                "TextButton #FilterAll { Text: \"All\"; Anchor: (Width: 40, Height: 22); Padding: (Left: 4, Right: 4); }");
        cmd.set("#FilterAll.Style", ALL_FILTER.equals(activeSetFilter) ? FILTER_ACTIVE : FILTER_INACTIVE);
        evt.addEventBinding(
                CustomUIEventBindingType.Activating, "#FilterAll",
                EventData.of("Action", "SetFilter:" + ALL_FILTER)
        );

        // One filter per set
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
                    "TextButton #" + filterId + " { Text: \"" + label + "\"; Anchor: (Height: 22); Padding: (Left: 6, Right: 6); }");
            cmd.set("#" + filterId + ".Style", setName.equals(activeSetFilter) ? FILTER_ACTIVE : FILTER_INACTIVE);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating, "#" + filterId,
                    EventData.of("Action", "SetFilter:" + setName)
            );
        }
    }

    private void buildRecipeList(UICommandBuilder cmd, UIEventBuilder evt,
                                 Store<EntityStore> store, Ref<EntityStore> ref) {
        cmd.clear("#RecipeGrid");

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
            withAffordability.add(new RecipeEntry(entry.recipeId, entry.outputItemId, entry.blockTypeId,
                    entry.benchId, entry.set, affordable));
        }

        // Sort affordable above unaffordable, preserving alphabetical within each group
        withAffordability.sort(Comparator.comparing((RecipeEntry e) -> !e.affordable)
                .thenComparing(e -> e.recipeId, String.CASE_INSENSITIVE_ORDER));

        int showing = Math.min(withAffordability.size(), PAGE_SIZE);
        displayedRecipes = new ArrayList<>(withAffordability.subList(0, showing));

        System.out.println("[BlueprintUI] Building recipe grid: " + showing + " / " + withAffordability.size() + " items");

        for (int i = 0; i < showing; i++) {
            RecipeEntry entry = displayedRecipes.get(i);

            // Append minimal cell template (Group + ItemIcon)
            cmd.append("#RecipeGrid", "Pages/BlueprintBench/RecipeIconCell.ui");
            String base = "#RecipeGrid[" + i + "]";

            // Set item icon via space-separated child selector
            cmd.set(base + " #CellIcon.ItemId", entry.outputItemId);

            // Bind click event on cell
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    base,
                    EventData.of("RecipeId", entry.recipeId)
            );
        }

        System.out.println("[BlueprintUI] Grid build complete: " + showing + " cells");

        // Update count label
        String countText = filteredRecipes.size() + " recipes";
        if (showing < filteredRecipes.size()) {
            countText = showing + " / " + filteredRecipes.size() + " recipes";
        }
        cmd.set("#CountLabel.Text", countText);
    }

    private void updateDetailPanel(UICommandBuilder cmd) {
        cmd.clear("#CostGrid");

        if (selectedRecipeId != null) {
            RecipeEntry entry = findEntry(selectedRecipeId);
            if (entry != null) {
                cmd.set("#OutputIcon.ItemId", entry.outputItemId);
                cmd.set("#OutputName.Text", entry.blockTypeId);
                cmd.set("#StatusMessage.Text", "");

                // Populate ingredient icons
                try {
                    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId);
                    if (recipe != null) {
                        MaterialQuantity[] inputs = recipe.getInput();
                        if (inputs != null) {
                            FilteredRecipeEntry fe = RecipeFilterRegistry.getEntry(entry.recipeId);
                            BenchCategory category = fe != null ? fe.benchCategory() : BenchCategory.BUILDERS_ONLY;
                            int slot = 0;
                            for (int i = 0; i < inputs.length; i++) {
                                if (inputs[i] == null) continue;
                                String itemId = ResourceTypeResolver.resolveInputItemId(inputs[i], category);
                                if (itemId == null || itemId.isEmpty()) continue;
                                cmd.append("#CostGrid", "Pages/BlueprintBench/CostIconCell.ui");
                                String base = "#CostGrid[" + slot + "]";
                                cmd.set(base + " #CostIcon.ItemId", itemId);
                                slot++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[BlueprintUI] Error populating cost grid: " + e.getMessage());
                    e.printStackTrace();
                }
                return;
            }
        }
        cmd.set("#OutputIcon.ItemId", "");
        cmd.set("#OutputName.Text", "No recipe selected");
        cmd.set("#StatusMessage.Text", "");
    }

    private void updateAcquireButton(UICommandBuilder cmd,
                                     Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemContainer combined = player.getInventory().getCombinedHotbarFirst();
        boolean canAfford = combined.canRemoveItemStack(
                new ItemStack(LIFE_ESSENCE_ITEM_ID, ACQUIRE_COST));

        if (canAfford) {
            cmd.set("#AcquireButton.Style",
                    Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "ConfirmButtonStyle"));
        } else {
            cmd.set("#AcquireButton.Style",
                    Value.ref("Pages/BlueprintBench/BlueprintBenchPage.ui", "DisabledConfirmStyle"));
        }
    }

    private void buildPlaceholderSlots(UICommandBuilder cmd, UIEventBuilder evt,
                                       Store<EntityStore> store, Ref<EntityStore> ref) {
        cmd.clear("#PlaceholderSlots");

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        var combined = player.getInventory().getCombinedHotbarFirst();
        int slotIndex = 0;

        for (short i = 0; i < combined.getCapacity(); i++) {
            ItemStack stack = combined.getItemStack(i);
            if (stack == null || !PlaceBlockMetadata.isPlaceBlock(stack)) continue;

            cmd.append("#PlaceholderSlots", "Pages/BlueprintBench/PlaceholderSlot.ui");

            String prefix = "#PlaceholderSlots[" + slotIndex + "]";

            // Build display text: "Block_Placeholder → Oak_Planks" or "Block_Placeholder [click to assign]"
            String armedRecipeId = PlaceBlockMetadata.getArmedRecipeId(stack);
            String displayText;
            if (armedRecipeId != null) {
                String blockTypeId = PlaceBlockMetadata.getOutputBlockTypeId(stack);
                displayText = stack.getItemId() + " → " + (blockTypeId != null ? blockTypeId : armedRecipeId);
                cmd.set(prefix + ".Style", SLOT_STYLE_ARMED);
            } else if (this.selectedRecipeId != null) {
                displayText = stack.getItemId() + " [click to assign]";
            } else {
                displayText = stack.getItemId() + " [select a recipe first]";
                cmd.set(prefix + ".Style", SLOT_STYLE_DISABLED);
            }
            cmd.set(prefix + ".TextSpans", Message.raw(displayText));

            // Clicking the row assigns the selected recipe
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    prefix,
                    EventData.of("Action", "Assign:" + i)
            );

            slotIndex++;
        }

        if (slotIndex == 0) {
            cmd.set("#StatusMessage.Text", "§eNo placeholder blocks in inventory.");
        }
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
                .append(new KeyedCodec<>("RecipeId", Codec.STRING), (e, s) -> e.recipeId = s, e -> e.recipeId).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (e, s) -> e.slotIndex = s, e -> e.slotIndex).add()
                .build();

        String searchQuery;
        String recipeId;
        String action;
        int slotIndex = -1;
    }
}
