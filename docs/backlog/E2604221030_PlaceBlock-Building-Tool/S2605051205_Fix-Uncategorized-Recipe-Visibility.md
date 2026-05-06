---
id: S2605051205
type: story
title: "Fix Uncategorized Recipe Visibility"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-05-05
---

# Fix Uncategorized Recipe Visibility

## User Story
As a **player**, I want **recipes without a set assignment to appear in the grid when the Uncategorized toggle is on** so that **no recipes are silently hidden from the Blueprint Bench**.

## Acceptance Criteria

### Checklist
- [ ] When `showUncategorized = true` and no manual set filters are active, recipes with `Item.set == null` appear in the grid under an "Uncategorized" group
- [ ] When `showUncategorized = false`, uncategorized recipes are hidden (current default behavior, but now correctly implemented)
- [ ] The `showUncategorized` toggle visibly affects the grid (not dead code)
- [ ] The Uncategorized set name appears in the sidebar set list when `showUncategorized = true`

### Scenarios
**Uncategorized toggle ON, no set filters**
- **Given** the player has not selected any specific sets in the sidebar
- **And** the Uncategorized toggle is ON
- **When** the pipeline executes
- **Then** recipes with null `Item.set` appear in an "Uncategorized" group in the grid

**Uncategorized toggle OFF**
- **Given** the Uncategorized toggle is OFF
- **When** the pipeline executes
- **Then** recipes with null `Item.set` do not appear in the grid

## Notes
- **Root cause:** `extractSets()` at `RecipeFilterPipeline.java:337` unconditionally removes `UNCATEGORIZED_SET` from `visibleSets`. Then `effectiveSetFilter = visibleSets` (line 199-200) excludes uncategorized recipes via `filterBySets()` BEFORE the `showUncategorized` guard runs (line 203). The guard is dead code.
- **Fix approach:** Either include `UNCATEGORIZED_SET` in `visibleSets` when `showUncategorized=true`, or move the uncategorized exclusion logic before the `filterBySets` step.
- The `execute()` method needs the `showUncategorized` parameter to influence `visibleSets` construction, not just a post-filter removeIf.
