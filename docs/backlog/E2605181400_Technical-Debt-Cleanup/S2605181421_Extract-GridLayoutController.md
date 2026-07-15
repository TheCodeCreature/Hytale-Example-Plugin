---
id: S2605181421
type: story
title: "Extract GridLayoutController"
status: done
priority: medium
feature: F2605181420
epic: E2605181400
created: 2026-05-18
---

# Extract GridLayoutController

## User Story
As a **developer**, I want **grid rendering logic in a focused controller** so that **layout computation and cell rendering are testable and maintainable independently of the page's event handling**.

## Acceptance Criteria

### Checklist
- [ ] `GridLayoutController.java` created in `com.CodeCreature.ui.bench`
- [ ] Methods moved: `updateRecipeGrid()`, `hideRemainingCells()`, `buildRecipeGridBindings()`
- [ ] Fields moved: `cellSlotToRecipeIndex`, `groupCellOffset`, `cellsPerSet`, `setNameToGroupIndex`, `totalSetCount`, `maxLayoutSetNames`, `totalCellCount`
- [ ] `resolveRecipeIndex(int slotIdx)` exposed for event routing
- [ ] `StencilSelectionPage` delegates all grid rendering to controller
- [ ] Grid renders identically to current behavior

### Scenarios
**Grid rendering delegation**
- **Given** the filter pipeline produces a new `displayedRecipes` list
- **When** `gridController.updateUI(cmd, displayedRecipes, selectedRecipeId)` is called
- **Then** all grid cells render with correct icons, set headers, and selection state

## Notes
- ~150 lines extracted
- Constructor takes `MaxLayoutInfo` computed by the page
- Controller never calls `sendUpdate()` — only writes to `UICommandBuilder`
- Wave 1 of the decomposition (no dependency on Wave 2)
