---
id: F2605191110
type: feature
title: "Auto-Craft Consumption Planning"
status: backlog
priority: high
epic: E2605191100
created: 2026-05-19
---

# Auto-Craft Consumption Planning

## Description
Build the runtime planning system that computes what to consume from a player's inventory. Two-pass algorithm: fast path uses direct `canRemoveMaterials()` when the player has all intermediates; slow path computes per-ingredient deficit, resolves crafted deficits to raw materials via `RecipeTreeResolver`, and produces an atomic consumption list.

## Acceptance Criteria

### Checklist
- [ ] Fast path returns direct consumption plan when player has all intermediates
- [ ] Slow path correctly identifies per-ingredient deficit
- [ ] Existing intermediates are used first (Contract #16)
- [ ] Deficits for crafted intermediates resolved to raw materials via RecipeTreeResolver
- [ ] Total raw material needs aggregated across all deficits
- [ ] Plan is unaffordable if raw materials are insufficient
- [ ] `RecipeAffordabilityResolver.isAffordableWithAutoCraft()` method added

### Scenarios
**Fast path — player has all intermediates**
- **Given** brick stairs recipe needs 3 bricks, player has 5 bricks
- **When** `AutoCraftPlanner.plan()` is called
- **Then** returns plan with `requiresAutoCraft=false`, consuming 3 bricks

**Slow path — full auto-craft**
- **Given** brick stairs recipe needs 3 bricks, player has 0 bricks, 50 cobblestone
- **When** `AutoCraftPlanner.plan()` is called
- **Then** returns plan with `requiresAutoCraft=true`, consuming 36 cobblestone

**Mixed inventory**
- **Given** recipe needs 3 bricks, player has 1 brick and 30 cobblestone
- **When** `AutoCraftPlanner.plan()` is called
- **Then** returns plan consuming 1 brick + 24 cobblestone (deficit of 2 bricks × 12 cobblestone each)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605191111 | Implement AutoCraftPlanner | backlog |
| S2605191112 | Add isAffordableWithAutoCraft to RecipeAffordabilityResolver | backlog |

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §4.2, §6.2
