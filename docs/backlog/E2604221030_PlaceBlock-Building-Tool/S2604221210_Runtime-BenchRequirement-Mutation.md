---
id: S2604221210
type: story
title: "Runtime BenchRequirement Mutation for Recipe Aggregation"
status: done
priority: high
feature: F2604221035
epic: E2604221030
created: 2026-04-22
---

# Runtime BenchRequirement Mutation for Recipe Aggregation

## User Story
As a **player**, I want the **Stencil Crafting to show recipes from both Builders and Furniture benches** so that **I can select any placeable recipe for my building tool**.

## Acceptance Criteria

### Checklist
- [ ] At asset load time, all recipes with `BenchRequirement` matching "Builders" get an additional entry for "Blueprint"
- [ ] All recipes with `BenchRequirement` matching "Furniture_Bench" get an additional entry for "Blueprint"
- [ ] The mutation uses the `StructuralCrafting` bench type for the new entries
- [ ] Original bench requirements are preserved — recipes still appear at their original benches
- [ ] The Stencil Crafting displays all mutated recipes in its categories
- [ ] Non-block recipes (Rope, Fibre, tools) can optionally be excluded if they're not placeable

### Scenarios
**Builders recipes visible**
- **Given** the Stencil Crafting is open
- **When** the player browses the "Bricks" category
- **Then** Builders Bench brick recipes appear

**Furniture recipes visible**
- **Given** the Stencil Crafting is open
- **When** the player browses furniture categories
- **Then** Furniture Bench recipes (chairs, tables) appear

**Original benches unchanged**
- **Given** a Builders Bench and Stencil Crafting both exist
- **When** the player opens each
- **Then** the Builders Bench shows only its original recipes; the Stencil Crafting shows both

## Notes
- The codebase already uses reflection-based asset mutation (`AssetFieldAccessor`) for recipe cost scaling. The same pattern can add `BenchRequirement` entries.
- KEY CONSIDERATION: Furniture recipes are `BenchType: "Crafting"`, but the Stencil Crafting is `"StructuralCrafting"`. The mutated requirement must match the Stencil Crafting's type.
- This runs during `LoadAssetEvent`, alongside `DropScaler.apply()`.
- Risk: The StructuralCrafting UI may not render multi-ingredient Furniture recipes correctly. Test with a simple furniture recipe first.
