---
id: F2605061205
type: feature
title: "Dynamic Ingredient Tree Data Model"
status: backlog
priority: high
epic: E2605061200
created: 2026-05-06
---

# Dynamic Ingredient Tree Data Model

## Description
Build a data model that scans all recipe inputs across registered benches and organizes them into a three-tier tree structure: All_* meta-groups → ResourceType entries → ExactID entries. The tree is built once at init time using the existing ResourceTypeResolver infrastructure.

## Acceptance Criteria

### Checklist
- [ ] Scans all CraftingRecipe inputs (both ItemId and ResourceTypeId based)
- [ ] Resolves each input to its resource types via Item.getResourceTypes()
- [ ] Groups resource types into All_* meta-groups (e.g., all wood types under "All Wood")
- [ ] Each ResourceType node lists the concrete item IDs that declare it
- [ ] Tree is immutable after construction
- [ ] Handles edge cases: inputs with no resource types, duplicate items across resource types

### Scenarios
**Recipe with ResourceTypeId input**
- **Given** a recipe input with ResourceTypeId "Wood_Hardwood"
- **When** the ingredient tree is built
- **Then** "Wood_Hardwood" appears under the appropriate All_* group, and all items declaring ResourceType "Wood_Hardwood" appear as ExactID children

**Recipe with ItemId input**
- **Given** a recipe input with ItemId "Ingredient_Bone_Fragment"
- **When** the ingredient tree is built
- **Then** the item's declared resource types (e.g., "Bone") are added to the tree, and "Ingredient_Bone_Fragment" appears as an ExactID child under "Bone"

## Notes
- The existing ResourceTypeResolver.itemsWithResourceType() can find all items for a given ResourceTypeId
- The All_* groupings need a definition source — either hardcoded mapping (like current META_FILTER_MAP) or derived from engine ResourceType definitions
- Consider whether the tree should include ALL items declaring a ResourceType, or only items that actually appear as recipe inputs
