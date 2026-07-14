---
id: F2605291020
type: feature
title: "Dynamic UI Tab Verification"
status: backlog
priority: medium
epic: E2605291000
created: 2026-05-29
---

# Dynamic UI Tab Verification

## Description
Verify and adjust the Stencil Crafting UI to properly handle dynamically discovered bench tabs. Only benches whose recipes produce placeable blocks should get a tab — non-block recipe benches (e.g., tool-only crafting) are excluded from the tab bar. The UI already discovers tabs from `RecipeFilterRegistry`, but needs validation with more than the current 2 bench tabs.

## Acceptance Criteria

### Checklist
- [ ] UI tab bar renders correctly with 5+ bench tabs
- [ ] Tab display names are human-readable (underscores replaced with spaces)
- [ ] Tab filtering works correctly — selecting a tab shows only that bench's recipes
- [ ] Preferences persistence works for dynamically discovered tabs
- [ ] "All" tab continues to show recipes from every bench

### Scenarios
**Many tabs render correctly**
- **Given** BenchRegistry has discovered 6 bench IDs
- **When** a player opens the Stencil Crafting UI
- **Then** 7 tabs appear (All + 6 benches) and all are selectable

**Persisted tab survives restart**
- **Given** a player selected the "Workbench" tab and closed the UI
- **When** the player reopens the Stencil Crafting UI
- **Then** "Workbench" is still the active tab

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605291070 | Verify UI Tab Rendering with N Benches | backlog |
| S2605291075 | Validate BlueprintBookPrefs for Dynamic Tabs | backlog |

## Notes
- `BlueprintSelectionPage.loadRecipes()` already collects bench IDs dynamically from entries. The main risk is whether the Hytale `#BenchTabs` UI component handles many tabs gracefully.
- If the tab bar overflows, a scrollable tab bar or dropdown may be needed (future enhancement).
