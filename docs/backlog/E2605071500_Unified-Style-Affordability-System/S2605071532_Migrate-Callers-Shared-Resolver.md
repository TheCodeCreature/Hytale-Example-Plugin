---
id: S2605071532
type: story
title: "Migrate Callers to Shared Resolver"
status: backlog
priority: high
feature: F2605071530
epic: E2605071500
created: 2026-05-07
---

# Migrate Callers to Shared Resolver

## User Story
As a **plugin developer**, I want **all three affordability consumers to use RecipeAffordabilityResolver** so that **ingredient resolution and inventory checking are consistent everywhere**.

## Acceptance Criteria

### Checklist
- [ ] BlueprintSelectionPage.updateDetailPanel() calls resolveIngredientCosts() instead of inline 3-step chain
- [ ] StencilVisualManager.scanAndSend() calls isAffordable() instead of direct canRemoveMaterials()
- [ ] StencilRadialMenuPage.showCostArc() calls resolveIngredientCosts() instead of inline 3-step chain
- [ ] Duplicated imports of ResourceTypeResolver and NaturalResourceRegistry removed from migrated callers
- [ ] Existing tests pass without modification
- [ ] No visual regression in BlueprintBook, stencil hotbar glow, or radial menu

### Scenarios
**BlueprintBook detail panel unchanged**
- **Given** updateDetailPanel() now uses resolveIngredientCosts()
- **When** a player selects a recipe in the BlueprintBook
- **Then** cost cells show the same item icons, quantities, and red/white coloring as before

**Stencil hotbar glow unchanged**
- **Given** scanAndSend() now uses isAffordable()
- **When** a player's inventory changes
- **Then** stencil items in the hotbar glow green/red exactly as before

## Notes
- BlueprintSelectionPage.isAffordable() also does BlockGroup checks — keep that as a wrapper around the resolver
- AffordabilityMode toggle stays in BlueprintSelectionPage — not moved to resolver
