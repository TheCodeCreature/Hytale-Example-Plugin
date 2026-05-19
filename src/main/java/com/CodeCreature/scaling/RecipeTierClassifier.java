package com.CodeCreature.scaling;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Classifies items as "crafted intermediates" vs "raw materials" for
 * selective recipe cost scaling. Only raw material inputs should be
 * scaled by the 12× multiplier; crafted intermediates keep their
 * vanilla quantities to avoid exponential cost explosion on multi-tier
 * recipes.
 *
 * <p>An item is "crafted" if it appears as the primary output of any
 * non-Salvage recipe AND is not also a natural drop. Items with dual
 * identity (craftable + natural drop) are treated as raw.
 *
 * <p>Thread-safe: the set is immutable after {@link #init()}.
 */
public final class RecipeTierClassifier {

    private static Set<String> craftedItemIds = Collections.emptySet();

    private RecipeTierClassifier() {}

    private static final Logger LOGGER = Logger.getLogger("RecipeTierClassifier");

    private static void log(String msg) {
        LOGGER.info("[RecipeTierClassifier] " + msg);
    }

    /**
     * Scans all crafting recipes and builds the set of crafted item IDs.
     * Must be called after {@link NaturalResourceRegistry#init()} so that
     * natural item classification is available for dual-identity filtering.
     */
    public static void init() {
        Set<String> crafted = new HashSet<>();

        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;
            if (recipe.getId().startsWith("Salvage")) continue;

            MaterialQuantity output = recipe.getPrimaryOutput();
            if (output == null) continue;

            String itemId = output.getItemId();
            if (itemId != null && !itemId.isEmpty()) {
                crafted.add(itemId);
            }
        }

        // Remove dual-identity items: if an item is also a natural drop,
        // treat it as raw material (e.g. items that can be both crafted
        // and dropped from natural blocks)
        crafted.removeIf(NaturalResourceRegistry::isNaturalItem);

        craftedItemIds = Collections.unmodifiableSet(crafted);
        log("Initialized: " + craftedItemIds.size() + " crafted item IDs classified");
    }

    /**
     * Returns {@code true} if the given item ID is a crafted intermediate
     * (output of a non-Salvage recipe, not also a natural drop).
     */
    public static boolean isCraftedItem(String itemId) {
        return craftedItemIds.contains(itemId);
    }

    /**
     * Returns {@code true} if the given recipe input should be scaled
     * (i.e., it represents a raw material, not a crafted intermediate).
     *
     * <ul>
     *   <li>ResourceTypeId-based inputs: returns true if any concrete item
     *       matching the resource type is a natural drop</li>
     *   <li>Direct itemId inputs: returns true if the item is NOT in the
     *       crafted set</li>
     * </ul>
     */
    public static boolean isRawInput(MaterialQuantity mq) {
        // ResourceTypeId-based input: check if any matching item is natural
        String resourceTypeId = mq.getResourceTypeId();
        if (resourceTypeId != null) {
            for (var entry : Item.getAssetMap().getAssetMap().entrySet()) {
                Item item = entry.getValue();
                if (item == null || item.getResourceTypes() == null) continue;
                boolean matches = Arrays.stream(item.getResourceTypes())
                        .anyMatch(rt -> resourceTypeId.equals(rt.id));
                if (matches && NaturalResourceRegistry.isNaturalItem(entry.getKey())) {
                    return true;
                }
            }
            return false;
        }

        // Direct itemId input: raw if not crafted
        String itemId = mq.getItemId();
        if (itemId != null && !itemId.isEmpty()) {
            return !isCraftedItem(itemId);
        }

        // Unknown input type — default to scaling
        return true;
    }
}
