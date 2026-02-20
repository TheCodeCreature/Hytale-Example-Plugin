package com.UnobstructedThirdPerson.Commands.debug;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages mapping from hitbox types to placeholder block IDs.
 * Uses pattern matching to intelligently categorize 234+ hitbox types into ~11 placeholder categories.
 */
public class PlaceholderBlockManager {
    
    private static final Map<String, String> EXACT_MAPPINGS = new HashMap<>();
    private static final Map<String, String> PATTERN_MAPPINGS = new HashMap<>();
    
    static {
        // Initialize pattern mappings (checked if hitboxType contains the key)
        // Order matters - more specific patterns first
        PATTERN_MAPPINGS.put("Block_Half", "Placeholder_Half");
        PATTERN_MAPPINGS.put("_Half", "Placeholder_Half");
        PATTERN_MAPPINGS.put("Block_Quarter", "Placeholder_Quarter");
        PATTERN_MAPPINGS.put("_Quarter", "Placeholder_Quarter");
        PATTERN_MAPPINGS.put("Block_One_Eighth", "Placeholder_Quarter");
        PATTERN_MAPPINGS.put("Block_Three_Eighth", "Placeholder_Quarter");
        PATTERN_MAPPINGS.put("Block_Five_Eighth", "Placeholder_Quarter");
        PATTERN_MAPPINGS.put("Block_Seven_Eighth", "Placeholder_Quarter");
        PATTERN_MAPPINGS.put("Block_Vertical", "Placeholder_Half");
        PATTERN_MAPPINGS.put("Stairs", "Placeholder_Stairs");
        PATTERN_MAPPINGS.put("Roof", "Placeholder_Stairs");
        PATTERN_MAPPINGS.put("Fence", "Placeholder_Fence");
        PATTERN_MAPPINGS.put("Door", "Placeholder_Door");
        PATTERN_MAPPINGS.put("Trapdoor", "Placeholder_Door");
        PATTERN_MAPPINGS.put("Shutter", "Placeholder_Door");
        PATTERN_MAPPINGS.put("Platform", "Placeholder_Platform");
        PATTERN_MAPPINGS.put("Block_Flat", "Placeholder_Platform");
        PATTERN_MAPPINGS.put("Ladder", "Placeholder_Ladder");
        PATTERN_MAPPINGS.put("Chain", "Placeholder_Ladder");
        PATTERN_MAPPINGS.put("Rope", "Placeholder_Ladder");
        PATTERN_MAPPINGS.put("Plant", "Placeholder_Plant");
        PATTERN_MAPPINGS.put("Mushroom", "Placeholder_Plant");
        PATTERN_MAPPINGS.put("Pumpkin", "Placeholder_Plant");
        PATTERN_MAPPINGS.put("Water_Lily", "Placeholder_Plant");
        PATTERN_MAPPINGS.put("Bed", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Chair", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Bench", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Table", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Desk", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Wardrobe", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Chest", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Coffin", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Coop", "Placeholder_Furniture");
        PATTERN_MAPPINGS.put("Torch", "Placeholder_Torch");
        PATTERN_MAPPINGS.put("Lantern", "Placeholder_Torch");
        PATTERN_MAPPINGS.put("Brazier", "Placeholder_Torch");
        PATTERN_MAPPINGS.put("Candle", "Placeholder_Torch");
        PATTERN_MAPPINGS.put("Lamp", "Placeholder_Torch");
        PATTERN_MAPPINGS.put("Sign", "Placeholder_Ladder");
        PATTERN_MAPPINGS.put("Painting", "Placeholder_Ladder");
        PATTERN_MAPPINGS.put("Banner", "Placeholder_Ladder");
        PATTERN_MAPPINGS.put("Food", "Placeholder_Torch");
        PATTERN_MAPPINGS.put("Beam", "Placeholder_Fence");
        PATTERN_MAPPINGS.put("Branch", "Placeholder_Fence");
    }
    
    /**
     * Get the appropriate placeholder block ID for a given hitbox type.
     * Uses pattern matching to categorize hitbox types intelligently.
     * 
     * @param hitboxType The hitbox type string (e.g., "Block_Half", "Stairs_Corner_Left")
     * @return The placeholder block ID to use
     */
    @Nonnull
    public static String getPlaceholderForHitbox(@Nonnull String hitboxType) {
        // Check exact mappings first (single map lookup)
        String exactMatch = EXACT_MAPPINGS.get(hitboxType);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Check pattern mappings - find first pattern that matches
        for (Map.Entry<String, String> entry : PATTERN_MAPPINGS.entrySet()) {
            if (hitboxType.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Default fallback to Full cube
        return "Placeholder_Full";
    }
    
    /**
     * Check if a placeholder block ID exists in the asset system.
     * 
     * @param placeholderId The placeholder block ID
     * @return true if the placeholder exists
     */
    public static boolean placeholderExists(@Nonnull String placeholderId) {
        return com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
            .getAssetMap()
            .getAsset(placeholderId) != null;
    }
}
