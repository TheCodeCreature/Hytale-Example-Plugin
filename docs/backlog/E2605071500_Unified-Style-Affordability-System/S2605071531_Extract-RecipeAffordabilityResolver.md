---
id: S2605071531
type: story
title: "Extract RecipeAffordabilityResolver"
status: backlog
priority: high
feature: F2605071530
epic: E2605071500
created: 2026-05-07
---

# Extract RecipeAffordabilityResolver

## User Story
As a **plugin developer**, I want **a single utility for checking recipe affordability and resolving ingredients** so that **the 3-step resolution chain and inventory checks are not duplicated across 3 files**.

## Acceptance Criteria

### Checklist
- [ ] RecipeAffordabilityResolver class in the placeblock package (shared, not UI-specific)
- [ ] isAffordable(CraftingRecipe, CombinedItemContainer) → boolean
- [ ] resolveIngredientCosts(CraftingRecipe, BenchCategory, CombinedItemContainer) → List<ResolvedIngredient>
- [ ] ResolvedIngredient record with: resolvedItemId, requiredQty, playerHas, sufficient
- [ ] No dependencies on UI classes (UICommandBuilder, Value, AffordabilityMode)
- [ ] Unit tests covering: single ingredient affordable, single ingredient unaffordable, ResourceTypeId resolution, multi-ingredient mixed affordability, null/empty recipe handling

## Notes
- Place in com.UnobstructedThirdPerson.placeblock (shared between bench and stencil subsystems)
- The method subsumes PlaceBlockCostUtil.getPerUnitCost() + ResourceTypeResolver.resolveInputItemId() + NaturalResourceRegistry.resolveToGatherableForm() + countItemInInventory()
