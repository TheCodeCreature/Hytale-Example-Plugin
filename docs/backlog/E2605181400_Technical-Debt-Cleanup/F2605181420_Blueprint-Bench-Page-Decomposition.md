---
id: F2605181420
type: feature
title: "Blueprint Bench Page Decomposition"
status: backlog
priority: medium
epic: E2605181400
created: 2026-05-18
---

# Blueprint Bench Page Decomposition

## Description
Extract two render-only controllers from `BlueprintSelectionPage` following the proven `IngredientTreeGridController` pattern: `GridLayoutController` owns grid rendering and indirection arrays (~150 lines), `DetailPanelController` owns the cost/output detail panel (~100 lines). The page remains the event entry point per `InteractiveCustomUIPage` contract but delegates rendering to these focused controllers.

## Acceptance Criteria

### Checklist
- [ ] `BlueprintSelectionPage` drops below 800 lines (from ~1060)
- [ ] `GridLayoutController` exists with `buildUI()` and `updateUI()` methods
- [ ] `DetailPanelController` exists with `updateUI()` method
- [ ] Grid renders identically (all cells, icons, selection highlight, dim overlays)
- [ ] Detail panel renders identically (output icon, name, cost grid, affordability coloring)
- [ ] Event routing unchanged — recipe selection still works end-to-end
- [ ] All 3 affordability modes render correctly
- [ ] Stencil creation flow works end-to-end
- [ ] Build passes clean

### Scenarios
**Full filter pipeline post-extraction**
- **Given** the bench UI is opened
- **When** player switches tabs, searches, toggles set filters, changes affordability mode
- **Then** grid updates correctly with matching recipes, dimmed unaffordable cells, proper set grouping

**Recipe selection**
- **Given** any recipe is visible in the grid
- **When** player clicks it
- **Then** detail panel shows output icon, name, and per-ingredient cost with affordability coloring

**Stencil creation**
- **Given** an affordable recipe is selected
- **When** player clicks "Create Blueprint"
- **Then** a stencil item appears in their inventory with correct BSON metadata

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605181421 | Extract GridLayoutController | backlog |
| S2605181422 | Extract DetailPanelController | backlog |
| S2605181423 | Wire Controllers into BlueprintSelectionPage | backlog |

## Notes
- Design doc: docs/design-deferred-fixes.md (Item #17)
- Architecture assessment: docs/review-refactor-blast-radius.md (Finding #17)
- Pattern reference: `IngredientTreeGridController` already proves this delegation model
- Product Owner condition: Must verify full filter pipeline post-extraction before shipping. If verification fails, revert — don't patch forward.
- Deferred to Sprint 2 per Product Owner (regression risk not worth accepting alongside #5)
