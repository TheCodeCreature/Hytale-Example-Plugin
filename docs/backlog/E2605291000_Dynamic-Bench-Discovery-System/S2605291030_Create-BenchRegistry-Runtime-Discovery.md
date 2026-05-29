---
id: S2605291030
type: story
title: "Create BenchRegistry with Runtime Discovery"
status: backlog
priority: high
feature: F2605291005
epic: E2605291000
created: 2026-05-29
---

# Create BenchRegistry with Runtime Discovery

## User Story
As a **plugin developer**, I want **bench IDs discovered automatically from recipe assets** so that **new benches are supported without code changes**.

## Acceptance Criteria

### Checklist
- [ ] `BenchRegistry` class exists in the `registry` package
- [ ] `BenchRegistry.init(Set<String> denyList)` scans all `CraftingRecipe` assets and collects every unique `BenchRequirement.id`
- [ ] Denied bench IDs are excluded during scanning
- [ ] Each bench ID is stored with a `preferNatural` flag (default `false`)
- [ ] Known override: `"Furniture_Bench"` defaults to `preferNatural=true`
- [ ] `BenchRegistry.getAllBenchIds()` returns all discovered, non-denied bench IDs
- [ ] `BenchRegistry.isPreferNatural(String benchId)` returns the preference for a bench
- [ ] `BenchRegistry.getBenchIdsForRecipe(CraftingRecipe)` returns the set of matching bench IDs for a recipe
- [ ] Replaces `BenchCategory.allBenchIds()` as the single source of truth

### Scenarios
**Auto-discovery**
- **Given** the game has recipes for Builders, Furniture_Bench, Workbench, and Loom
- **When** `BenchRegistry.init(Set.of())` runs
- **Then** all four bench IDs are registered

**Deny list exclusion**
- **Given** the deny list is `["Loom"]`
- **When** `BenchRegistry.init(Set.of("Loom"))` runs
- **Then** only Builders, Furniture_Bench, and Workbench are registered

**Furniture preference override**
- **Given** `BenchRegistry` discovers "Furniture_Bench"
- **When** preferences are resolved
- **Then** `isPreferNatural("Furniture_Bench")` returns `true`

## Notes
- This replaces the role of the `BenchCategory` enum for bench ID tracking.
- `BenchCategory.fromRecipe()` logic should move into `BenchRegistry` as a dynamic lookup.
- The `preferNatural` overrides could later be config-driven, but hardcoded overrides for known benches are acceptable initially.
