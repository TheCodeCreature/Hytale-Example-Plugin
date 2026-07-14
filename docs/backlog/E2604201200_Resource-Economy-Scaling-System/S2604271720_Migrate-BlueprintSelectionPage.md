---
id: S2604271720
type: story
title: "Migrate BlueprintSelectionPage to Shared Registry"
status: backlog
priority: high
feature: F2604271700
epic: E2604201200
created: 2026-04-27
---

# Migrate BlueprintSelectionPage to Shared Registry

## User Story
As a **developer**, I want **BlueprintSelectionPage to query the shared RecipeFilterRegistry** so that **it benefits from consistent filtering and ResourceType resolution**.

## Acceptance Criteria

### Checklist
- [ ] `loadRecipes()` is replaced by a query against `RecipeFilterRegistry`
- [ ] `resolveIngredientItemId()` is removed — delegates to `ResourceTypeResolver`
- [ ] `ALLOWED_BENCHES` is removed — reads from the shared bench ID list
- [ ] UI-specific concerns (set grouping, tab/set filtering, affordability, search) remain in `BlueprintSelectionPage`
- [ ] No visible change to the Stencil Crafting UI behavior

### Scenarios
**Same UI with shared data source**
- **Given** a player opens the Stencil Crafting
- **When** recipes load from the shared registry
- **Then** the same tabs, set filters, icons, and ingredient displays appear as before

## Notes
- `applyFilter()` stays in BlueprintSelectionPage — it's UI-specific (tab, set, search, affordability).
