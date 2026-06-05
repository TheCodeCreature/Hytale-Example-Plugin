package com.CodeCreature.ui.bench;

import com.hypixel.hytale.server.core.Message;

/**
 * Three-state affordability mode for the Blueprint Bench filter system.
 *
 * <p>Controls how recipes are filtered and dimmed in the recipe grid:
 * <ul>
 *   <li>{@link #INVENTORY_DRIVEN} — filters/dims by player inventory contents</li>
 *   <li>{@link #RESOURCE_PLANNING} — filters/dims by player-selected resource types</li>
 * </ul>
 *
 * <p>The toggle cycles: INVENTORY_DRIVEN → RESOURCE_PLANNING → ALL → INVENTORY_DRIVEN.
 */
public enum AffordabilityMode {

    /** Filter by player inventory. Sets hidden if zero affordable recipes. Items dimmed if unaffordable. */
    INVENTORY_DRIVEN,

    /** Filter by selected resource types. Sets hidden if zero matching recipes. Items dimmed if no input matches. */
    RESOURCE_PLANNING; 

    /**
     * Returns the next mode in the cycling order.
     *
     * <p>Cycle: INVENTORY_DRIVEN → RESOURCE_PLANNING → INVENTORY_DRIVEN.
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
     *   <li>RESOURCE_PLANNING → "Resource Planning"</li>
     * </ul>
     *
     * @return display label string
     */
    public Message label() {
        return switch (this) {
            case INVENTORY_DRIVEN -> Message.translation("server.ui.blueprint.filter.mode.inventoryDriven");
            case RESOURCE_PLANNING -> Message.translation("server.ui.blueprint.filter.mode.resourcePlanning");
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
            case "INVENTORY_DRIVEN" -> INVENTORY_DRIVEN;
            case "RESOURCE_PLANNING" -> RESOURCE_PLANNING;
            case "true" -> INVENTORY_DRIVEN;
            case "false" -> RESOURCE_PLANNING;
            default -> INVENTORY_DRIVEN;
        };
    }
}
