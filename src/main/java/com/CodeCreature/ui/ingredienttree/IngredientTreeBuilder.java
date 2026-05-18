package com.CodeCreature.ui.ingredienttree;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.protocol.ItemResourceType;

import java.util.*;
import java.util.logging.Logger;

public final class IngredientTreeBuilder {

    private static final Logger LOG = Logger.getLogger(IngredientTreeBuilder.class.getName());

    private IngredientTreeBuilder() {}

    public static IngredientTree build(List<CraftingRecipe> allRecipes) {
        if (allRecipes == null || allRecipes.isEmpty()) {
            return new IngredientTree(List.of(), Map.of());
        }

        // ── Step 1: Collect resource type IDs from recipe inputs ──
        Set<String> resourceTypeIds = new LinkedHashSet<>();

        for (CraftingRecipe recipe : allRecipes) {
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null) continue;

            for (MaterialQuantity input : inputs) {
                if (input.getResourceTypeId() != null) {
                    resourceTypeIds.add(input.getResourceTypeId());
                }
                String itemId = input.getItemId();
                if (itemId != null && !"Empty".equals(itemId)) {
                    Item item = Item.getAssetMap().getAsset(itemId);
                    if (item != null && item.getResourceTypes() != null) {
                        for (ItemResourceType rt : item.getResourceTypes()) {
                            if (rt.id != null) {
                                resourceTypeIds.add(rt.id);
                            }
                        }
                    }
                }
            }
        }

        LOG.fine(() -> "Collected " + resourceTypeIds.size() + " resource type IDs");

        // ── Step 1b: Collect direct item IDs (items not covered by any collected resource type) ──
        Set<String> directItemIds = new LinkedHashSet<>();

        for (CraftingRecipe recipe : allRecipes) {
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null) continue;

            for (MaterialQuantity input : inputs) {
                String itemId = input.getItemId();
                if (itemId == null || "Empty".equals(itemId)) continue;

                Item item = Item.getAssetMap().getAsset(itemId);
                if (item == null) continue;

                boolean coveredByResourceType = false;
                if (item.getResourceTypes() != null) {
                    for (ItemResourceType rt : item.getResourceTypes()) {
                        if (rt.id != null && resourceTypeIds.contains(rt.id)) {
                            coveredByResourceType = true;
                            break;
                        }
                    }
                }

                if (!coveredByResourceType) {
                    directItemIds.add(itemId);
                }
            }
        }

        LOG.fine(() -> "Collected " + directItemIds.size() + " direct item IDs");

        // ── Step 2: Resolve icons for each resource type ID ──
        Map<String, String> iconMap = new HashMap<>();
        for (String resId : resourceTypeIds) {
            ResourceType rtAsset = ResourceType.getAssetMap().getAsset(resId);
            if (rtAsset != null && rtAsset.getIcon() != null) {
                iconMap.put(resId, rtAsset.getIcon());
            } else {
                iconMap.put(resId, "Icons/ResourceTypes/" + resId + ".png");
            }
        }

        // ── Step 3: Group by ID prefix (first segment before '_') ──
        Map<String, List<String>> prefixGroups = new LinkedHashMap<>();
        for (String resId : resourceTypeIds) {
            String prefix = extractPrefix(resId);
            prefixGroups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(resId);
        }
        for (String itemId : directItemIds) {
            String prefix = extractPrefix(itemId);
            prefixGroups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(itemId);
        }

        // ── Step 4: Merge single-member groups into a shared "Other" group ──
        List<String> otherResIds = new ArrayList<>();
        Iterator<Map.Entry<String, List<String>>> it = prefixGroups.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<String>> entry = it.next();
            if (entry.getValue().size() == 1) {
                otherResIds.addAll(entry.getValue());
                it.remove();
            }
        }
        if (!otherResIds.isEmpty()) {
            prefixGroups.put("Other", otherResIds);
        }

        // ── Step 5: Build immutable tree objects ──
        List<IngredientGroup> groups = new ArrayList<>();
        Map<String, IngredientTreeNode> nodeIndex = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : prefixGroups.entrySet()) {
            String prefix = entry.getKey();
            List<String> resIds = entry.getValue();

            String groupId = "grp:" + prefix;
            String displayName = prefix.replace('_', ' ');

            // Group icon: try Any_{Prefix}.png first, then use first child's icon
            String groupIconPath = resolveGroupIcon(prefix, resIds, iconMap, directItemIds);

            // Placeholder group for parent back-references in RT nodes
            IngredientGroup placeholderGroup = new IngredientGroup(
                    groupId, displayName, groupIconPath, List.of());

            // Build resource type children sorted alphabetically
            List<String> sortedResIds = new ArrayList<>(resIds);
            sortedResIds.sort(Comparator.naturalOrder());

            List<IngredientResourceType> rtNodes = new ArrayList<>();
            for (String resId : sortedResIds) {
                String rtIconPath;
                if (directItemIds.contains(resId)) {
                    // Direct item — no icon path (GridController uses ItemIcon)
                    rtIconPath = null;
                } else {
                    rtIconPath = iconMap.getOrDefault(resId, "Common/Icons/ResourceTypes/" + resId + ".png");
                    if (!rtIconPath.startsWith("Common/")) {
                        rtIconPath = "Common/" + rtIconPath;
                    }
                }
                String rtDisplayName = resId.replace('_', ' ');

                IngredientResourceType rt = new IngredientResourceType(
                        resId, rtDisplayName, rtIconPath, placeholderGroup, List.of());
                rtNodes.add(rt);
            }

            IngredientGroup realGroup = new IngredientGroup(
                    groupId, displayName, groupIconPath, rtNodes);
            groups.add(realGroup);

            // Index nodes
            nodeIndex.put(realGroup.getId(), realGroup);
            for (IngredientResourceType rt : realGroup.getChildren()) {
                nodeIndex.put(rt.getId(), rt);
            }
        }

        groups.sort(Comparator.comparing(IngredientGroup::getDisplayName));

        LOG.info("Built ingredient tree: " + groups.size() + " groups, "
                + nodeIndex.size() + " total nodes");

        return new IngredientTree(groups, nodeIndex);
    }

    private static String extractPrefix(String resId) {
        int underscore = resId.indexOf('_');
        return underscore > 0 ? resId.substring(0, underscore) : resId;
    }

    private static String resolveGroupIcon(String prefix, List<String> resIds, Map<String, String> iconMap, Set<String> directItemIds) {
        // Check if any child has an "Any_{prefix}" style icon
        for (String resId : resIds) {
            String icon = iconMap.get(resId);
            if (icon != null) {
                String filename = extractFilename(icon);
                if (filename.startsWith("Any_")) {
                    return "Common/Icons/ResourceTypes/" + filename;
                }
            }
        }
        // Fall back to first child that has an icon in the resource type map
        for (String resId : resIds) {
            String icon = iconMap.get(resId);
            if (icon != null) {
                if (!icon.startsWith("Common/")) {
                    icon = "Common/" + icon;
                }
                return icon;
            }
        }
        // No resource type icons — try looking up the first direct item's resource type icon
        for (String resId : resIds) {
            if (!directItemIds.contains(resId)) continue;
            Item item = Item.getAssetMap().getAsset(resId);
            if (item == null || item.getResourceTypes() == null) continue;
            for (ItemResourceType irt : item.getResourceTypes()) {
                if (irt.id == null) continue;
                ResourceType rtAsset = ResourceType.getAssetMap().getAsset(irt.id);
                if (rtAsset != null && rtAsset.getIcon() != null) {
                    String icon = rtAsset.getIcon();
                    if (!icon.startsWith("Common/")) {
                        icon = "Common/" + icon;
                    }
                    return icon;
                }
            }
        }
        return null;
    }

    private static String extractFilename(String path) {
        if (path == null || path.isEmpty()) return "";
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
