package com.CodeCreature.ui.bench.render;

/**
 * @node    StencilCenterRenderer
 * @wiki    docs/wiki/StencilBook/StencilCenterRenderer.md
 * @intent  Provides a shared lifecycle contract for stencil center rendering so
 *          the production page can switch between legacy fixed-grid and grouped
 *          renderers without changing filtering, selection, or details logic.
 * @wave    1 (dual renderer migration)
 * @status  Wave 1 - contract implemented and used by production page
 * @do-not  Move filter or selection authority into renderer implementations.
 */

import com.CodeCreature.ui.bench.RecipeFilterPipeline;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import java.util.List;

public interface StencilCenterRenderer {

    /** @intent Appends renderer-owned UI components into the center container.
     *  @wave   1
     *  @node   StencilCenterRenderer#appendStructure */
    void appendStructure(UICommandBuilder cmd);

    /** @intent Binds one-time UI events for all interactive center cells.
     *  @wave   1
     *  @node   StencilCenterRenderer#buildBindings */
    void buildBindings(UIEventBuilder evt);

    /** @intent Updates center tile state from already-filtered displayed recipes.
     *  @wave   1
     *  @node   StencilCenterRenderer#updateUI */
    void updateUI(UICommandBuilder cmd,
                  List<RecipeFilterPipeline.TaggedRecipe> displayedRecipes,
                  String selectedRecipeId);

    /** @intent Clears center renderer UI state during page dismiss.
     *  @wave   1
     *  @node   StencilCenterRenderer#clearOnDismiss */
    void clearOnDismiss(UICommandBuilder cmd);
}