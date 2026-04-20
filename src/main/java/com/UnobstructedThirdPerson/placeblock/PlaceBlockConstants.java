package com.UnobstructedThirdPerson.placeblock;

import java.util.Set;

/**
 * Constants for the PlaceBlock item system.
 *
 * <p>Defines the three item variant IDs (one per quality state),
 * BSON metadata keys for recipe storage, and a convenience set
 * for quick identity checks.</p>
 */
public final class PlaceBlockConstants {

    private PlaceBlockConstants() {}

    // ═══════════════════════════════════════════════════════════════
    //  Item variant IDs — must match JSON asset file names
    // ═══════════════════════════════════════════════════════════════

    /** Default state: no recipe assigned. Quality = "Tool" (blue highlight). */
    public static final String ITEM_DEFAULT = "PlaceBlock_Default";

    /** Armed state: recipe assigned, resources available. Quality = "Uncommon" (green highlight). */
    public static final String ITEM_ARMED = "PlaceBlock_Armed";

    /** No-resources state: recipe assigned, resources insufficient. Quality = "Developer" (red highlight). */
    public static final String ITEM_NO_RESOURCES = "PlaceBlock_NoResources";

    /** All PlaceBlock item IDs for quick identity checks. */
    public static final Set<String> ALL_PLACEBLOCK_IDS = Set.of(
            ITEM_DEFAULT, ITEM_ARMED, ITEM_NO_RESOURCES
    );

    // ═══════════════════════════════════════════════════════════════
    //  BSON metadata keys — stored on ItemStack
    // ═══════════════════════════════════════════════════════════════

    /** BSON key: the CraftingRecipe asset ID (e.g., "Builders_WoodPlanks_Oak"). */
    public static final String META_RECIPE_ID = "placeblock_recipe_id";

    /** BSON key: human-readable recipe name for tooltip display. */
    public static final String META_RECIPE_NAME = "placeblock_recipe_name";
}
