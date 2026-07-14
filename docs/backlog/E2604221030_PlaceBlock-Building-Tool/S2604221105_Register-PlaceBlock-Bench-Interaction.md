---
id: S2604221105
type: story
title: "Create Stencil Crafting Block Asset"
status: backlog
priority: high
feature: F2604221035
epic: E2604221030
created: 2026-04-22
updated: 2026-04-22
---

# Create Stencil Crafting Block Asset

## User Story
As a **player**, I want a **Stencil Crafting block I can place in the world** so that **I can interact with it to browse and select recipes for my PlaceBlock tool**.

## Acceptance Criteria

### Checklist
- [ ] `Bench_Blueprint.json` item asset exists, cloned from `Bench_Builders.json`
- [ ] Stencil Crafting has `Bench.Id: "Blueprint"` (distinct from `"Builders"`)
- [ ] Stencil Crafting has `Bench.Type: "StructuralCrafting"`
- [ ] Stencil Crafting has `BlockEntity.Components.BenchBlock: {}`
- [ ] Stencil Crafting has combined categories from both Builders and Furniture benches
- [ ] Stencil Crafting can be given via admin command, placed in the world, and right-clicked
- [ ] Right-clicking opens a StructuralCrafting window with recipe categories
- [ ] Stencil Crafting has distinct visual/icon from the Builders Bench (reuse or modify model/texture)

### Scenarios
**Bench is placeable**
- **Given** the player has a `Bench_Blueprint` item
- **When** they place it in the world
- **Then** a bench block appears with proper model and hitbox

**Bench opens on interaction**
- **Given** a Stencil Crafting is placed in the world
- **When** the player right-clicks it
- **Then** a StructuralCrafting window opens

## Notes
- Clone the `Bench_Builders.json` asset and change `Bench.Id`, model references, and category list.
- The bench window will initially be empty of recipes until `BenchRequirement` mutation is implemented (S2604221210).
- For Phase 1 testing, just verify the bench exists and opens — recipes come next.
