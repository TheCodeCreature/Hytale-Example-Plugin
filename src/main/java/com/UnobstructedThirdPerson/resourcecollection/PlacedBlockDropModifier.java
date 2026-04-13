package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Sets {@code useDefaultDropWhenPlaced = true} on every natural block's
 * gathering config so that player-placed instances drop 1x of themselves
 * while world-generated instances keep 12x drops.
 *
 * <p>How it works at runtime (inside {@code BlockHarvestUtils.performBlockDamage}):
 * <ol>
 *   <li>When a player places a block with this flag, the engine marks it as
 *       "deco" via {@code BlockPhysics.markDeco()} (value 15).</li>
 *   <li>When any block with this flag is broken, the engine checks
 *       {@code BlockPhysics.isDeco()} on that position.</li>
 *   <li>If the block IS deco (player-placed), the engine nulls out
 *       {@code itemId} and {@code dropListId}, causing the fallback path
 *       that drops {@code blockType.getItem()} at quantity 1.</li>
 *   <li>If the block is NOT deco (world-generated), the modified 12x
 *       breaking/drop config is used as normal.</li>
 * </ol>
 *
 * <p>To avoid contaminating non-natural blocks that share the same
 * {@link BlockGathering} Java instance (due to Hytale's asset inheritance),
 * this modifier creates a private copy of the gathering before setting the flag.
 *
 * <p>Must be called after {@link NaturalResourceRegistry#init()} and after
 * all drop modifiers (NaturalDropModifier, IngredientDropModifier) have run,
 * though the order relative to those doesn't actually matter since this
 * only sets a boolean flag.
 */
public final class PlacedBlockDropModifier {

    private PlacedBlockDropModifier() {}

    private static void log(String msg) {
        System.out.println("[PlacedBlockDrop] " + msg);
    }

    public static void apply() {
        Field useDefaultDropField;
        Field gatheringField;
        try {
            useDefaultDropField = BlockGathering.class.getDeclaredField("useDefaultDropWhenPlaced");
            useDefaultDropField.setAccessible(true);
            gatheringField = BlockType.class.getDeclaredField("gathering");
            gatheringField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find required field: " + e.getMessage());
            return;
        }

        int modified = 0;
        int skipped = 0;

        // Track gatherings that have already been privatized by this run
        // so that two natural blocks sharing the same original gathering
        // both end up on the same (already private) clone.
        Set<BlockGathering> alreadyPrivatized =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        for (String blockTypeId : NaturalResourceRegistry.getNaturalBlockTypes()) {
            BlockType bt = BlockType.getAssetMap().getAssetMap().get(blockTypeId);
            if (bt == null) continue;

            // Skip recipe blocks — they are handled by RecipeDropModifier
            if (BlockRecipeRegistry.hasRecipe(blockTypeId)) {
                skipped++;
                continue;
            }

            BlockGathering gathering = bt.getGathering();
            if (gathering == null) {
                skipped++;
                continue;
            }

            try {
                boolean current = useDefaultDropField.getBoolean(gathering);
                if (current) {
                    // Already true (set by asset definition or a previous privatization)
                    skipped++;
                    continue;
                }

                // Privatize the gathering if not already done, then set the flag
                if (!alreadyPrivatized.contains(gathering)) {
                    BlockGathering newGathering =
                            NaturalDropModifier.cloneGathering(gathering);
                    gatheringField.set(bt, newGathering);
                    gathering = newGathering;
                    alreadyPrivatized.add(gathering);
                }

                useDefaultDropField.setBoolean(gathering, true);
                modified++;
            } catch (Exception e) {
                log("ERROR setting useDefaultDropWhenPlaced for " + blockTypeId + ": " + e.getMessage());
            }
        }

        log("Set useDefaultDropWhenPlaced=true for " + modified
                + " natural blocks (" + skipped + " skipped — no gathering or already set)");
    }
}
