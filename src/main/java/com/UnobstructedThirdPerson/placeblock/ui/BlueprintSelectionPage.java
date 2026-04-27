package com.UnobstructedThirdPerson.placeblock.ui;

import com.UnobstructedThirdPerson.placeblock.PlaceBlockMetadata;
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
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BlueprintSelectionPage extends InteractiveCustomUIPage<BlueprintSelectionPage.EventPayload> {

    private static final int PAGE_SIZE = 64;

    private static final Value<String> CELL_STYLE_SELECTED =
            Value.ref("Pages/BlueprintBench/RecipeIconCell.ui", "SelectedCellStyle");
    private static final Value<String> CELL_STYLE_UNAFFORDABLE =
            Value.ref("Pages/BlueprintBench/RecipeIconCell.ui", "UnaffordableCellStyle");
    private static final Value<String> SLOT_STYLE_ARMED =
            Value.ref("Pages/BlueprintBench/PlaceholderSlot.ui", "ArmedStyle");
    private static final Value<String> SLOT_STYLE_DISABLED =
            Value.ref("Pages/BlueprintBench/PlaceholderSlot.ui", "DisabledSlotStyle");

    private static final String LIFE_ESSENCE_ITEM_ID = "Ingredient_Life_Essence";
    private static final String PLACEHOLDER_ITEM_ID = "Block_Placeholder";
    private static final int ACQUIRE_COST = 1;

    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private final List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private String searchQuery = "";
    private String selectedRecipeId;

    private Ref<EntityStore> playerRef_ref;
    private Store<EntityStore> playerStore;

    public BlueprintSelectionPage(@NonNull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventPayload.CODEC);
        loadRecipes();
    }

    private void loadRecipes() {
        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;
            String id = recipe.getId();
            if (id.startsWith("Blueprint_")) continue;

            MaterialQuantity output = recipe.getPrimaryOutput();
            if (output == null) continue;
            String outputItemId = output.getItemId();
            if (outputItemId == null) continue;

            Item outputItem = Item.getAssetMap().getAsset(outputItemId);
            if (outputItem == null || outputItem.getBlockId() == null) continue;

            allRecipes.add(new RecipeEntry(id, outputItemId, outputItem.getBlockId(), true));
        }
        allRecipes.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.recipeId, b.recipeId));
        applyFilter();
    }

    private void applyFilter() {
        filteredRecipes.clear();
        String query = searchQuery.toLowerCase();
        for (RecipeEntry entry : allRecipes) {
            if (query.isEmpty()
                    || entry.recipeId.toLowerCase().contains(query)
                    || entry.blockTypeId.toLowerCase().contains(query)) {
                filteredRecipes.add(entry);
            }
        }
    }

    @Override
    public void build(@NonNull Ref<EntityStore> ref,
                      @NonNull UICommandBuilder cmd,
                      @NonNull UIEventBuilder evt,
                      @NonNull Store<EntityStore> store) {

        this.playerRef_ref = ref;
        this.playerStore = store;

        // Load custom .ui template — no appendInline()
        cmd.append("Pages/BlueprintBench/BlueprintBenchPage.ui");

        // Bind search input
        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );

        // Populate recipe list, detail panel, and placeholder slots
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

        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim();
            applyFilter();
            this.selectedRecipeId = null;
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
            withAffordability.add(new RecipeEntry(entry.recipeId, entry.outputItemId, entry.blockTypeId, affordable));
        }

        // Sort affordable above unaffordable, preserving alphabetical within each group
        withAffordability.sort(Comparator.comparing((RecipeEntry e) -> !e.affordable)
                .thenComparing(e -> e.recipeId, String.CASE_INSENSITIVE_ORDER));

        int showing = Math.min(withAffordability.size(), PAGE_SIZE);
        for (int i = 0; i < showing; i++) {
            RecipeEntry entry = withAffordability.get(i);

            cmd.append("#RecipeGrid", "Pages/BlueprintBench/RecipeIconCell.ui");
            cmd.set("#RecipeGrid[" + i + "].#Icon.ItemId", entry.outputItemId);

            if (entry.recipeId.equals(this.selectedRecipeId)) {
                cmd.set("#RecipeGrid[" + i + "].Style", CELL_STYLE_SELECTED);
            } else if (!entry.affordable) {
                cmd.set("#RecipeGrid[" + i + "].Style", CELL_STYLE_UNAFFORDABLE);
            }

            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#RecipeGrid[" + i + "]",
                    EventData.of("RecipeId", entry.recipeId)
            );
        }

        // Update count label
        String countText = filteredRecipes.size() + " recipes";
        if (showing < filteredRecipes.size()) {
            countText = showing + " / " + filteredRecipes.size() + " recipes";
        }
        cmd.set("#CountLabel.Text", countText);
    }

    private void updateDetailPanel(UICommandBuilder cmd) {
        if (selectedRecipeId != null) {
            RecipeEntry entry = findEntry(selectedRecipeId);
            if (entry != null) {
                cmd.set("#OutputIcon.ItemId", entry.outputItemId);
                cmd.set("#OutputName.Text", entry.blockTypeId);
                cmd.set("#CostSummary.Text", buildCostString(entry.recipeId));
                cmd.set("#StatusMessage.Text", "");
                return;
            }
        }
        cmd.set("#OutputIcon.ItemId", "");
        cmd.set("#OutputName.Text", "No recipe selected");
        cmd.set("#CostSummary.Text", "");
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

    private String buildCostString(String recipeId) {
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) return "";

        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) return "Free";

        StringBuilder sb = new StringBuilder("Cost: ");
        for (int i = 0; i < inputs.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(inputs[i].getQuantity()).append("× ").append(inputs[i].getItemId());
        }
        return sb.toString();
    }

    @Nullable
    private RecipeEntry findEntry(String recipeId) {
        for (RecipeEntry entry : allRecipes) {
            if (entry.recipeId.equals(recipeId)) return entry;
        }
        return null;
    }

    private record RecipeEntry(String recipeId, String outputItemId, String blockTypeId, boolean affordable) {}

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (e, s) -> e.searchQuery = s, e -> e.searchQuery).add()
                .append(new KeyedCodec<>("RecipeId", Codec.STRING), (e, s) -> e.recipeId = s, e -> e.recipeId).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .build();

        String searchQuery;
        String recipeId;
        String action;
    }
}
