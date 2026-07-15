package com.CodeCreature.ui.bench;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static registry of all known resource types available in the Stencil Crafting
 * resource type filter grid.
 *
 * <p>Each entry maps a {@code resourceTypeId} to an icon filename located at
 * {@code Common/UI/Custom/Common/Icons/ResourceTypes/}. Entries are either:
 * <ul>
 *   <li><strong>Exact-match entries</strong> — {@code resourceTypeId} matches
 *       a single engine {@code ResourceTypeId} (e.g., {@code "Hardwood"})</li>
 *   <li><strong>Meta-filter entries</strong> — {@code resourceTypeId} ends with
 *       {@code "_Group"} and maps to a SET of engine {@code ResourceTypeId}
 *       values (e.g., {@code "Rock_Group"} → {@code {"Rock", "Clays", "Sands", ...}})</li>
 * </ul>
 *
 * <p>Use {@link #resolveFilterIds(String)} to expand any registry entry to
 * the actual set of {@code ResourceTypeId} values it matches.
 *
 * <p>This class is <strong>stateless and thread-safe</strong> — all data is
 * initialized at class load time and never mutated.
 *
 * <h3>Usage:</h3>
 * <pre>
 *   List&lt;ResourceTypeEntry&gt; all = ResourceTypeRegistry.getAll();
 *   Set&lt;String&gt; ids = ResourceTypeRegistry.resolveFilterIds("Rock_Group");
 *   boolean isMeta = ResourceTypeRegistry.isMetaFilter("Rock_Group"); // true
 * </pre>
 */
public final class ResourceTypeRegistry {

    /** Icon base path relative to the UI asset root. */
    static final String ICON_BASE_PATH = "Common/Icons/ResourceTypes/";

    /**
     * A single resource type entry in the registry.
     *
     * @param resourceTypeId the resource type ID matching {@code MaterialQuantity.ResourceTypeId}
     *                       values in recipe inputs (e.g., "Hardwood", "Rock_Basalt_Brick"),
     *                       OR a meta-filter group ID ending in "_Group" (e.g., "Bone_Group")
     * @param iconFilename   the icon filename within the ResourceTypes directory
     *                       (e.g., "Hardwood.png", "Any_Bone.png")
     * @param sortOrder      the display sort order (0-based, alphabetical by default)
     * @param metaFilter     {@code true} if this entry is a group meta-filter that maps
     *                       to multiple actual ResourceTypeIds via {@link #resolveFilterIds}
     */
    public record ResourceTypeEntry(
            String resourceTypeId,
            String iconFilename,
            int sortOrder,
            boolean metaFilter
    ) {}

    /** Immutable ordered list of all resource type entries. */
    private static final List<ResourceTypeEntry> ENTRIES;

    /**
     * Maps meta-filter group IDs to the set of actual engine ResourceTypeIds
     * they cover. Only populated for entries where {@code metaFilter == true}.
     */
    private static final Map<String, Set<String>> META_FILTER_MAP;

    static {
        ENTRIES = List.of(
                new ResourceTypeEntry("Blackwood",                    "Blackwood.png",                    0,  false),
                new ResourceTypeEntry("Bone_Group",                   "Any_Bone.png",                     1,  true),
                new ResourceTypeEntry("Books_Group",                  "Any_Book.png",                     2,  true),
                new ResourceTypeEntry("Crystal_Shards",               "Crystal_Shards.png",               3,  false),
                new ResourceTypeEntry("Darkwood",                     "Darkwood.png",                     4,  false),
                new ResourceTypeEntry("Deadwood",                     "Deadwood.png",                     5,  false),
                new ResourceTypeEntry("Drywood",                      "Drywood.png",                      6,  false),
                new ResourceTypeEntry("Fish",                         "Fish.png",                         7,  false),
                new ResourceTypeEntry("Fish_Epic",                    "Fish_Epic.png",                    8,  false),
                new ResourceTypeEntry("Fish_Legendary",               "Fish_Legendary.png",               9,  false),
                new ResourceTypeEntry("Fish_Rare",                    "Fish_Rare.png",                    10, false),
                new ResourceTypeEntry("Fish_Uncommon",                "Fish_Uncommon.png",                11, false),
                new ResourceTypeEntry("Flowers",                      "Flowers.png",                      12, false),
                new ResourceTypeEntry("Fuel",                         "Fuel.png",                         13, false),
                new ResourceTypeEntry("Goldenwood",                   "Goldenwood.png",                   14, false),
                new ResourceTypeEntry("Greenwood",                    "Greenwood.png",                    15, false),
                new ResourceTypeEntry("Hardwood",                     "Hardwood.png",                     16, false),
                new ResourceTypeEntry("Lightwood",                    "Lightwood.png",                    17, false),
                new ResourceTypeEntry("Meats_Group",                  "Any_Meat.png",                     18, true),
                new ResourceTypeEntry("Milk_Bucket",                  "Milk_Bucket.png",                  19, false),
                new ResourceTypeEntry("Milk_Mosshorn_Bucket",         "Milk_Mosshorn_Bucket.png",         20, false),
                new ResourceTypeEntry("Moss",                         "Moss.png",                         21, false),
                new ResourceTypeEntry("Mushrooms_Group",              "Any_Mushroom.png",                 22, true),
                new ResourceTypeEntry("Prototype_Rock_Concrete_Brick","Prototype_Rock_Concrete_Brick.png",23, false),
                new ResourceTypeEntry("Redwood",                      "Redwood.png",                      24, false),
                new ResourceTypeEntry("Rock_Aqua_Brick",              "Rock_Aqua_Brick.png",              25, false),
                new ResourceTypeEntry("Rock_Aqua_Cobble",             "Rock_Aqua_Cobble.png",             26, false),
                new ResourceTypeEntry("Rock_Basalt_Brick",            "Rock_Basalt_Brick.png",            27, false),
                new ResourceTypeEntry("Rock_Basalt_Cobble",           "Rock_Basalt_Cobble.png",           28, false),
                new ResourceTypeEntry("Rock_Calcite_Brick",           "Rock_Calcite_Brick.png",           29, false),
                new ResourceTypeEntry("Rock_Calcite_Cobble",          "Rock_Calcite_Cobble.png",          30, false),
                new ResourceTypeEntry("Rock_Chalk_Brick",             "Rock_Chalk_Brick.png",             31, false),
                new ResourceTypeEntry("Rock_Gold_Brick",              "Rock_Gold_Brick.png",              32, false),
                new ResourceTypeEntry("Rock_Group",                   "Any_Rock.png",                     33, true),
                new ResourceTypeEntry("Rock_Ledge_Brick",             "Rock_Ledge_Brick.png",             34, false),
                new ResourceTypeEntry("Rock_Ledge_Cobble",            "Rock_Ledge_Cobble.png",            35, false),
                new ResourceTypeEntry("Rock_Lime_Brick",              "Rock_Lime_Brick.png",              36, false),
                new ResourceTypeEntry("Rock_Lime_Cobble",             "Rock_Lime_Cobble.png",             37, false),
                new ResourceTypeEntry("Rock_Marble_Brick",            "Rock_Marble_Brick.png",            38, false),
                new ResourceTypeEntry("Rock_Marble_Cobble",           "Rock_Marble_Cobble.png",           39, false),
                new ResourceTypeEntry("Rock_Peach_Brick",             "Rock_Peach_Brick.png",             40, false),
                new ResourceTypeEntry("Rock_Peach_Cobble",            "Rock_Peach_Cobble.png",            41, false),
                new ResourceTypeEntry("Rock_Quartzite_Brick",         "Rock_Quartzite_Brick.png",         42, false),
                new ResourceTypeEntry("Rock_Quartzite_Cobble",        "Rock_Quartzite_Cobble.png",        43, false),
                new ResourceTypeEntry("Rock_Runic_Blue_Brick",        "Rock_Runic_Blue_Brick.png",        44, false),
                new ResourceTypeEntry("Rock_Runic_Brick",             "Rock_Runic_Brick.png",             45, false),
                new ResourceTypeEntry("Rock_Sandstone_Brick",         "Rock_Sandstone_Brick.png",         46, false),
                new ResourceTypeEntry("Rock_Sandstone_Cobble",        "Rock_Sandstone_Cobble.png",        47, false),
                new ResourceTypeEntry("Rock_Sandstone_Red_Brick",     "Rock_Sandstone_Red_Brick.png",     48, false),
                new ResourceTypeEntry("Rock_Sandstone_Red_Cobble",    "Rock_Sandstone_Red_Cobble.png",    49, false),
                new ResourceTypeEntry("Rock_Sandstone_White_Brick",   "Rock_Sandstone_White_Brick.png",   50, false),
                new ResourceTypeEntry("Rock_Sandstone_White_Cobble",  "Rock_Sandstone_White_Cobble.png",  51, false),
                new ResourceTypeEntry("Rock_Shale_Brick",             "Rock_Shale_Brick.png",             52, false),
                new ResourceTypeEntry("Rock_Shale_Cobble",            "Rock_Shale_Cobble.png",            53, false),
                new ResourceTypeEntry("Rock_Slate_Cobble",            "Rock_Slate_Cobble.png",            54, false),
                new ResourceTypeEntry("Rock_Stone",                   "Rock_Stone.png",                   55, false),
                new ResourceTypeEntry("Rock_Stone_Brick",             "Rock_Stone_Brick.png",             56, false),
                new ResourceTypeEntry("Rock_Stone_Cobble",            "Rock_Stone_Cobble.png",            57, false),
                new ResourceTypeEntry("Rock_Temp",                    "Rock_Temp.png",                    58, false),
                new ResourceTypeEntry("Rock_Volcanic_Brick",          "Rock_Volcanic_Brick.png",          59, false),
                new ResourceTypeEntry("Rock_Volcanic_Cobble",         "Rock_Volcanic_Cobble.png",         60, false),
                new ResourceTypeEntry("Rubble_Group",                 "Any_Rubble.png",                   61, true),
                new ResourceTypeEntry("Softwood",                     "Softwood.png",                     62, false),
                new ResourceTypeEntry("Soil_Clay_Brick",              "Soil_Clay_Brick.png",              63, false),
                new ResourceTypeEntry("Soil_Clay_Ocean_Brick",        "Soil_Clay_Ocean_Brick.png",        64, false),
                new ResourceTypeEntry("Soil_Hive_Brick",              "Soil_Hive_Brick.png",              65, false),
                new ResourceTypeEntry("Soil_Hive_Corrupted_Brick",    "Soil_Hive_Corrupted_Brick.png",    66, false),
                new ResourceTypeEntry("Soil_Snow_Brick",              "Soil_Snow_Brick.png",              67, false),
                new ResourceTypeEntry("Tropicalwood",                 "Tropicalwood.png",                 68, false),
                new ResourceTypeEntry("Trunk_Group",                  "Any_Trunk.png",                    69, true),
                new ResourceTypeEntry("Wood",                         "Wood.png",                         70, false),
                new ResourceTypeEntry("Wood_Planks",                  "Wood_Planks.png",                  71, false),
                new ResourceTypeEntry("Wood_Trunk_Temp",              "Wood_Trunk_Temp.png",              72, false)
        );

        Map<String, Set<String>> map = new HashMap<>();
        map.put("Bone_Group", Set.of("Bone"));
        map.put("Books_Group", Set.of("Books"));
        map.put("Meats_Group", Set.of("Meats"));
        map.put("Mushrooms_Group", Set.of("Mushrooms"));
        map.put("Rock_Group", Set.of("Rock", "Clays", "Sands", "Soils",
                "Rock_Slate_Brick", "Rock_Runic_Teal_Brick", "Rock_Runic_Dark_Brick"));
        map.put("Rubble_Group", Set.of("Rubble"));
        map.put("Trunk_Group", Set.of(
                "Wood_Trunk", "Wood_All_Trunk", "Wood_Hardwood_Trunk",
                "Wood_Amber_Trunk", "Wood_Apple_Trunk", "Wood_Ash_Trunk",
                "Wood_Aspen_Trunk", "Wood_Azure_Trunk", "Wood_Bamboo_Trunk",
                "Wood_Banyan_Trunk", "Wood_Beech_Trunk", "Wood_Birch_Trunk",
                "Wood_Blackwood_Trunk", "Wood_Bottletree_Trunk", "Wood_Camphor_Trunk",
                "Wood_Cedar_Trunk", "Wood_Crystal_Trunk", "Wood_Darkwood_Trunk",
                "Wood_Deadwood_Trunk", "Wood_Dry_Trunk", "Wood_Drywood_Trunk",
                "Wood_Fig_Blue_Trunk", "Wood_Fir_Trunk", "Wood_Goldenwood_Trunk",
                "Wood_Greenwood_Trunk", "Wood_Gumboab_Trunk", "Wood_Jungle_Trunk",
                "Wood_Lightwood_Trunk", "Wood_Maple_Trunk", "Wood_Oak_Trunk",
                "Wood_Palm_Trunk", "Wood_Palo_Trunk", "Wood_Petrified_Trunk",
                "Wood_Redwood_Trunk", "Wood_Sallow_Trunk", "Wood_Softwood_Trunk",
                "Wood_Spiral_Trunk", "Wood_Stormbark_Trunk", "Wood_Tropicalwood_Trunk",
                "Wood_Windwillow_Trunk", "Wood_Wisteria_Wild_Trunk"
        ));
        META_FILTER_MAP = Collections.unmodifiableMap(map);
    }

    private ResourceTypeRegistry() {}

    /**
     * Returns the immutable, ordered list of all resource type entries.
     *
     * <p>The list is sorted alphabetically by {@code resourceTypeId} and
     * never changes after class initialization.
     *
     * @return unmodifiable list of all resource type entries
     */
    public static List<ResourceTypeEntry> getAll() {
        return ENTRIES;
    }

    /**
     * Returns the icon filename for the given resource type ID.
     *
     * <p>Performs a linear scan of the registry. Returns null if the
     * resource type ID is not found.
     *
     * @param resourceTypeId the resource type ID to look up
     * @return the icon filename (e.g., "Hardwood.png"), or null if not found
     */
    /**
     * Returns the icon <b>filename token</b> for the given resource type ID — e.g.
     * {@code "Hardwood.png"} or {@code "Any_Bone.png"}.
     *
     * <p><b>This is a bare filename, not a UI-ready path.</b> Callers must normalize it
     * via {@link com.CodeCreature.ui.common.IconPathResolver#normalizeResourceTypeIcon(String)}
     * (or the higher-level
     * {@link com.CodeCreature.ui.common.IconPathResolver#resolveResourceTypeIcon(String)})
     * before passing it to a UI command builder.
     *
     * <p>Performs a linear scan of the registry. Returns {@code null} if the
     * resource type ID is not found.
     *
     * @param resourceTypeId the resource type ID to look up (e.g. {@code "Hardwood"})
     * @return the icon filename token, or {@code null} if not found
     */
    public static String getIconPath(String resourceTypeId) {
        for (ResourceTypeEntry entry : ENTRIES) {
            if (entry.resourceTypeId().equals(resourceTypeId)) {
                return entry.iconFilename();
            }
        }
        return null;
    }

    /**
     * Returns the total number of registered resource types.
     *
     * @return the count of resource type entries
     */
    public static int getCount() {
        return ENTRIES.size();
    }

    /**
     * Resolves a registry entry's {@code resourceTypeId} to the set of actual
     * engine {@code ResourceTypeId} values it covers.
     *
     * <p>For meta-filter entries (e.g., {@code "Rock_Group"}), returns the
     * mapped set of all ResourceTypeIds that share the group's icon in the
     * engine's ResourceType definitions. For exact-match entries (e.g.,
     * {@code "Hardwood"}), returns a singleton set containing the ID itself.
     *
     * <p>If the given ID is not found in the meta-filter map, this method
     * assumes it is an exact-match ID and returns {@code Set.of(resourceTypeId)}.
     *
     * @param resourceTypeId the registry entry's resourceTypeId
     * @return unmodifiable set of actual engine ResourceTypeId values; never empty
     */
    public static Set<String> resolveFilterIds(String resourceTypeId) {
        Set<String> metaSet = META_FILTER_MAP.get(resourceTypeId);
        if (metaSet != null) return metaSet;
        return Set.of(resourceTypeId);
    }

    /**
     * Returns {@code true} if the given {@code resourceTypeId} is a group
     * meta-filter entry that maps to multiple actual ResourceTypeIds.
     *
     * @param resourceTypeId the registry entry's resourceTypeId
     * @return true if this ID is a meta-filter group
     */
    public static boolean isMetaFilter(String resourceTypeId) {
        return META_FILTER_MAP.containsKey(resourceTypeId);
    }

    /**
     * One-time preference migration map from old {@code "Any_*"} registry IDs
     * to the new {@code "*_Group"} meta-filter IDs. Used by
     * {@code StencilSelectionPage.loadPrefs()} to migrate stale saved values.
     *
     * @return unmodifiable map of old ID → new ID
     */
    public static Map<String, String> getLegacyIdMigrationMap() {
        return Map.ofEntries(
                Map.entry("Any_Bone", "Bone_Group"),
                Map.entry("Any_Book", "Books_Group"),
                Map.entry("Any_Meat", "Meats_Group"),
                Map.entry("Any_Mushroom", "Mushrooms_Group"),
                Map.entry("Any_Recipe", ""),
                Map.entry("Any_Rock", "Rock_Group"),
                Map.entry("Any_Rubble", "Rubble_Group"),
                Map.entry("Any_Trunk", "Trunk_Group"),
                Map.entry("Bone", "Bone_Group"),
                Map.entry("Books", "Books_Group"),
                Map.entry("Rock", "Rock_Group"),
                Map.entry("Rubble", "Rubble_Group"),
                Map.entry("Wood_Trunk", "Trunk_Group")
        );
    }
}
