package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.MultipleItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Abstract base for bench-category processors. Provides the shared
 * recipe-block processing logic (resolve inputs → build drops → set
 * breaking config). Subclasses only need to declare their
 * {@link BenchCategory}.
 *
 * <p>The processing logic is identical across all bench categories —
 * the difference is entirely in the {@link BenchCategory} passed to
 * {@link ResourceTypeResolver#resolveInputItemId}, which controls
 * the natural/non-natural preference. If a future bench type needs
 * completely different drop logic, it can override {@link #process}.
 *
 * <p>Thread-safe: each invocation produces an independent
 * {@link ProcessResult} and only mutates block assets that belong
 * to its partitioned block set.
 */
public abstract class AbstractBenchProcessor implements BenchCategoryProcessor {

    @Override
    @Nonnull
    public ProcessResult process(@Nonnull Set<String> blockTypeIds,
                                  @Nonnull AssetFieldAccessor f,
                                  @Nonnull Set<String> ingredientItemIds) {
        int modified = 0;
        int skipped = 0;
        List<ItemDropList> localSyntheticDropLists = new ArrayList<>();

        for (String btId : blockTypeIds) {
            BlockType bt = BlockType.getAssetMap().getAsset(btId);
            if (bt == null) { skipped++; continue; }

            CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(btId);
            if (recipe == null) { skipped++; continue; }

            BlockGathering originalGathering = bt.getGathering();
            if (originalGathering == null) { skipped++; continue; }

            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length ==  0) { skipped++; continue; }

            MaterialQuantity primaryOut = recipe.getPrimaryOutput();
            int outputQty = (primaryOut != null && primaryOut.getQuantity() > 0)
                    ? primaryOut.getQuantity() : 1;

            record ResolvedIngredient(String itemId, int dropQty) {}
            List<ResolvedIngredient> resolved = new ArrayList<>();
            for (MaterialQuantity mq : inputs) {
                if (mq == null) continue;
                String itemId = ResourceTypeResolver.resolveInputItemId(mq, category());
                if (itemId == null) continue;
                int inputQty = mq.getQuantity(); // already 12x scaled from Phase 1
                int dropQty = Math.max(1, inputQty / outputQty);
                resolved.add(new ResolvedIngredient(itemId, dropQty));
            }
            if (resolved.isEmpty()) { skipped++; continue; }

            // Preserve tool requirements from existing breaking config
            BlockBreakingDropType existing = originalGathering.getBreaking();
            String gatherType = existing != null ? existing.getGatherType() : null;
            int quality = existing != null ? existing.getQuality() : 0;

            try {
                // Clone gathering to avoid shared-instance contamination —
                // child block types that inherit from a parent share the same
                // BlockGathering Java object. Mutating it would affect all siblings.
                BlockGathering gathering = DropScaler.cloneGathering(originalGathering);
                f.blockTypeGathering.set(bt, gathering);

                if (resolved.size() == 1) {
                    ResolvedIngredient ing = resolved.getFirst();
                    BlockBreakingDropType newBreaking = new BlockBreakingDropType(
                            gatherType, quality, ing.dropQty(), ing.itemId(), null);
                    f.gatheringBreaking.set(gathering, newBreaking);
                } else {
                    String dlId = "Plugin_RecipeDrop_" + btId;
                    SingleItemDropContainer[] containers = new SingleItemDropContainer[resolved.size()];
                    for (int i = 0; i < resolved.size(); i++) {
                        ResolvedIngredient ing = resolved.get(i);
                        ItemDrop drop = new ItemDrop(ing.itemId(), null, ing.dropQty(), ing.dropQty());
                        containers[i] = new SingleItemDropContainer(drop, 100.0);
                    }
                    MultipleItemDropContainer multi = new MultipleItemDropContainer(
                            containers, 100.0, 1, 1);
                    localSyntheticDropLists.add(new ItemDropList(dlId, multi));

                    BlockBreakingDropType newBreaking = new BlockBreakingDropType(
                            gatherType, quality, 1, null, dlId);
                    f.gatheringBreaking.set(gathering, newBreaking);
                }
                modified++;
            } catch (Exception e) {
                System.out.println("[" + category() + "Processor] ERROR processing " + btId + ": " + e.getMessage());
                skipped++;
            }
        }

        return new ProcessResult(modified, skipped, localSyntheticDropLists);
    }
}
