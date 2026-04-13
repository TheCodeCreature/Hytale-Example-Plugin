package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;

/**
 * Multiplies the drop quantity of natural blocks that have a {@code breaking}
 * config by {@link ResourceConstants#RESOURCE_MULTIPLIER}.
 * Soft-only blocks (e.g. dirt, grass) are left untouched — their vanilla
 * drops are handled by the soft config which has no quantity field.
 * Must be called after {@link NaturalResourceRegistry#init()}.
 */
public final class NaturalDropModifier {

    private NaturalDropModifier() {}

    private static void log(String msg) {
        System.out.println("[NaturalDropMod] " + msg);
    }

    public static void apply() {
        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;

        Field quantityField;
        try {
            quantityField = BlockBreakingDropType.class.getDeclaredField("quantity");
            quantityField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find quantity field on BlockBreakingDropType: " + e.getMessage());
            return;
        }

        int modified = 0;
        int skipped = 0;
        // Track already-modified instances to avoid double-multiplying shared configs
        Set<BlockBreakingDropType> alreadyModified = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (String blockTypeId : NaturalResourceRegistry.getNaturalBlockTypes()) {
            BlockType bt = BlockType.getAssetMap().getAssetMap().get(blockTypeId);
            if (bt == null) continue;

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

            if (alreadyModified.contains(breaking)) continue;

            try {
                int current = quantityField.getInt(breaking);
                if (current > 0) {
                    quantityField.setInt(breaking, current * multiplier);
                    alreadyModified.add(breaking);
                    modified++;
                } else {
                    skipped++;
                }
            } catch (IllegalAccessException e) {
                log("ERROR setting quantity for " + blockTypeId + ": " + e.getMessage());
            }
        }

        log("Multiplied drop quantity (" + multiplier + "x) for " + modified
                + " natural blocks (" + skipped + " skipped — no breaking drop or quantity=0)");
    }
}
