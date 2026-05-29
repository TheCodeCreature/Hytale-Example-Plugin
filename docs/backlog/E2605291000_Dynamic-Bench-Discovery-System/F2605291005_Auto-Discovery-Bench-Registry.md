---
id: F2605291005
type: feature
title: "Auto-Discovery Bench Registry"
status: backlog
priority: high
epic: E2605291000
created: 2026-05-29
---

# Auto-Discovery Bench Registry

## Description
Replace the hardcoded `BenchCategory` enum with a runtime `BenchRegistry` that discovers all bench IDs by scanning `CraftingRecipe` assets. The registry becomes the single source of truth for which bench IDs exist and their configuration (e.g., preferNatural flag). Builders and Furniture retain their known preferences; all other benches default to `preferNatural=false`.

## Acceptance Criteria

### Checklist
- [ ] A new `BenchRegistry` class exists that is populated at runtime from recipe assets
- [ ] All unique `BenchRequirement.id` values found in recipes are registered (minus denied ones)
- [ ] Each registered bench has a `preferNatural` flag (Furniture_Bench=true, all others=false)
- [ ] `RecipeFilterRegistry` uses `BenchRegistry` instead of `BenchCategory.allBenchIds()`
- [ ] `BenchBlockClassifier` works with dynamic bench data instead of the `BenchCategory` enum
- [ ] `BenchCategory` enum is removed or reduced to a legacy compatibility shim
- [ ] Existing tests continue to pass

### Scenarios
**Discovering a new bench**
- **Given** a CraftingRecipe asset declares `BenchRequirement.id = "Workbench"`
- **When** `BenchRegistry` scans recipe assets during init
- **Then** "Workbench" is registered with `preferNatural=false` and its recipes are indexed

**Preserving Furniture preference**
- **Given** `BenchRegistry` discovers bench ID "Furniture_Bench"
- **When** the registry applies known overrides
- **Then** "Furniture_Bench" has `preferNatural=true`

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605291030 | Create BenchRegistry with Runtime Discovery | backlog |
| S2605291035 | Update RecipeFilterRegistry for Open Bench Discovery | backlog |
| S2605291040 | Update BenchBlockClassifier for Dynamic Categories | backlog |

## Notes
- The `preferNatural` override for Furniture_Bench should be configurable, but the default hardcoded override is acceptable for the initial implementation.
- `BenchCategory.fromRecipe()` logic needs to be replaced with a dynamic lookup in the registry.
