---
id: S2605291070
type: story
title: "Verify UI Tab Rendering with N Benches"
status: backlog
priority: medium
feature: F2605291020
epic: E2605291000
created: 2026-05-29
---

# Verify UI Tab Rendering with N Benches

## User Story
As a **player**, I want **all crafting bench tabs to appear and work correctly** so that **I can browse recipes from any bench**.

## Acceptance Criteria

### Checklist
- [ ] Tab bar renders tabs only for benches with block-producing recipes, plus the "All" tab
- [ ] Each tab displays its bench name with underscores replaced by spaces
- [ ] Selecting a tab filters recipes to only that bench
- [ ] The "All" tab still shows all recipes across all benches
- [ ] No visual glitches with 5+ tabs

### Scenarios
**Tab selection filtering**
- **Given** tabs: All, Builders, Furniture Bench, Workbench, Loom
- **When** player selects "Workbench"
- **Then** only Workbench recipes are shown in the grid

## Notes
- `BlueprintSelectionPage.loadRecipes()` already builds tabs dynamically from `RecipeFilterRegistry` — this story is primarily verification.
- If the Hytale tab component has a max tab count, that's a constraint to document.
