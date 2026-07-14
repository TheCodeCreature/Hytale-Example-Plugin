---
id: F2604221035
type: feature
title: "Stencil Crafting Block Asset"
status: backlog
priority: high
epic: E2604221030
created: 2026-04-22
updated: 2026-04-22
---

# Stencil Crafting Block Asset

## Description
Create a new **Stencil Crafting** workbench block, cloned from the existing `Bench_Builders` asset. This is a physical block placed in the world that the player interacts with directly. The Stencil Crafting aggregates placeable recipes from both Builders and Furniture benches, giving the player a unified view of everything they can build. The existing Builders Bench is NOT modified — the Stencil Crafting is a separate, new block (Contract #15).

## Acceptance Criteria

### Checklist
- [ ] Stencil Crafting block asset JSON exists (`Bench_Blueprint.json`), cloned from `Bench_Builders`
- [ ] Stencil Crafting has a unique `Bench.Id` (e.g., `"Blueprint"`)
- [ ] Stencil Crafting uses `Bench.Type: "StructuralCrafting"` (same as Builders)
- [ ] Stencil Crafting has `BlockEntity.Components.BenchBlock: {}` for automatic chest scanning
- [ ] Stencil Crafting can be placed in the world and right-clicked to open a crafting window
- [ ] Recipes from Builders Bench appear at the Stencil Crafting (via runtime `BenchRequirement` mutation)
- [ ] Recipes from Furniture Bench appear at the Stencil Crafting (via runtime `BenchRequirement` mutation)
- [ ] Existing Builders Bench behavior is completely unchanged
- [ ] `Block_Placeholder` item assets exist (Blue/Green/Red variants for quality states)
- [ ] `Block_Placeholder` is categorized as a Tool for right-click interaction handling
- [ ] `Block_Placeholder` defaults to "Tool" quality (blue highlight) when unarmed

### Scenarios
**Player places Stencil Crafting**
- **Given** a player has a `Bench_Blueprint` item
- **When** they place it in the world
- **Then** the bench block appears and can be interacted with

**Stencil Crafting opens crafting window**
- **Given** a Stencil Crafting is placed in the world
- **When** the player right-clicks it
- **Then** a StructuralCrafting window opens showing recipe categories

**Stencil Crafting shows Builders recipes**
- **Given** the Stencil Crafting is open
- **When** the player browses categories
- **Then** recipes that belong to the Builders Bench (WoodPlanks, Bricks, etc.) are visible

**Stencil Crafting shows Furniture recipes**
- **Given** the Stencil Crafting is open
- **When** the player browses categories
- **Then** recipes that belong to the Furniture Bench are also visible

**Existing Builders Bench unchanged**
- **Given** an existing Builders Bench in the world
- **When** a player right-clicks it
- **Then** it behaves exactly as before

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221100 | Create Block_Placeholder Item Assets | backlog |
| S2604221105 | Create Stencil Crafting Block Asset | backlog |
| S2604221210 | Runtime BenchRequirement Mutation for Recipe Aggregation | backlog |

## Notes
- Bench creation is data-driven — a JSON with a unique `Bench.Id` auto-registers with the engine (confirmed by Hytale Expert).
- **KEY BLOCKER (from Hytale Expert):** Builders recipes use `BenchType: "StructuralCrafting"`, Furniture uses `BenchType: "Crafting"`. A bench can only be ONE type. Combining requires runtime mutation of recipe `BenchRequirement` arrays to add entries matching the Stencil Crafting's type+ID.
- `BlockEntity.Components.BenchBlock: {}` is required for automatic chest scanning via `CraftingManager.getContainersAroundBench()`.
- Chest radius is engine-global (4h/2v blocks, 8 chests max) — not configurable per bench.
- The Portable Bench system is NOT involved in this feature.
