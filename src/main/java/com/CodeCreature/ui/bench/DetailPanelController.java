package com.CodeCreature.ui.bench;

/**
 * @node    DetailPanelController
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Renders stencil detail-panel ingredient costs from Wave 2 presentation-oriented
 *          affordability data instead of inferring semantics from a resolved concrete item ID.
 * @wave    2 (affordability and UI projection migration)
 * @status  Wave 2 - detail panel now consumes facade-projected ingredient presentation
 * @do-not  Reintroduce semantic coupling to one representative concrete item here.
 *          Add planner or raw-cost policy here.
 */

import com.CodeCreature.crafting.CraftingAffordabilityFacade;
import com.CodeCreature.registry.FilteredRecipeEntry;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nullable;
import java.util.List;
/**
 * Owns detail panel rendering for the Stencil Crafting selection page.
 * Renders the output icon, name, and per-ingredient cost grid with
 * affordability coloring.
 */
public class DetailPanelController {

    static final int COST_CELLS_PER_ROW = 3;
    static final int MAX_COST_ROWS = 10;
    static final int MAX_COST_CELLS = COST_CELLS_PER_ROW * MAX_COST_ROWS;

    private static final Value<String> COST_QTY_NORMAL =
            Value.ref("Styles/Labels.ui", "CostQuantityStyle");
    private static final Value<String> COST_QTY_INSUFFICIENT =
            Value.ref("Styles/Labels.ui", "CostQuantityInsufficientStyle");

    private static final Value<String> DETAIL_LABEL_NORMAL =
            Value.ref("Styles/Labels.ui", "DetailLabelStyle");
    private static final Value<String> DETAIL_LABEL_MUTED =
            Value.ref("Styles/Labels.ui", "DetailLabelMutedStyle");

    private static final String OUTPUT_BG_NORMAL = "Common/Buttons/Tertiary.png";
    private static final String OUTPUT_BG_EMPTY = "Common/UnknownItemIcon.png";
    private static final String OUTPUT_BG_UNAFFORDABLE = "Common/Buttons/Destructive.png";

    public DetailPanelController() {
    }

    /**
     * Updates the detail panel for the given recipe selection state.
     *
     * @param cmd the UI command builder to write to
     * @param selectedRecipeId the currently selected recipe ID (null = empty state)
     * @param allRecipes list of all recipe entries for lookup
     * @param affordabilityMode current affordability mode
     * @param container player's combined inventory (null if unavailable)
     */
    public void updateUI(UICommandBuilder cmd,
                         @Nullable String selectedRecipeId,
                         List<StencilSelectionPage.RecipeEntry> allRecipes,
                         boolean checkInventory,
                         @Nullable CombinedItemContainer container) {
        if (selectedRecipeId != null) {
            StencilSelectionPage.RecipeEntry entry = findEntry(selectedRecipeId, allRecipes);
            if (entry != null) {
                String outputItemId = entry.outputItemId() == null ? "" : entry.outputItemId();
                String outputName = entry.blockTypeId() != null
                        ? entry.blockTypeId().replace('_', ' ')
                        : outputItemId;

                cmd.set("#OutputIcon.ItemId", outputItemId);
                cmd.set("#OutputName.Text", outputName);

                boolean allAffordable = true;
                int costIdx = 0;
                CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(entry.recipeId());
                if (recipe != null) {
                    FilteredRecipeEntry fe = RecipeFilterRegistry.getEntry(entry.recipeId());
                    boolean preferNatural = fe != null && fe.preferNatural();

                    List<CraftingAffordabilityFacade.DirectIngredientView> ingredients =
                        CraftingAffordabilityFacade.resolveDirectIngredients(recipe, preferNatural, container);

                    if (!ingredients.isEmpty()) {
                        for (CraftingAffordabilityFacade.DirectIngredientView ing : ingredients) {
                            if (costIdx >= MAX_COST_CELLS) break;
                            String sel = costCellSelector(costIdx);
                            String itemId = ing.presentation().iconItemId() == null ? "" : ing.presentation().iconItemId();
                            String genericIconPath = ing.presentation().genericIconPath();
                            boolean hasIconItemId = !itemId.isEmpty();
                            boolean useGenericIcon = !hasIconItemId && genericIconPath != null && !genericIconPath.isEmpty();
                            int requiredQty = ing.requiredQty();
                            boolean sufficient = ing.sufficient();
                            if (!sufficient) allAffordable = false;

                            cmd.set(sel + ".Visible", true);
                            cmd.set(sel + " #Icon.ItemId", useGenericIcon ? "" : itemId);
                            cmd.set(sel + " #GenericIcon.Visible", useGenericIcon);
                            cmd.set(sel + " #GenericIcon.Background", useGenericIcon ? genericIconPath : "");
                            cmd.set(sel + " #Qty.Text", Message.translation("server.ui.stencil.detail.quantityFormat").param("qty", requiredQty));
                            if (checkInventory) {
                                cmd.set(sel + " #Dim.Visible", !sufficient);
                                cmd.set(sel + " #Qty.Style", sufficient ? COST_QTY_NORMAL : COST_QTY_INSUFFICIENT);
                            } else {
                                cmd.set(sel + " #Dim.Visible", false);
                                cmd.set(sel + " #Qty.Style", COST_QTY_NORMAL);
                            }
                            costIdx++;
                        }
                    }
                }

                // Hide remaining cost cells and reset their state
                for (int i = costIdx; i < MAX_COST_CELLS; i++) {
                    String sel = costCellSelector(i);
                    cmd.set(sel + ".Visible", false);
                    cmd.set(sel + " #GenericIcon.Visible", false);
                    cmd.set(sel + " #GenericIcon.Background", "");
                    cmd.set(sel + " #Dim.Visible", false);
                    cmd.set(sel + " #Qty.Style", COST_QTY_NORMAL);
                }
                setCostRowVisibility(cmd, costIdx);

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
        cmd.set("#OutputName.Text", Message.translation("server.ui.stencil.detail.noRecipeSelected"));
        cmd.set("#OutputName.Style", DETAIL_LABEL_MUTED);
        cmd.set("#OutputFrame.Background", OUTPUT_BG_EMPTY);
        cmd.set("#OutputDim.Visible", false);
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            String sel = costCellSelector(i);
            cmd.set(sel + ".Visible", false);
            cmd.set(sel + " #GenericIcon.Visible", false);
            cmd.set(sel + " #GenericIcon.Background", "");
            cmd.set(sel + " #Dim.Visible", false);
            cmd.set(sel + " #Qty.Style", COST_QTY_NORMAL);
        }
        setCostRowVisibility(cmd, 0);
    }

    /**
     * Clears cost grid cells (used during dismiss to remove tooltip data).
     */
    public void clearUI(UICommandBuilder cmd) {
        for (int i = 0; i < MAX_COST_CELLS; i++) {
            cmd.set(costCellSelector(i) + ".Visible", false);
        }
        setCostRowVisibility(cmd, 0);
    }

    private static String costCellSelector(int costIdx) {
        int row = costIdx / COST_CELLS_PER_ROW;
        int col = costIdx % COST_CELLS_PER_ROW;
        return "#CostGrid[" + row + "] #CostRowCells[" + col + "]";
    }

    private static void setCostRowVisibility(UICommandBuilder cmd, int visibleCellCount) {
        int visibleRows = (visibleCellCount + COST_CELLS_PER_ROW - 1) / COST_CELLS_PER_ROW;
        for (int row = 0; row < MAX_COST_ROWS; row++) {
            cmd.set("#CostGrid[" + row + "].Visible", row < visibleRows);
        }
    }

    @Nullable
    private StencilSelectionPage.RecipeEntry findEntry(String recipeId, List<StencilSelectionPage.RecipeEntry> allRecipes) {
        for (StencilSelectionPage.RecipeEntry entry : allRecipes) {
            if (entry.recipeId().equals(recipeId)) return entry;
        }
        return null;
    }
}
