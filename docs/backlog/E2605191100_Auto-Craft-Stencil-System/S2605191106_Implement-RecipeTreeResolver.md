---
id: S2605191106
type: story
title: "Implement RecipeTreeResolver with Cache"
status: backlog
priority: high
feature: F2605191105
epic: E2605191100
created: 2026-05-19
---

# Implement RecipeTreeResolver with Cache

## User Story
As a **system**, I want **a pre-computed map of crafted items to their raw material costs** so that **auto-craft affordability checks are O(1) at runtime**.

## Acceptance Criteria

### Checklist
- [ ] `init()` builds `recipeByOutputItem` map from all non-Salvage recipes (crafting AND processing benches)
- [ ] `init()` pre-computes `rawCostCache` for all items in `RecipeTierClassifier.craftedItemIds`
- [ ] `resolveItemToRaw(itemId)` returns cached raw material list or null for raw items
- [ ] `resolveRecipeToRaw(recipe)` returns total raw cost for one output unit
- [ ] `findRecipeFor(itemId)` returns the crafting recipe or null
- [ ] Multi-recipe items: store cheapest by total raw material quantity
- [ ] Cycle detection via `Set<String> visited` prevents infinite recursion
- [ ] ResourceTypeId inputs resolved via `ResourceTypeResolver.resolveInputItemId()` with full variant enumeration
- [ ] Items mapped through `NaturalResourceRegistry.resolveToGatherableForm()`
- [ ] Duplicate raw materials merged (quantities summed)
- [ ] Processing bench recipes (smelting, stonecutting, refining) included in resolution tree
- [ ] `init()` called after `DropScaler.applyModifications()` (added to `DropScaler.apply()`)

### Scenarios
**Init populates cache**
- **Given** the standard test data set with planks, slabs, rails, doors
- **When** `RecipeTreeResolver.init()` is called
- **Then** `resolveItemToRaw("Wood_Planks_Oak")` returns `[{Wood_Log_Oak, 6}]`

**Raw item returns null**
- **Given** initialized resolver
- **When** `resolveItemToRaw("Rock_Stone")` is called
- **Then** returns null (raw material)

## Notes
Skeleton file already exists at `src/main/java/com/CodeCreature/crafting/RecipeTreeResolver.java`.
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §4.1, §6.1
