---
id: S2604272110
type: story
title: "Remove Base-Block Classification"
status: cancelled
priority: critical
feature: F2604272100
epic: E2604201200
cancelled: 2026-05-19
cancellation-reason: "Parent feature F2604272100 cancelled. Leaf-only scaling (F2605191000) introduces per-input classification instead of removing base-block classification."
created: 2026-04-27
---

# Remove Base-Block Classification

## User Story
As a **developer**, I want **base-block classification removed from all registries** so that **ALL bench recipes are scaled uniformly and classification bugs are eliminated**.

## Acceptance Criteria

### Checklist
- [ ] `BenchRecipeRegistry`: remove `baseBlockRecipeIds`, `isBaseBlockRecipe()`, `isBaseBlockType()`, `allInputsNatural()`
- [ ] `BenchRecipeRegistries`: remove any aggregate base-block query methods
- [ ] `BenchBlockClassifier`: remove `baseBlockTypes`, `isBaseBlock()`, `getNonBaseBlocksByCategory()`, `allInputsExclusivelyNatural()`
- [ ] `ResourceTypeResolver`: remove `isResourceTypeExclusivelyNatural()`
- [ ] No compilation errors after all removals

### Scenarios
**Uniform Recipe Classification**
- **Given** A recipe at Builders bench with all-natural inputs (e.g., 1 Trunk → 1 Plank)
- **When** The pipeline processes recipes
- **Then** The recipe is treated the same as any other recipe — no "base" exception

## Notes
- Migration steps 2–5 in design doc §12
- Must remove consumers (DropScaler) in S2604272115 simultaneously or after
- Compile check after each sub-step
