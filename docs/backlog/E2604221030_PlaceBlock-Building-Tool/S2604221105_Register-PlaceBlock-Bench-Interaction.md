---
id: S2604221105
type: story
title: "Create Blueprint Bench Block Asset"
status: backlog
priority: high
feature: F2604221035
epic: E2604221030
created: 2026-04-22
updated: 2026-04-22
---

# Create Blueprint Bench Block Asset

## User Story
As a **player**, I want a **Blueprint Bench block I can place in the world** so that **I can interact with it to browse and select recipes for my PlaceBlock tool**.

## Acceptance Criteria

### Checklist
- [ ] `Bench_Blueprint.json` item asset exists, cloned from `Bench_Builders.json`
- [ ] Blueprint Bench has `Bench.Id: "Blueprint"` (distinct from `"Builders"`)
- [ ] Blueprint Bench has `Bench.Type: "StructuralCrafting"`
- [ ] Blueprint Bench has `BlockEntity.Components.BenchBlock: {}`
- [ ] Blueprint Bench has combined categories from both Builders and Furniture benches
- [ ] Blueprint Bench can be given via admin command, placed in the world, and right-clicked
- [ ] Right-clicking opens a StructuralCrafting window with recipe categories
- [ ] Blueprint Bench has distinct visual/icon from the Builders Bench (reuse or modify model/texture)

### Scenarios
**Bench is placeable**
- **Given** the player has a `Bench_Blueprint` item
- **When** they place it in the world
- **Then** a bench block appears with proper model and hitbox

**Bench opens on interaction**
- **Given** a Blueprint Bench is placed in the world
- **When** the player right-clicks it
- **Then** a StructuralCrafting window opens

## Notes
- Clone the `Bench_Builders.json` asset and change `Bench.Id`, model references, and category list.
- The bench window will initially be empty of recipes until `BenchRequirement` mutation is implemented (S2604221210).
- For Phase 1 testing, just verify the bench exists and opens — recipes come next.
