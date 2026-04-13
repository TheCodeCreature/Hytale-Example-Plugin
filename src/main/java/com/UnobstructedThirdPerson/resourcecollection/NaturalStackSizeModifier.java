package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.lang.reflect.Field;

/**
 * Adjusts max stack sizes for natural resource items at startup.
 * Uses {@link NaturalResourceRegistry} to determine which items are natural resources.
 */
public final class NaturalStackSizeModifier {

    private NaturalStackSizeModifier() {}

    private static void log(String msg) {
        System.out.println("[NaturalStack] " + msg);
    }

    /**
     * Multiplies the maxStack of all natural resource items by
     * {@link ResourceConstants#RESOURCE_MULTIPLIER}.
     * Must be called after {@link NaturalResourceRegistry#init()}.
     */
    public static void apply() {
        int multiplier = ResourceConstants.RESOURCE_MULTIPLIER;
        Field maxStackField;
        try {
            maxStackField = Item.class.getDeclaredField("maxStack");
            maxStackField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find maxStack field on Item: " + e.getMessage());
            return;
        }

        int boosted = 0;
        for (String itemId : NaturalResourceRegistry.getNaturalItemIds()) {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) continue;

            try {
                int current = maxStackField.getInt(item);
                if (current > 1) {
                    maxStackField.setInt(item, current * multiplier);
                    boosted++;
                }
            } catch (IllegalAccessException e) {
                log("ERROR: Could not set maxStack for " + itemId + ": " + e.getMessage());
            }
        }
        log("Boosted stack size (" + multiplier + "x) for " + boosted + " natural resource items");
    }
}
