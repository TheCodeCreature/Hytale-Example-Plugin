---
id: F2605071530
type: feature
title: "Unified Affordability Resolver"
status: backlog
priority: high
epic: E2605071500
created: 2026-05-07
---

# Unified Affordability Resolver

## Description
Extract a shared RecipeAffordabilityResolver utility that consolidates the duplicated ingredient resolution chain and affordability checking logic currently spread across BlueprintSelectionPage, StencilVisualManager, and StencilRadialMenuPage into a single source of truth.

## Acceptance Criteria

### Checklist
- [ ] RecipeAffordabilityResolver utility class exists with no UI dependencies (no UICommandBuilder, Value, AffordabilityMode)
- [ ] Provides whole-recipe affordability check: isAffordable(recipe, container) → boolean
- [ ] Provides per-ingredient resolution with affordability: resolveIngredientCosts(recipe, category, container) → List<ResolvedIngredient>
- [ ] ResolvedIngredient includes: resolvedItemId, requiredQty, playerHas, sufficient
- [ ] Encapsulates the 3-step chain: getPerUnitCost → resolveInputItemId → resolveToGatherableForm
- [ ] BlueprintSelectionPage.updateDetailPanel() uses resolveIngredientCosts() instead of inline resolution chain
- [ ] StencilVisualManager.scanAndSend() uses isAffordable() instead of direct canRemoveMaterials()
- [ ] StencilRadialMenuPage.showCostArc() uses resolveIngredientCosts() instead of inline resolution chain
- [ ] Existing behavior unchanged: BlueprintBench detail panel, stencil hotbar glow, radial menu cost display all render identically

### Scenarios
**Ingredient resolution is consistent across subsystems**
- **Given** a recipe with a ResourceTypeId input "Wood_All"
- **When** BlueprintSelectionPage, StencilVisualManager, and StencilRadialMenuPage all resolve this ingredient
- **Then** all three resolve to the same concrete item ID via the same code path

**Per-ingredient affordability check**
- **Given** a recipe requiring 24x Wood_Planks and 12x Ingredient_Fibre
- **And** the player has 24x Wood_Planks but only 6x Ingredient_Fibre
- **When** resolveIngredientCosts() is called
- **Then** Wood_Planks returns sufficient=true, Ingredient_Fibre returns sufficient=false

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605071531 | Extract RecipeAffordabilityResolver | backlog |
| S2605071532 | Migrate Callers to Shared Resolver | backlog |

## Notes
- StencilVisualManager runs outside a UI context — the resolver must not depend on UI classes
- AffordabilityMode remains a UI-layer toggle in BlueprintSelectionPage — the resolver does not replace it
- BlockGroup interchangeability: currently BlueprintSelectionPage checks it but StencilVisualManager doesn't. Resolver should offer both variants.
