---
id: S2605191020
type: story
title: "Add Duplicate Recipe Guard to scaleCraftingCosts"
status: done
priority: critical
feature: F2605191000
epic: E2604201200
created: 2026-05-19
---

# Add Duplicate Recipe Guard to scaleCraftingCosts

## User Story
As a **developer**, I want **recipes that appear in multiple bench registries to only be scaled once** so that **dual-bench recipes don't get double-scaled (e.g., 144× instead of 12×)**.

## Acceptance Criteria

### Checklist
- [ ] `scaleCraftingCosts()` tracks processed recipe IDs in a `Set<String>`
- [ ] If a recipe ID was already processed, skip it
- [ ] Log the skip count: "N duplicate recipes skipped"
- [ ] Recipes in `BUILDERS_AND_FURNITURE` category are only scaled once
- [ ] No compilation errors

### Scenarios
**Dual-Bench Recipe**
- **Given** Recipe "Fence_Oak" exists in both Builders and Furniture_Bench registries
- **When** Phase 1 runs
- **Then** The recipe inputs are scaled exactly once (×12 for raw inputs)

## Notes
- Pre-existing bug — not introduced by leaf-only scaling, but now more visible
- Simple fix: add `Set<String> processedRecipeIds` to the loop
