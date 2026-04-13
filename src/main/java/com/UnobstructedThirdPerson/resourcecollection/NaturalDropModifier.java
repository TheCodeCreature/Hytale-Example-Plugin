package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Multiplies the drop quantity of natural blocks that have a {@code breaking}
 * config by {@link ResourceConstants#RESOURCE_MULTIPLIER}.
 * Soft-only blocks (e.g. dirt, grass) are left untouched — their vanilla
 * drops are handled by the soft config which has no quantity field.
 *
 * <p>To avoid contaminating non-natural blocks that share the same
 * {@link BlockBreakingDropType} or {@link BlockGathering} Java instance
 * (due to Hytale's asset inheritance), this modifier creates private
 * copies of both objects before applying the multiplier.
 *
 * Must be called after {@link NaturalResourceRegistry#init()}.
 */
public final class NaturalDropModifier {

    private NaturalDropModifier() {}

    private static void log(String msg) {
        System.out.println("[NaturalDropMod] " + msg);
    }

    public static void apply() {
        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;

        Field breakingField;
        Field gatheringField;
        try {
            breakingField = BlockGathering.class.getDeclaredField("breaking");
            breakingField.setAccessible(true);
            gatheringField = BlockType.class.getDeclaredField("gathering");
            gatheringField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find required field: " + e.getMessage());
            return;
        }

        int modified = 0;
        int skipped = 0;

        for (String blockTypeId : NaturalResourceRegistry.getNaturalBlockTypes()) {
            BlockType bt = BlockType.getAssetMap().getAssetMap().get(blockTypeId);
            if (bt == null) continue;

            // Skip blocks that have a crafting recipe — RecipeDropModifier
            // handles those. Without this, recipe blocks that slip into
            // NaturalResourceRegistry would get 12x of themselves.
            if (BlockRecipeRegistry.hasRecipe(blockTypeId)) {
                skipped++;
                continue;
            }

            BlockGathering gathering = bt.getGathering();
            if (gathering == null) {
                skipped++;
                continue;
            }

            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking == null) {
                skipped++;
                continue;
            }

            // Only scale direct itemId drops. Drop lists use quantity as a
            // roll count — IngredientDropModifier handles scaling individual
            // ItemDrop quantities inside those lists instead.
            if (breaking.getDropListId() != null) {
                skipped++;
                continue;
            }

            int current = breaking.getQuantity();
            if (current <= 0) {
                skipped++;
                continue;
            }

            try {
                // Create a private copy of the breaking with the multiplied quantity
                BlockBreakingDropType newBreaking = new BlockBreakingDropType(
                        breaking.getGatherType(), breaking.getQuality(),
                        current * multiplier,
                        breaking.getItemId(), breaking.getDropListId());

                // Clone the gathering so non-natural blocks sharing the
                // original instance are not affected
                BlockGathering newGathering = cloneGathering(gathering);
                breakingField.set(newGathering, newBreaking);

                // Assign the private gathering to this BlockType
                gatheringField.set(bt, newGathering);
                modified++;
            } catch (Exception e) {
                log("ERROR cloning gathering for " + blockTypeId + ": " + e.getMessage());
            }
        }

        log("Multiplied drop quantity (" + multiplier + "x) for " + modified
                + " natural blocks (" + skipped + " skipped — no breaking drop or quantity=0)");
    }

    static BlockGathering cloneGathering(BlockGathering original) throws Exception {
        Constructor<BlockGathering> ctor = BlockGathering.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        BlockGathering clone = ctor.newInstance();
        for (Field f : BlockGathering.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            f.set(clone, f.get(original));
        }
        return clone;
    }
}
