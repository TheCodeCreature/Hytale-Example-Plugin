---
id: E2604201200
type: epic
title: "Resource Economy Scaling System"
status: backlog
priority: high
created: 2026-04-20
---

# Resource Economy Scaling System

## Goal
Provide a unified system for scaling crafting recipes, filtering recipe data, and managing bench-aware resource resolution across the plugin. Both the Stencil Crafting UI and the DropScaler pipeline share overlapping logic for recipe scanning, bench matching, and ResourceType resolution — this epic consolidates that into a single source of truth.

## Success Criteria
- [ ] One shared recipe registry serves both BlueprintSelectionPage and BenchRecipeRegistry
- [ ] Bench ID lists are defined in exactly one location
- [ ] ResourceType resolution uses a single implementation
- [ ] BenchRequirement matching is consistent (scans all entries, not just the first)
- [ ] Bug fixes to filtering logic propagate to all consumers automatically

## Features
| ID | Title | Status |
|----|-------|--------|
| F2604271700 | Unified Recipe Filter Registry | done |
| F2604272100 | Simplified Economy Pipeline | cancelled |
| F2605191000 | Leaf-Only Recipe Scaling | in-progress |

## Context
Currently, `BlueprintSelectionPage.loadRecipes()` and `BenchRecipeRegistry.init()` independently scan all CraftingRecipe assets with overlapping but divergent filter predicates. Bench IDs are defined in 3 places. ResourceType→ItemId resolution exists as two separate implementations (one naive, one bench-aware). BenchRequirement extraction differs (first-only vs all-entries). This creates maintenance burden and allows behavioral drift.
