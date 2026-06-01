---
id: S2605051200
type: story
title: "Dynamic Cells Per Set Group"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-05-05
---

# Dynamic Cells Per Set Group

## User Story
As a **player**, I want **each set group in the Blueprint Bench grid to show all its items** so that **I can browse and select any recipe in a set without items being silently hidden**.

## Acceptance Criteria

### Checklist
- [ ] Each set group displays ALL its recipes (10-20+), not a fixed cap of 9
- [ ] The grid scrolls or wraps to accommodate variable-sized groups
- [ ] Performance remains acceptable with groups of 20+ items
- [ ] The `CELLS_PER_GROUP` constant is removed or made dynamic
- [ ] The `cellInGroup >= CELLS_PER_GROUP` overflow skip is removed

### Scenarios
**Set with 15 items**
- **Given** the "Tavern" set has 15 recipes in the pipeline output
- **When** the player views the Blueprint Bench grid
- **Then** all 15 Tavern items are visible in the Tavern group

**Set with 3 items**
- **Given** the "Wool Red" set has 3 recipes
- **When** the player views the Blueprint Bench grid
- **Then** all 3 items display (no empty slots padded beyond the 3)

## Notes
- Current code: `CELLS_PER_GROUP = 9` at `BlueprintSelectionPage.java:50`
- Overflow is silently skipped at `BlueprintSelectionPage.java:588`: `if (cellInGroup >= CELLS_PER_GROUP) continue;`
- The `MAX_RECIPE_CELLS` constant (`MAX_SET_GROUPS * CELLS_PER_GROUP = 180`) will also need adjustment
- The UI template `BlueprintBookPage.ui` defines the grid structure — the Engineer must check whether `#GroupCells` elements are dynamically appendable or statically capped
- Risk: If the UI framework requires a fixed number of cell elements, the approach may need to pre-allocate a larger pool and hide unused slots
