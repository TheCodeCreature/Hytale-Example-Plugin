---
id: S2605181422
type: story
title: "Extract DetailPanelController"
status: done
priority: medium
feature: F2605181420
epic: E2605181400
created: 2026-05-18
---

# Extract DetailPanelController

## User Story
As a **developer**, I want **detail panel rendering in a focused controller** so that **cost display and affordability coloring logic is isolated and testable**.

## Acceptance Criteria

### Checklist
- [ ] `DetailPanelController.java` created in `com.CodeCreature.ui.bench`
- [ ] `updateDetailPanel()` logic moved to controller's `updateUI()` method
- [ ] Controller takes: selected recipe entry, affordability mode, player inventory container
- [ ] Controller produces: UICommandBuilder commands for output icon, name, cost grid
- [ ] `BlueprintSelectionPage` delegates detail panel rendering to controller
- [ ] Detail panel renders identically to current behavior

### Scenarios
**Detail panel rendering delegation**
- **Given** a recipe is selected
- **When** `detailController.updateUI(cmd, selectedRecipe, mode, inventory)` is called
- **Then** output icon, name, and per-ingredient cost grid render with correct affordability coloring

## Notes
- ~100 lines extracted
- No dependency on GridLayoutController (Wave 2, parallel to Wave 1)
- Controller never calls `sendUpdate()` — only writes to `UICommandBuilder`
