---
id: F2604271700
type: feature
title: "Unified Recipe Filter Registry"
status: backlog
priority: high
epic: E2604201200
created: 2026-04-27
---

# Unified Recipe Filter Registry

## Description
Extract the common recipe scanning, filtering, and resolution logic from `BlueprintSelectionPage.loadRecipes()` and `BenchRecipeRegistry.init()` into a shared `RecipeFilterRegistry` that both systems query. This eliminates duplicated iteration, inconsistent bench matching, divergent ResourceType resolution, and multiple definitions of the allowed bench ID list.

## Acceptance Criteria

### Checklist
- [ ] A single `RecipeFilterRegistry` holds the filtered recipe data used by both systems
- [ ] Bench IDs are defined in exactly one `public static` field
- [ ] BenchRequirement matching scans all entries (not just the first)
- [ ] ResourceType→ItemId resolution delegates to `ResourceTypeResolver` everywhere
- [ ] `BlueprintSelectionPage` queries the shared registry instead of scanning recipes itself
- [ ] `BenchRecipeRegistry` queries the shared registry instead of scanning recipes itself
- [ ] `DropScaler` initializes the registry once; both consumers read from it
- [ ] No behavioral change to either the UI or the drop scaling pipeline
- [ ] Input validation (null checks, valid itemId or resourceTypeId) lives in one place

### Scenarios
**Recipe appears in both systems**
- **Given** a recipe "Wood_Hardwood_Fence" with `BenchRequirement: [{Id: "Builders"}]` and `ResourceTypeId` input
- **When** the shared registry loads
- **Then** both `BlueprintSelectionPage` and `BenchRecipeRegistry` see the recipe with consistent bench matching and resolved ingredient item IDs

**Bench ID list changed**
- **Given** a developer adds `"Blacksmith"` to the shared bench ID list
- **When** both systems initialize
- **Then** both include Blacksmith recipes without separate code changes

**Recipe with multiple BenchRequirements**
- **Given** a recipe with `BenchRequirement: [{Id: "Builders"}, {Id: "Furniture_Bench"}]`
- **When** filtered with `benchId = "Furniture_Bench"`
- **Then** the recipe is included (matches on second entry, not just first)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604271710 | Extract Shared Recipe Predicate Pipeline | backlog |
| S2604271720 | Migrate BlueprintSelectionPage to Shared Registry | backlog |
| S2604271730 | Migrate BenchRecipeRegistry to Shared Registry | backlog |
| S2604271740 | Unify Bench ID Definitions | backlog |

## Notes
- Risk: BenchRecipeRegistry has downstream consumers (DropScaler, BenchBlockClassifier, NaturalResourceRegistry) that depend on its current API shape. Migration must preserve those contracts.
- Risk: BlueprintSelectionPage has UI-specific concerns (set grouping, affordability) that must remain layered on top of the shared registry.
- The shared registry should be a data provider, not an opinion about what to do with the data.
