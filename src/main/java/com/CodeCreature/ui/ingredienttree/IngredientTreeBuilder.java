package com.CodeCreature.ui.ingredienttree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import com.CodeCreature.crafting.GenericIngredientResolution;
import com.CodeCreature.crafting.IngredientPresentation;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.INGREDIENT_TREE;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

public final class IngredientTreeBuilder {

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

        DebugLogger.log(INGREDIENT_TREE, Level.FINE, () -> "Collected " + resourceTypeIds.size() + " resource type IDs");

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

        DebugLogger.log(INGREDIENT_TREE, Level.FINE, () -> "Collected " + directItemIds.size() + " direct item IDs");

        // ── Step 2: Resolve representative item IDs for each resource type ID ──
        Map<String, IngredientPresentation> representativePresentationMap = new HashMap<>();
        for (String resId : resourceTypeIds) {
            IngredientPresentation representativePresentation = resolveRepresentativePresentationForResourceType(resId);
            if (representativePresentation != null) {
                representativePresentationMap.put(resId, representativePresentation);
            }
            // Absent key = unresolvable; UI falls back to blank icon instead of broken texture path.
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

            // Group header icon uses ItemIcon with a representative item id.
            String groupDisplayItemId = resolveGroupDisplayItemId(resIds, representativePresentationMap, directItemIds);

            // Placeholder group for parent back-references in RT nodes
            IngredientGroup placeholderGroup = new IngredientGroup(
                    groupId, displayName, null, groupDisplayItemId, List.of());

            // Build resource type children sorted alphabetically
            List<String> sortedResIds = new ArrayList<>(resIds);
            sortedResIds.sort(Comparator.naturalOrder());

            List<IngredientResourceType> rtNodes = new ArrayList<>();
            for (String resId : sortedResIds) {
                String rtDisplayItemId;
                boolean isDirectItem;
                if (directItemIds.contains(resId)) {
                    // Direct item — render directly via its own ItemId.
                    rtDisplayItemId = resId;
                    isDirectItem = true;
                } else {
                    IngredientPresentation presentation = representativePresentationMap.get(resId);
                    rtDisplayItemId = presentation != null ? presentation.iconItemId() : null;
                    isDirectItem = false;
                }
                String rtDisplayName = resId.replace('_', ' ');

                IngredientResourceType rt = new IngredientResourceType(
                        resId, rtDisplayName, null, rtDisplayItemId, isDirectItem, placeholderGroup, List.of());
                rtNodes.add(rt);
            }

            IngredientGroup realGroup = new IngredientGroup(
                    groupId, displayName, null, groupDisplayItemId, rtNodes);
            groups.add(realGroup);

            // Index nodes
            nodeIndex.put(realGroup.getId(), realGroup);
            for (IngredientResourceType rt : realGroup.getChildren()) {
                nodeIndex.put(rt.getId(), rt);
            }
        }

        groups.sort(Comparator.comparing(IngredientGroup::getDisplayName));

        DebugLogger.log(INGREDIENT_TREE, Level.INFO, "Built ingredient tree: " + groups.size() + " groups, "
                + nodeIndex.size() + " total nodes");

        return new IngredientTree(groups, nodeIndex);
    }

    private static String extractPrefix(String resId) {
        int underscore = resId.indexOf('_');
        return underscore > 0 ? resId.substring(0, underscore) : resId;
    }

    @Nullable
    private static String resolveGroupDisplayItemId(List<String> resIds,
                                                    Map<String, IngredientPresentation> representativePresentationMap,
                                                    Set<String> directItemIds) {
        // Walk the list in order and take the first child that yields a representative item id.
        for (String resId : resIds) {
            if (directItemIds.contains(resId)) {
                return resId;
            }
            IngredientPresentation presentation = representativePresentationMap.get(resId);
            if (presentation != null && presentation.iconItemId() != null) {
                return presentation.iconItemId();
            }
        }
        return null;
    }

    @Nullable
    private static IngredientPresentation resolveRepresentativePresentationForResourceType(String resId) {
        GenericIngredientResolution resolution = ResourceTypeResolver.resolveGenericIngredient(
                new MaterialQuantity(null, resId, null, 1, null),
                false);
        List<String> indexedMatches = resolution.orderedMatchingItemIds();
        if (!indexedMatches.isEmpty()) {
            String representativeItemId = indexedMatches.get(0);
            return new IngredientPresentation(
                    representativeItemId,
                    representativeItemId,
                    representativeItemId.replace('_', ' '),
                    false);
        }

        // Fallback for cases where resolver index has not been initialized yet.
        List<String> fallbackMatches = new ArrayList<>();
        for (Map.Entry<String, Item> entry : Item.getAssetMap().getAssetMap().entrySet()) {
            Item item = entry.getValue();
            if (item == null || item.getResourceTypes() == null) continue;
            for (ItemResourceType rt : item.getResourceTypes()) {
                if (resId.equals(rt.id)) {
                    fallbackMatches.add(entry.getKey());
                    break;
                }
            }
        }
        fallbackMatches.sort(String.CASE_INSENSITIVE_ORDER);
        if (fallbackMatches.isEmpty()) {
            return null;
        }
        String representativeItemId = fallbackMatches.get(0);
        return new IngredientPresentation(
                representativeItemId,
                representativeItemId,
                representativeItemId.replace('_', ' '),
                false);
    }

}
