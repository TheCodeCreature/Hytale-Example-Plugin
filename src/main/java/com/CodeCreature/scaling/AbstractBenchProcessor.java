package com.CodeCreature.scaling;

import com.CodeCreature.crafting.RawMaterialRequirement;
import com.CodeCreature.crafting.RecipeTreeResolver;
import com.CodeCreature.registry.BenchRecipeRegistries;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.MultipleItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

/**
 * Abstract base for bench-category processors. Provides the shared
 * recipe-block processing logic (resolve inputs → build drops → set
 * breaking config). Subclasses only need to declare their
 * {@link #preferNatural()} preference.
 *
 * <p>The processing logic is identical across all bench types —
 * the difference is entirely in the {@code boolean preferNatural} passed to
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
                                  @Nonnull AssetFieldAccessor f) {
        int modified = 0;
        int skipped = 0;
        List<ItemDropList> localSyntheticDropLists = new ArrayList<>();

        for (String btId : blockTypeIds) {
            BlockType bt = BlockType.getAssetMap().getAsset(btId);
            if (bt == null) { skipped++; continue; }

            CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(btId);
            if (recipe == null) { skipped++; continue; }

            BlockGathering originalGathering = bt.getGathering();

            record ResolvedIngredient(String itemId, int dropQty) {}
            List<RawMaterialRequirement> rawCost = RecipeTreeResolver.resolveRecipeToRaw(recipe, preferNatural());
            if (rawCost.isEmpty()) { skipped++; continue; }

            List<ResolvedIngredient> resolved = new ArrayList<>();
            for (RawMaterialRequirement raw : rawCost) {
                resolved.add(new ResolvedIngredient(raw.itemId(), raw.quantity()));
            }
            if (resolved.isEmpty()) { skipped++; continue; }

            // Determine if this block uses the soft drop path.
            // A block is "soft" if its gathering has a non-null SoftBlockDropType.
            // Blocks with no original gathering (e.g. decorative blocks like Deco_Rope)
            // also need the soft path since they're breakable without tools.
            boolean isSoftBlock = originalGathering == null
                    || originalGathering.isSoft();

            // Preserve tool requirements from existing breaking config
            BlockBreakingDropType existing = originalGathering != null
                    ? originalGathering.getBreaking() : null;
            String gatherType = existing != null ? existing.getGatherType() : null;
            int quality = existing != null ? existing.getQuality() : 0;

            try {
                // Clone gathering to avoid shared-instance contamination —
                // child block types that inherit from a parent share the same
                // BlockGathering Java object. Mutating it would affect all siblings.
                // If no gathering exists (e.g. decorative blocks), create a new one.
                BlockGathering gathering;
                if (originalGathering != null) {
                    gathering = DropScaler.cloneGathering(originalGathering);
                } else {
                    gathering = DropScaler.createEmptyGathering();
                }
                f.blockTypeGathering.set(bt, gathering);

                // Always build a synthetic drop list — this is the single source
                // of truth for what drops and how many. Both breaking and soft
                // configs reference it by ID, so quantities are defined once.
                String dlId = "Plugin_RecipeDrop_" + btId;
                SingleItemDropContainer[] containers = new SingleItemDropContainer[resolved.size()];
                for (int i = 0; i < resolved.size(); i++) {
                    ResolvedIngredient ing = resolved.get(i);
                    ItemDrop drop = new ItemDrop(ing.itemId(), null, ing.dropQty(), ing.dropQty());
                    containers[i] = new SingleItemDropContainer(drop, 100.0);
                }
                if (resolved.size() == 1) {
                    localSyntheticDropLists.add(new ItemDropList(dlId, containers[0]));
                } else {
                    MultipleItemDropContainer multi = new MultipleItemDropContainer(
                            containers, 100.0, 1, 1);
                    localSyntheticDropLists.add(new ItemDropList(dlId, multi));
                }

                // Point breaking config at the drop list
                BlockBreakingDropType newBreaking = new BlockBreakingDropType(
                        gatherType, quality, 1, null, dlId);
                f.gatheringBreaking.set(gathering, newBreaking);

                // For soft blocks, also point soft config at the same drop list
                if (isSoftBlock) {
                    SoftBlockDropType softDrop = DropScaler.createEmptySoftDrop();
                    f.softDropListId.set(softDrop, dlId);
                    f.gatheringSoft.set(gathering, softDrop);
                }

                modified++;
            } catch (Exception e) {
                DebugLogger.log(SCALING, Level.WARNING, "[" + (preferNatural() ? "Natural" : "Synthetic") + "Processor] ERROR processing " + btId + ": " + e.getMessage());
                skipped++;
            }
        }

        return new ProcessResult(modified, skipped, localSyntheticDropLists);
    }
}
