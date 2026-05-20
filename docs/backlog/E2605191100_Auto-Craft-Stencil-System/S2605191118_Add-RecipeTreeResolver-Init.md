---
id: S2605191118
type: story
title: "Add RecipeTreeResolver.init() to DropScaler"
status: backlog
priority: medium
feature: F2605191115
epic: E2605191100
created: 2026-05-19
---

# Add RecipeTreeResolver.init() to DropScaler

## User Story
As a **system**, I want **the recipe tree cache to be initialized after scaling is complete** so that **the raw cost cache reflects the actual scaled recipe quantities**.

## Acceptance Criteria

### Checklist
- [ ] `DropScaler.apply()` calls `RecipeTreeResolver.init()` after Phase 5
- [ ] Init runs after all crafting costs are finalized (leaf-only scaling applied)
- [ ] Cache is fully populated before any player interaction

### Scenarios
**Init ordering**
- **Given** `DropScaler.apply()` runs at server startup
- **When** it reaches end of Phase 5
- **Then** `RecipeTreeResolver.init()` is called and cache is populated

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.7
