package com.UnobstructedThirdPerson.placeblock.ui;

/**
 * Three-state affordability mode for the Blueprint Bench filter system.
 *
 * <p>Controls how recipes are filtered and dimmed in the recipe grid:
 * <ul>
 *   <li>{@link #ALL} — no filtering, no dimming; complete catalog view</li>
 *   <li>{@link #INVENTORY_DRIVEN} — filters/dims by player inventory contents</li>
 *   <li>{@link #RESOURCE_DRIVEN} — filters/dims by player-selected resource types</li>
 * </ul>
 *
 * <p>The toggle cycles: INVENTORY_DRIVEN → RESOURCE_DRIVEN → ALL → INVENTORY_DRIVEN.
 */
public enum AffordabilityMode {

    /** No filtering, no dimming. All sets visible, all recipes bright. */
    ALL,

    /** Filter by player inventory. Sets hidden if zero affordable recipes. Items dimmed if unaffordable. */
    INVENTORY_DRIVEN,

    /** Filter by selected resource types. Sets hidden if zero matching recipes. Items dimmed if no input matches. */
    RESOURCE_DRIVEN;

    /**
     * Returns the next mode in the cycling order.
     *
     * <p>Cycle: ALL → INVENTORY_DRIVEN → RESOURCE_DRIVEN → ALL.
     *
     * @return the next {@link AffordabilityMode} in the cycle
     */
    public AffordabilityMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * Returns the human-readable label for display on the toggle button.
     *
     * <ul>
     *   <li>ALL → "All"</li>
     *   <li>INVENTORY_DRIVEN → "Inventory Driven"</li>
     *   <li>RESOURCE_DRIVEN → "Resource Driven"</li>
     * </ul>
     *
     * @return display label string
     */
    public String label() {
        return switch (this) {
            case ALL -> "All";
            case INVENTORY_DRIVEN -> "Inventory Driven";
            case RESOURCE_DRIVEN -> "Resource Driven";
        };
    }

    /**
     * Parses a mode from its persisted string representation.
     *
     * <p>Handles migration from the legacy boolean {@code affordabilityEnabled} field:
     * <ul>
     *   <li>{@code "true"} → {@link #INVENTORY_DRIVEN}</li>
     *   <li>{@code "false"} → {@link #ALL}</li>
     *   <li>Enum name string → corresponding enum value</li>
     *   <li>Unrecognized → {@link #INVENTORY_DRIVEN} (safe default)</li>
     * </ul>
     *
     * @param value the persisted string value (may be a boolean string or enum name)
     * @return the corresponding {@link AffordabilityMode}, never null
     */
    public static AffordabilityMode fromString(String value) {
        if (value == null) return INVENTORY_DRIVEN;
        return switch (value) {
            case "ALL" -> ALL;
            case "INVENTORY_DRIVEN" -> INVENTORY_DRIVEN;
            case "RESOURCE_DRIVEN" -> RESOURCE_DRIVEN;
            case "true" -> INVENTORY_DRIVEN;
            case "false" -> ALL;
            default -> INVENTORY_DRIVEN;
        };
    }
}
