---
id: S2604271730
type: story
title: "Migrate BenchRecipeRegistry to Shared Registry"
status: backlog
priority: high
feature: F2604271700
epic: E2604201200
created: 2026-04-27
---

# Migrate BenchRecipeRegistry to Shared Registry

## User Story
As a **developer**, I want **BenchRecipeRegistry to consume the shared RecipeFilterRegistry** so that **the DropScaler pipeline uses the same recipe data as the UI**.

## Acceptance Criteria

### Checklist
- [ ] `BenchRecipeRegistry.init()` recipe scanning loop is replaced by a query against the shared registry
- [ ] Base-block classification (`allInputsExclusivelyNatural`) remains as a BenchRecipeRegistry concern layered on top
- [ ] `BenchCategory` enum reads bench IDs from the shared list
- [ ] `DropScaler.apply()` initialization uses the shared bench ID list
- [ ] No change to drop scaling behavior

### Scenarios
**DropScaler produces identical results**
- **Given** the DropScaler pipeline runs on server startup
- **When** it uses the shared registry instead of its own scan
- **Then** the same recipes are classified and scaled as before

## Notes
- BenchRecipeRegistry has additional classification logic (base-block detection, natural resource checks) that stays local. Only the recipe scanning moves to the shared registry.
- Downstream consumers: `DropScaler`, `BenchBlockClassifier`, `NaturalResourceRegistry`.
