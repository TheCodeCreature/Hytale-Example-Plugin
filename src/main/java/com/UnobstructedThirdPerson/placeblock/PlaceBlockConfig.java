package com.UnobstructedThirdPerson.placeblock;

import java.util.Objects;

/**
 * Immutable configuration for the PlaceBlock building tool system.
 * Loaded from a JSON resource file via {@link PlaceBlockConfigLoader}.
 *
 * <p>Defines the chest scanning radius used by {@link ResourceScanner}
 * when checking resource availability both during recipe filtering
 * (at the bench) and placement (in the world).
 *
 * @param chestHorizontalRadius horizontal radius (in blocks) to scan for chests
 *                              around the player's position
 * @param chestVerticalRadius   vertical radius (in blocks) to scan for chests
 *                              around the player's position
 * @param placeholderItemId     the asset ID of the Block_Placeholder item
 *                              (e.g. {@code "hytale:Block_Placeholder"})
 */
public record PlaceBlockConfig(
        int chestHorizontalRadius,
        int chestVerticalRadius,
        String placeholderItemId
) {
    public PlaceBlockConfig {
        if (chestHorizontalRadius < 0) {
            throw new IllegalArgumentException("chestHorizontalRadius must be >= 0");
        }
        if (chestVerticalRadius < 0) {
            throw new IllegalArgumentException("chestVerticalRadius must be >= 0");
        }
        Objects.requireNonNull(placeholderItemId, "placeholderItemId must not be null");
    }
}
