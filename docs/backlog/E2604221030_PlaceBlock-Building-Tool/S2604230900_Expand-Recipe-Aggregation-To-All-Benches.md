---
id: S2604230900
type: story
title: "Expand Recipe Aggregation to All Benches"
status: backlog
priority: critical
feature: F2604221035
epic: E2604221030
created: 2026-04-23
---

# Expand Recipe Aggregation to All Benches

## User Story
As a **player**, I want **the Blueprint Bench to show placeable recipes from ALL registered benches** so that **I don't have to visit different benches to arm my placeholder with different block types**.

## Acceptance Criteria

### Checklist
- [ ] `BlueprintBookRecipeMutator` scans ALL recipes with any `BenchRequirement`, not just "Builders" and "Furniture_Bench"
- [ ] Only recipes whose primary output has a `blockId` (placeable blocks) are aggregated
- [ ] Non-block output recipes (Rope, Fibre, tools) are excluded from aggregation
- [ ] The Blueprint Bench's category list in `Bench_Blueprint.json` covers any new categories introduced by other benches
- [ ] Recipes from Stonecutter, Loom, or any future bench with placeable outputs appear at the Blueprint Bench
- [ ] Original bench requirements are preserved — recipes still appear at their source benches

### Scenarios
**Stonecutter block recipe appears**
- **Given** a Stonecutter bench has a recipe for "Smooth Stone Slab" (a placeable block)
- **When** the player opens the Blueprint Bench
- **Then** "Smooth Stone Slab" appears in the recipe grid

**Non-block recipe excluded**
- **Given** a Workbench has a recipe for "Rope" (no blockId)
- **When** the player opens the Blueprint Bench
- **Then** "Rope" does NOT appear in the recipe grid

**Source bench unchanged**
- **Given** a Stonecutter recipe was aggregated to Blueprint
- **When** the player opens the Stonecutter
- **Then** the recipe still appears there as before

## Technical Notes
- Change `SOURCE_BENCH_IDS` filter in `BlueprintBookRecipeMutator` to accept ANY bench ID
- Add a `hasPlaceableOutput(recipe)` check: resolve `primaryOutput.getItemId()` → `Item.getBlockId()` must be non-null
- Audit `Bench_Blueprint.json` categories to ensure coverage of categories from other benches
- Contract #16: "Any recipe whose output is a placeable block or furniture item is available at the Blueprint Bench"

## Dependencies
- Requires S2604221210 (Runtime BenchRequirement Mutation) to be complete — this story modifies the same code
