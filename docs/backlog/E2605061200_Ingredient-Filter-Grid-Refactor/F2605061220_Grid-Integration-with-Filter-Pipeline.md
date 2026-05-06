---
id: F2605061220
type: feature
title: "Grid Integration with Filter Pipeline"
status: backlog
priority: high
epic: E2605061200
created: 2026-05-06
---

# Grid Integration with Filter Pipeline

## Description
Wire the new ingredient tree grid into the existing RecipeFilterPipeline, replacing the current ResourceTypeRegistry-based filtering. The selected entries (at any tier) are expanded to a set of ResourceTypeIds and/or exact ItemIds, then used by recipeMatchesAnyResourceType() to filter recipes.

## Acceptance Criteria

### Checklist
- [ ] Selected All_* groups expand to all their ResourceType children for matching
- [ ] Selected ResourceType entries match via recipeMatchesAnyResourceType()
- [ ] Selected ExactID entries match by direct ItemId comparison
- [ ] Mixed selections (some groups + some exact IDs) combine with OR logic
- [ ] applyFilter() uses the new tree model instead of ResourceTypeRegistry
- [ ] Affordability mode integration unchanged
- [ ] Preferences save/load the selected entries

### Scenarios
**Player selects a ResourceType**
- **Given** the player selects "Wood_Hardwood" from the tree
- **When** applyFilter() runs
- **Then** all recipes with inputs matching ResourceType "Wood_Hardwood" are shown

**Player selects an exact item**
- **Given** the player selects "Wood_Hardwood_Planks" from the tree
- **When** applyFilter() runs
- **Then** only recipes with "Wood_Hardwood_Planks" as a direct input are shown

**Mixed selection**
- **Given** the player selects "All Rock" group and "Wood_Hardwood_Planks" exact ID
- **When** applyFilter() runs
- **Then** recipes matching ANY rock type OR using Wood_Hardwood_Planks as input are shown

## Notes
- ResourceTypeRegistry may become obsolete or reduced to just holding the All_* group definitions
- The current META_FILTER_MAP concept maps well to the All_* group tier
- Need to decide whether to keep ResourceTypeRegistry for backward compatibility or fully replace
