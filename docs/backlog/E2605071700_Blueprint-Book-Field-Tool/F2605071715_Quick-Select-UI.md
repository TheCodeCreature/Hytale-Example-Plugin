---
id: F2605071715
type: feature
title: "Quick Select UI (Q-key subset view)"
status: backlog
priority: high
epic: E2605071700
created: 2026-05-07
---

# Quick Select UI (Q-key subset view)

## Description
Pressing Q while holding the Blueprint Book opens a compact grid showing only recipes the player has previously encountered. Selecting a recipe gives the stencil and closes the UI.

## Acceptance Criteria

### Checklist
- [ ] Q-key opens a flat grid UI (4×3, 12 items per page)
- [ ] Only previously encountered recipes appear
- [ ] Unaffordable recipes show dim overlay
- [ ] Clicking a recipe gives the stencil and closes the UI
- [ ] Pagination works when more than 12 recipes encountered
- [ ] Empty state shows hint to visit a bench

### Scenarios
**Select from Quick Select**
- **Given** player has 5 encountered recipes, holds Blueprint Book
- **When** player presses Q, clicks on Hardwood Planks
- **Then** stencil for Hardwood Planks appears in hotbar, UI closes

**Empty encounter set**
- **Given** player has never visited a bench, holds Blueprint Book
- **When** player presses Q
- **Then** UI opens showing empty state with hint message

**Pagination**
- **Given** player has 20 encountered recipes
- **When** player presses Q
- **Then** first 12 shown, next/prev buttons navigate pages

## Notes
- Reuses shared component library (`ClickableIconCell.ui`, `CostCell.ui`)
- Reuses `RecipeAffordabilityResolver` for dim overlays
- Does NOT include filters, search, tabs, or set browsing
