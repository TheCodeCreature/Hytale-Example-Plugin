---
id: F2604272100
type: feature
title: "Simplified Economy Pipeline"
status: cancelled
priority: critical
epic: E2604201200
created: 2026-04-27
cancelled: 2026-05-19
cancellation-reason: "Superseded by F2605191000 (Leaf-Only Recipe Scaling). Uniform ×12 scaling causes exponential cost explosion on multi-tier recipes. Leaf-only scaling fixes the root cause."
---

# Simplified Economy Pipeline

## Description
Eliminate the base-block concept from the DropScaler pipeline, fix the broken NaturalResourceRegistry init (orphaned sets), and simplify the pipeline from 6 phases to 5. ALL bench recipes are scaled 12x uniformly — including FullBlock transitions (Trunk→Planks, Planks→Decorative→Ornate). Natural blocks (including Deco) drop 12x. Drop lists are scaled completely (all items, not just ingredients).

## Acceptance Criteria

### Checklist
- [ ] ALL bench recipes have their input quantities scaled 12x (no base-block exception)
- [ ] Natural blocks drop 12x their vanilla quantity (including Deco natural blocks)
- [ ] Drop lists on natural blocks have ALL item quantities scaled 12x (not just ingredient items)
- [ ] FullBlock 1:1 transitions scale 12:12 (12 logs → 12 planks, 12 planks → 12 decorative)
- [ ] Crafted blocks drop their recipe ingredients when broken
- [ ] Stack sizes for natural items are scaled 12x
- [ ] NaturalResourceRegistry.init() correctly populates naturalItemIds (not orphaned)
- [ ] Base-block classification removed from BenchBlockClassifier, BenchRecipeRegistry, and BenchRecipeRegistries
- [ ] No compilation errors after all changes

### Scenarios
**FullBlock Transition Chain**
- **Given** Oak tree drops 12 Wood_Oak_Trunk
- **When** Player crafts Wood_Hardwood_Planks (12 Trunk → 12 Planks after scaling)
- **Then** Player receives 12 Planks — same purchasing power per tree

**Decorative Cycling**
- **Given** Player has 12 Wood_Hardwood_Planks
- **When** Player converts Planks → Decorative at Builders bench (12:12 after scaling)
- **Then** Player receives 12 Decorative — no resource loss from cycling

**Deco_Rope Economy**
- **Given** Recipe: 1x Ingredient_Fibre → 1x Deco_Rope (scaled to 12:12)
- **When** Player breaks Deco_Rope
- **Then** Player receives 12x Ingredient_Fibre back

**Leaf Drop List Scaling**
- **Given** A leaf block has a drop list with Plant_Fiber and Berry
- **When** Player breaks the leaf block
- **Then** ALL drops in the list are scaled 12x (both Plant_Fiber and Berry)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604272105 | Fix NaturalResourceRegistry Init and Simplify | backlog |
| S2604272110 | Remove Base-Block Classification | backlog |
| S2604272115 | Simplify DropScaler Pipeline Phases | backlog |
| S2604272120 | Update Tests for Uniform Scaling | backlog |

## Notes
- Design doc: `docs/Plans/design-simplified-economy-pipeline.md`
- Migration path sequenced in design doc §12 for compilability at each step
- Eliminates ~40% of classification complexity
- Product vision Contract #6 will be removed/updated after implementation
