---
id: E2605061200
type: epic
title: "Ingredient Filter Grid Refactor"
status: in-progress
priority: high
created: 2026-05-06
---

# Ingredient Filter Grid Refactor

## Goal
Replace the hardcoded ResourceTypeRegistry (73 static entries) with a dynamically-derived ingredient filter grid. The grid is populated by scanning all recipe inputs in the bench and resolving them through the ResourceTypeResolver infrastructure into a three-tier collapsible tree of filter entries. This gives players an intuitive way to filter recipes by ingredient at any granularity — from "all wood" down to a specific plank type.

## Success Criteria
- [ ] Grid entries are derived from actual recipe inputs, not a static registry
- [ ] Three-tier hierarchy displayed: All_* groups → ResourceType → ExactID
- [ ] Collapsible tree sections with wrap-grid children
- [ ] Three-state checkbox (all/some/none) on each group with cascade-down selection
- [ ] Selecting a group selects all children; deselecting individual children triggers partial state
- [ ] Existing affordability mode integration preserved
- [ ] Reusable UI components (group header, filter checkbox, icon button) extractable for future grids

## Features
| ID | Title | Status |
|----|-------|--------|
| F2605061205 | Dynamic Ingredient Tree Data Model | backlog |
| F2605061210 | Collapsible Tree Grid UI | backlog |
| F2605061215 | Three-State Checkbox Selection | backlog |
| F2605061220 | Grid Integration with Filter Pipeline | backlog |

## Context
The current ResourceTypeRegistry is a manually-maintained list of 73 resource type entries with 7 meta-filter groups. This has several problems:
- Doesn't cover all ingredients (only ResourceTypeId-based, misses ItemId-based inputs)
- Requires manual updates when new resource types are added
- No hierarchy — flat grid of icons is hard to navigate
- Meta-filter groups (Any_*) were bolted on after the fact

The ResourceTypeResolver already has the infrastructure to resolve both ResourceTypeId and ItemId inputs to concrete items, scan item ResourceTypes, and handle set-root priority. This refactor leverages that infrastructure to dynamically build the grid.

### Three-tier category model:
1. **All_* (Meta-group)**: Aggregates multiple ResourceTypes — e.g., "All Wood" covers Wood_Hardwood, Wood_Softwood, etc.
2. **ResourceType**: A specific engine ResourceTypeId — e.g., "Wood_Hardwood" matches all items declaring that type (planks, trunks, etc.)
3. **ExactID**: A specific item ID — e.g., "Wood_Hardwood_Planks"

## Roadmap
```
Phase 1: Design (current) — full system design + HTML UI mocks
Phase 2: Data model — IngredientTree builder from recipe inputs
Phase 3: UI — collapsible tree grid with three-state checkboxes
Phase 4: Integration — wire into filter pipeline, replace ResourceTypeRegistry usage
```
