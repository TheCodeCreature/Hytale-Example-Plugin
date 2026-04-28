---
id: S2604272110
type: story
title: "Remove Base-Block Classification"
status: backlog
priority: critical
feature: F2604272100
epic: E2604201200
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
