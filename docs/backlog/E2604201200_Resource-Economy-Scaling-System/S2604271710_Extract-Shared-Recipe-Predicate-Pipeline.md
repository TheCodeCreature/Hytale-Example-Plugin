---
id: S2604271710
type: story
title: "Extract Shared Recipe Predicate Pipeline"
status: backlog
priority: high
feature: F2604271700
epic: E2604201200
created: 2026-04-27
---

# Extract Shared Recipe Predicate Pipeline

## User Story
As a **developer**, I want **a single place that defines which recipes are valid block-producing bench recipes** so that **filter logic bugs are fixed once and apply everywhere**.

## Acceptance Criteria

### Checklist
- [ ] A new `RecipeFilterRegistry` class exists in a shared package
- [ ] It accepts a list of allowed bench IDs and produces a filtered collection of recipe entries
- [ ] Each entry includes: recipeId, outputItemId, blockTypeId, benchId(s), itemSet, resolved ingredient item IDs
- [ ] Recipe scanning applies these predicates in order: non-null, skip-prefix exclusions, valid output with blockId, valid inputs (itemId or resourceTypeId), bench match (all BenchRequirement entries)
- [ ] Skip-prefix exclusions are configurable (e.g., "Stencil_", "Salvage")
- [ ] ResourceType resolution delegates to `ResourceTypeResolver.resolveInputItemId()`
- [ ] BenchRequirement matching scans all entries, not just the first

### Scenarios
**ResourceTypeId-only input is valid**
- **Given** a recipe with input `{ResourceTypeId: "Wood_Hardwood", Quantity: 1}`
- **When** the registry processes it
- **Then** the recipe is included and the ingredient resolves to a representative item like "Wood_Hardwood_Planks"

## Notes
- The registry is a data structure, not a UI or scaling concern. Consumers layer their own filtering on top.
