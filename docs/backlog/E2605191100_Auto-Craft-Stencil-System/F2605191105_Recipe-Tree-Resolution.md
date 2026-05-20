---
id: F2605191105
type: feature
title: "Recipe Tree Resolution"
status: backlog
priority: high
epic: E2605191100
created: 2026-05-19
---

# Recipe Tree Resolution

## Description
Build a recursive recipe resolver that maps any crafted item to its raw material cost. Results are pre-computed at server init (after DropScaler runs) and cached for O(1) runtime lookups. Handles cycle detection, ResourceTypeId resolution, and Salvage/processing recipe exclusion.

## Acceptance Criteria

### Checklist
- [ ] `RecipeTreeResolver.init()` pre-computes raw costs for all crafted items
- [ ] `resolveItemToRaw("Wood_Planks_Oak")` returns `[{Wood_Log_Oak, 6}]` (12 logs / 2 output)
- [ ] `resolveItemToRaw("Rock_Stone")` returns null (raw material — no recipe)
- [ ] Multi-tier chains resolve correctly (stairs → bricks → cobblestone)
- [ ] Cycle detection produces warning log and treats cycled item as terminal
- [ ] ResourceTypeId inputs at intermediate tiers resolve correctly
- [ ] Salvage recipes are excluded from resolution
- [ ] Processing bench recipes (smelting, stonecutting, refining) included in resolution tree
- [ ] Multi-recipe items: cheapest by total raw material quantity selected

### Scenarios
**Single-tier crafted item**
- **Given** a recipe: 1× Wood_Log_Oak → 2× Wood_Planks_Oak (scaled: 12× Wood_Log_Oak)
- **When** resolveItemToRaw("Wood_Planks_Oak") is called
- **Then** returns [{Wood_Log_Oak, 6}] (12 logs per-unit / 2 output = 6 per plank)

**Multi-tier crafted item**
- **Given** brick stairs: 3× brick → 1× brick_stairs; brick: 1× cobblestone → 1× brick
- **When** resolveItemToRaw("brick_stairs") is called
- **Then** returns [{cobblestone, 36}] (3 bricks × 12 cobblestone per brick)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605191106 | Implement RecipeTreeResolver with Cache | backlog |
| S2605191107 | Implement Supporting Records | backlog |

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §4.1, §6.1
