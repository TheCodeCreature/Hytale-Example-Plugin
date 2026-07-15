package com.CodeCreature.ui.ingredienttree;

import com.CodeCreature.ui.bench.RecipeFilterPipeline;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

public class IngredientTreeGridController {

    private enum ElementKind { HEADER, GRID_CONTAINER, ITEM_BUTTON }

    private record ElementInfo(String nodeId, NodeType nodeType, ElementKind kind, int containerIdx, int leafIdx) {}

    private final IngredientTree tree;
    private final IngredientSelectionModel selection;

    private final Set<String> expandedGroups = new HashSet<>();

    private int totalContainerElements;
    private final List<ElementInfo> elements = new ArrayList<>();

    public IngredientTreeGridController(IngredientTree tree) {
        this.tree = tree;
        this.selection = new IngredientSelectionModel(tree);

        // All groups start collapsed
    }

    public void buildUI(UICommandBuilder cmd, UIEventBuilder evt) {
        int containerIdx = 0;

        for (IngredientGroup group : tree.getGroups()) {
            // Append group header
            cmd.append("#IngredientTreeContainer", "Pages/StencilBook/Components/IngredientGroupHeader.ui");
            elements.add(new ElementInfo(group.getId(), NodeType.META_GROUP, ElementKind.HEADER, containerIdx, -1));

            String headerSel = "#IngredientTreeContainer[" + containerIdx + "]";
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged,
                    headerSel + " #CheckboxIcon",
                    EventData.of("Action", "IngredientCheckbox:" + group.getId()));
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                    headerSel + " #GroupHeaderBtn",
                    EventData.of("Action", "IngredientExpand:" + group.getId()));
            containerIdx++;

            // Append wrap grid container for this group's leaf buttons
            cmd.append("#IngredientTreeContainer", "Pages/StencilBook/Components/IngredientButtonGrid.ui");
            int gridContainerIdx = containerIdx;
            elements.add(new ElementInfo(group.getId(), NodeType.META_GROUP, ElementKind.GRID_CONTAINER, containerIdx, -1));
            containerIdx++;

            // Append leaf buttons into the wrap grid
            String gridSel = "#IngredientTreeContainer[" + gridContainerIdx + "] #GridCells";
            int leafIdx = 0;
            for (IngredientResourceType rt : group.getChildren()) {
                String template = "Pages/StencilBook/Components/ExactItemFilterButton.ui";
                ElementKind kind = ElementKind.ITEM_BUTTON;

                cmd.append(gridSel, template);
                elements.add(new ElementInfo(rt.getId(), NodeType.RESOURCE_TYPE, kind, gridContainerIdx, leafIdx));

                String rtSel = gridSel + "[" + leafIdx + "]";
                String btnId = "#Btn";
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        rtSel + " " + btnId,
                        EventData.of("Action", "IngredientToggle:" + rt.getId()));
                leafIdx++;
            }
        }
        this.totalContainerElements = containerIdx;

        DebugLogger.log(INGREDIENT_TREE, Level.FINE, () -> "Ingredient tree UI built: " + totalContainerElements + " container elements, "
                + elements.size() + " total tracked elements (" + tree.getGroups().size() + " groups)");

        updateUI(cmd);
    }

    public void updateUI(UICommandBuilder cmd) {
        for (ElementInfo info : elements) {
            IngredientTreeNode node = tree.findNode(info.nodeId);
            if (node == null) continue;

            switch (info.kind) {
                case HEADER -> {
                    String sel = "#IngredientTreeContainer[" + info.containerIdx + "]";
                    CheckState state = selection.getState(info.nodeId);
                    cmd.set(sel + " #CheckboxIcon.Value", state != CheckState.NONE);

                    IngredientGroup group = (IngredientGroup) node;
                    cmd.set(sel + " #GroupHeaderLabel.Text", group.getDisplayName() + " (" + group.getChildCount() + ")");
                    if (group.getDisplayItemId() != null) {
                        cmd.set(sel + " #GroupHeaderIcon.ItemId", group.getDisplayItemId());
                    }
                    cmd.set(sel + ".Visible", true);
                }
                case GRID_CONTAINER -> {
                    String sel = "#IngredientTreeContainer[" + info.containerIdx + "]";
                    boolean expanded = expandedGroups.contains(info.nodeId);
                    cmd.set(sel + ".Visible", expanded);
                }
                case ITEM_BUTTON -> {
                    String sel = "#IngredientTreeContainer[" + info.containerIdx + "] #GridCells[" + info.leafIdx + "]";
                    IngredientResourceType rt = (IngredientResourceType) node;
                    boolean selected = selection.getState(info.nodeId) == CheckState.ALL;
                    if (rt.getDisplayItemId() != null) {
                        cmd.set(sel + " #FilterItemIcon.ItemId", rt.getDisplayItemId());
                    }
                    cmd.set(sel + " #ActiveOverlay.Visible", selected);
                    cmd.set(sel + ".TooltipText", rt.getDisplayName());
                    cmd.set(sel + ".Visible", true);
                }
            }
        }
    }

    public void handleEvent(String action) {
        if (action.startsWith("IngredientCheckbox:")) {
            String nodeId = action.substring("IngredientCheckbox:".length());
            selection.toggle(nodeId);
        } else if (action.startsWith("IngredientExpand:")) {
            String nodeId = action.substring("IngredientExpand:".length());
            if (!expandedGroups.remove(nodeId)) {
                expandedGroups.add(nodeId);
            }
        } else if (action.startsWith("IngredientToggle:")) {
            String nodeId = action.substring("IngredientToggle:".length());
            selection.toggle(nodeId);
        }
    }

    @Nullable
    public RecipeFilterPipeline.ResourceTypeChecker getFilterPredicate() {
        Set<String> selectedRTs = selection.getSelectedResourceTypeIds();
        Set<String> selectedItems = selection.getSelectedDirectItemIds();

        if (selectedRTs.isEmpty() && selectedItems.isEmpty()) return null;

        return recipe -> {
            CraftingRecipe cr = CraftingRecipe.getAssetMap().getAsset(recipe.recipeId());
            if (cr == null) return false;
            if (!selectedRTs.isEmpty() && ResourceTypeResolver.recipeMatchesAnyResourceType(cr, selectedRTs)) return true;
            if (!selectedItems.isEmpty() && cr.getInput() != null) {
                for (MaterialQuantity input : cr.getInput()) {
                    if (input.getItemId() != null && selectedItems.contains(input.getItemId())) return true;
                }
            }
            return false;
        };
    }

    public void clearAll() {
        selection.clearAll();
    }

    public boolean hasAnySelection() {
        return selection.hasAnySelection();
    }

    public IngredientSelectionModel getSelectionModel() {
        return selection;
    }

    public Set<String> getSelectedNodeIds() {
        return selection.getSelectedNodeIds();
    }

    public void restoreSelection(Collection<String> nodeIds) {
        selection.restoreSelection(nodeIds);
    }
}
