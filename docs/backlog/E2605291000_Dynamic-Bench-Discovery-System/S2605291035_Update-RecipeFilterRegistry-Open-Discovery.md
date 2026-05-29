---
id: S2605291035
type: story
title: "Update RecipeFilterRegistry for Open Bench Discovery"
status: backlog
priority: high
feature: F2605291005
epic: E2605291000
created: 2026-05-29
---

# Update RecipeFilterRegistry for Open Bench Discovery

## User Story
As a **plugin developer**, I want **RecipeFilterRegistry to index recipes for all discovered benches** so that **new bench recipes appear in the UI and get drop scaling**.

## Acceptance Criteria

### Checklist
- [ ] `RecipeFilterRegistry.init()` uses `BenchRegistry.getAllBenchIds()` instead of `BenchCategory.allBenchIds()`
- [ ] All references to `BenchCategory.allBenchIds()` are replaced
- [ ] `FilteredRecipeEntry` stores bench IDs as `Set<String>` (no change — already does this)
- [ ] `FilteredRecipeEntry` no longer stores a `BenchCategory` enum value — stores bench config data instead or derives it dynamically
- [ ] Recipes for newly discovered benches (e.g., Workbench) appear in `getEntriesForBench("Workbench")`

### Scenarios
**Workbench recipes indexed**
- **Given** 15 recipes have `BenchRequirement.id = "Workbench"`
- **When** `RecipeFilterRegistry.init()` completes
- **Then** `getEntriesForBench("Workbench")` returns 15 entries

## Notes
- `FilteredRecipeEntry` currently stores a `BenchCategory` — this field needs to be replaced or made optional since `BenchCategory` is being removed.
- The `extractMatchingBenchIds()` method already does generic matching against an allowed set — just needs the allowed set to come from `BenchRegistry` instead.
