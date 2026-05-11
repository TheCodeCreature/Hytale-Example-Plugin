package com.CodeCreature.placeblock.ui.ingredienttree;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public class IngredientSelectionModel {

    private final Set<String> selectedNodes = new HashSet<>();
    private final IngredientTree tree;

    public IngredientSelectionModel(@Nonnull IngredientTree tree) {
        this.tree = tree;
    }

    public void toggle(String nodeId) {
        IngredientTreeNode node = tree.findNode(nodeId);
        if (node == null) return;

        switch (node.getNodeType()) {
            case META_GROUP: {
                IngredientGroup group = (IngredientGroup) node;
                CheckState state = getGroupState(group.getId());
                if (state == CheckState.ALL) {
                    deselectAll(group.getId());
                } else {
                    selectAll(group.getId());
                }
                break;
            }
            case RESOURCE_TYPE: {
                IngredientResourceType rt = (IngredientResourceType) node;
                if (selectedNodes.contains(rt.getId())) {
                    selectedNodes.remove(rt.getId());
                    for (IngredientExactItem child : rt.getChildren()) {
                        selectedNodes.remove(child.getId());
                    }
                } else {
                    selectedNodes.add(rt.getId());
                    for (IngredientExactItem child : rt.getChildren()) {
                        selectedNodes.add(child.getId());
                    }
                }
                break;
            }
            case EXACT_ITEM: {
                if (selectedNodes.contains(nodeId)) {
                    selectedNodes.remove(nodeId);
                } else {
                    selectedNodes.add(nodeId);
                }
                break;
            }
        }
    }

    public void selectAll(String nodeId) {
        IngredientTreeNode node = tree.findNode(nodeId);
        if (node == null) return;

        switch (node.getNodeType()) {
            case META_GROUP: {
                IngredientGroup group = (IngredientGroup) node;
                for (IngredientResourceType rt : group.getChildren()) {
                    selectedNodes.add(rt.getId());
                    for (IngredientExactItem item : rt.getChildren()) {
                        selectedNodes.add(item.getId());
                    }
                }
                break;
            }
            case RESOURCE_TYPE: {
                IngredientResourceType rt = (IngredientResourceType) node;
                selectedNodes.add(rt.getId());
                for (IngredientExactItem item : rt.getChildren()) {
                    selectedNodes.add(item.getId());
                }
                break;
            }
            case EXACT_ITEM: {
                selectedNodes.add(nodeId);
                break;
            }
        }
    }

    public void deselectAll(String nodeId) {
        IngredientTreeNode node = tree.findNode(nodeId);
        if (node == null) return;

        switch (node.getNodeType()) {
            case META_GROUP: {
                IngredientGroup group = (IngredientGroup) node;
                for (IngredientResourceType rt : group.getChildren()) {
                    selectedNodes.remove(rt.getId());
                    for (IngredientExactItem item : rt.getChildren()) {
                        selectedNodes.remove(item.getId());
                    }
                }
                break;
            }
            case RESOURCE_TYPE: {
                IngredientResourceType rt = (IngredientResourceType) node;
                selectedNodes.remove(rt.getId());
                for (IngredientExactItem item : rt.getChildren()) {
                    selectedNodes.remove(item.getId());
                }
                break;
            }
            case EXACT_ITEM: {
                selectedNodes.remove(nodeId);
                break;
            }
        }
    }

    public CheckState getState(String nodeId) {
        IngredientTreeNode node = tree.findNode(nodeId);
        if (node == null) return CheckState.NONE;

        switch (node.getNodeType()) {
            case META_GROUP:
                return getGroupState(nodeId);
            case RESOURCE_TYPE: {
                IngredientResourceType rt = (IngredientResourceType) node;
                if (rt.getChildren().isEmpty()) {
                    return selectedNodes.contains(nodeId) ? CheckState.ALL : CheckState.NONE;
                }
                boolean allSelected = true;
                boolean anySelected = false;
                if (selectedNodes.contains(rt.getId())) {
                    anySelected = true;
                } else {
                    allSelected = false;
                }
                for (IngredientExactItem child : rt.getChildren()) {
                    if (selectedNodes.contains(child.getId())) {
                        anySelected = true;
                    } else {
                        allSelected = false;
                    }
                }
                if (allSelected) return CheckState.ALL;
                if (anySelected) return CheckState.SOME;
                return CheckState.NONE;
            }
            case EXACT_ITEM:
                return selectedNodes.contains(nodeId) ? CheckState.ALL : CheckState.NONE;
            default:
                return CheckState.NONE;
        }
    }

    public CheckState getGroupState(String groupId) {
        IngredientTreeNode node = tree.findNode(groupId);
        if (node == null || node.getNodeType() != NodeType.META_GROUP) return CheckState.NONE;

        IngredientGroup group = (IngredientGroup) node;
        boolean allSelected = true;
        boolean anySelected = false;

        for (IngredientResourceType rt : group.getChildren()) {
            if (selectedNodes.contains(rt.getId())) {
                anySelected = true;
            } else {
                allSelected = false;
            }
            for (IngredientExactItem item : rt.getChildren()) {
                if (selectedNodes.contains(item.getId())) {
                    anySelected = true;
                } else {
                    allSelected = false;
                }
            }
        }

        if (allSelected) return CheckState.ALL;
        if (anySelected) return CheckState.SOME;
        return CheckState.NONE;
    }

    public Set<String> getSelectedResourceTypeIds() {
        Set<String> allRtIds = tree.getAllResourceTypeIds();
        return selectedNodes.stream()
                .filter(allRtIds::contains)
                .collect(Collectors.toSet());
    }

    public Set<String> getSelectedDirectItemIds() {
        Set<String> allDirectIds = tree.getAllDirectItemIds();
        return selectedNodes.stream()
                .filter(allDirectIds::contains)
                .collect(Collectors.toSet());
    }

    public Set<String> getSelectedExactItemIds() {
        Set<String> allExactIds = tree.getAllExactItemIds();
        return selectedNodes.stream()
                .filter(allExactIds::contains)
                .collect(Collectors.toSet());
    }

    public void clearAll() {
        selectedNodes.clear();
    }

    public boolean hasAnySelection() {
        return !selectedNodes.isEmpty();
    }

    public Set<String> getSelectedNodeIds() {
        return Collections.unmodifiableSet(selectedNodes);
    }

    public void restoreSelection(Collection<String> nodeIds) {
        selectedNodes.clear();
        for (String id : nodeIds) {
            if (tree.findNode(id) != null) {
                selectedNodes.add(id);
            }
        }
    }
}
