package com.CodeCreature.stencil;

/**
 * Immutable data holder for a single segment slot in the stencil radial menu.
 *
 * <p>Each segment displays an item icon and a label. The {@code index} is the
 * segment's position in the global dummy item list (not the per-page slot index).
 *
 * @param itemId   the item asset ID to display (e.g. "hytale:oak_log")
 * @param recipeId the recipe ID for creating the stencil
 * @param label    the display name shown on hover
 * @param index    the global index in the full item list (used in event payloads)
 */
public record RadialSegmentItem(String itemId, String recipeId, String label, int index) {
}
