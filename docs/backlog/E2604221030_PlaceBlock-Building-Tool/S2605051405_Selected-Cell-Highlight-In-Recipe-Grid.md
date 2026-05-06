---
id: S2605051405
type: story
title: "Selected Cell Highlight in Recipe Grid"
status: backlog
priority: high
feature: F2605051400
epic: E2604221030
created: 2026-05-05
---

# Selected Cell Highlight in Recipe Grid

## User Story
As a **player**, I want **the selected recipe cell to be visually highlighted** so that **I can clearly see which recipe I'm viewing details for**.

## Acceptance Criteria

### Checklist
- [ ] A new `@SelectedCellStyle` TextButtonStyle is defined in BlueprintBenchStyles.ui using Primary_Square backgrounds
- [ ] When a recipe cell is selected, `#CellBtn.Style` is set to `@SelectedCellStyle`
- [ ] When a recipe cell is NOT selected, `#CellBtn.Style` remains `@TransparentButtonStyle`
- [ ] An unaffordable selected cell shows BOTH the selected cell style AND the dim overlay simultaneously
- [ ] The selected state persists across filter changes if the recipe remains visible
- [ ] Only one cell is highlighted at a time

### Scenarios
**Player clicks a recipe cell**
- **Given** no recipe is currently selected
- **When** the player clicks a recipe cell
- **Then** that cell's background changes to the Primary_Square style, and the detail panel updates

**Player switches selection**
- **Given** recipe A is selected and highlighted
- **When** the player clicks recipe B
- **Then** recipe A returns to transparent style, and recipe B shows the highlighted style

## Notes
- The `#CellBtn` overlay already exists in RecipeIconCell.ui — its Style property toggles between transparent and selected
- The `#CellDim` overlay for unaffordable items is independent and layered separately, so both can be visible simultaneously
