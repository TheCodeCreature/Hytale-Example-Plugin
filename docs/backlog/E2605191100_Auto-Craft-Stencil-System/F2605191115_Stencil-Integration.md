---
id: F2605191115
type: feature
title: "Stencil Integration"
status: backlog
priority: high
epic: E2605191100
created: 2026-05-19
---

# Stencil Integration

## Description
Wire the AutoCraftPlanner into the stencil placement system and visual affordability indicator. Replace the current direct `canRemoveMaterials`/`removeMaterials` flow with `AutoCraftPlanner.plan()` + execute. Update the visual indicator to use auto-craft-aware affordability.

## Acceptance Criteria

### Checklist
- [ ] `StencilPlacementSystem.handle()` uses `AutoCraftPlanner.plan()` for consumption
- [ ] Fast path behavior identical to current (no regression)
- [ ] Auto-craft path consumes correct raw materials atomically
- [ ] `StencilVisualManager` uses `isAffordableWithAutoCraft()` for green/red glow
- [ ] Stencil shows green when auto-craft is possible
- [ ] Stencil shows red only when neither direct nor auto-craft is possible
- [ ] `DropScaler.apply()` calls `RecipeTreeResolver.init()` at end

### Scenarios
**Stencil placement with auto-craft**
- **Given** player has stencil armed with brick stairs, 0 bricks, 50 cobblestone
- **When** player right-clicks to place
- **Then** 36 cobblestone consumed, brick stairs placed, stencil preserved

**Stencil visual green with auto-craft**
- **Given** player holds stencil armed with brick stairs, 0 bricks, 50 cobblestone
- **When** affordability visual updates
- **Then** stencil shows green (auto-craft available)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605191116 | Wire AutoCraftPlanner into StencilPlacementSystem | backlog |
| S2605191117 | Update StencilVisualManager for Auto-Craft Affordability | backlog |
| S2605191118 | Add RecipeTreeResolver.init() to DropScaler | backlog |

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.1, §8.3, §8.7
