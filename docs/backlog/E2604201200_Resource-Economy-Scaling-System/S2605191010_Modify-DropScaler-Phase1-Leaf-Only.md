---
id: S2605191010
type: story
title: "Modify DropScaler Phase 1 for Leaf-Only Scaling"
status: not started
priority: critical
feature: F2605191000
epic: E2604201200
created: 2026-05-19
---

# Modify DropScaler Phase 1 for Leaf-Only Scaling

## User Story
As a **player**, I want **multi-tier crafting recipes to cost 12× their vanilla raw material total** so that **building with crafted blocks doesn't require exponentially more resources at each tier**.

## Acceptance Criteria

### Checklist
- [ ] `DropScaler.apply()` calls `RecipeTierClassifier.init()` after `NaturalResourceRegistry.init()` and before Phase 1
- [ ] `scaleCraftingCosts()` accepts a `RecipeTierClassifier` parameter
- [ ] For each recipe input, check `classifier.isRawInput(mq)` before scaling
- [ ] Raw inputs: multiply quantity by `RESOURCE_MULTIPLIER` (12)
- [ ] Crafted inputs: keep vanilla quantity (no scaling)
- [ ] Log counts: "Phase 1: X inputs scaled (raw), Y inputs skipped (crafted), Z recipes processed"
- [ ] All downstream systems produce correct results without modification
- [ ] Place-break loop is lossless for both raw-input and crafted-input recipes

### Scenarios
**Brick Recipe (Raw Input)**
- **Given** Recipe: 1 stone → 1 brick (stone is raw)
- **When** Phase 1 runs
- **Then** Recipe becomes: 12 stone → 1 brick
- **And** Breaking brick returns 12 stone (via Phase 3a drop list)

**Brick Stairs Recipe (Crafted Input)**
- **Given** Recipe: 3 bricks → 1 stairs (brick is crafted)
- **When** Phase 1 runs
- **Then** Recipe stays: 3 bricks → 1 stairs
- **And** Breaking stairs returns 3 bricks (via Phase 3a drop list)
- **And** Total raw cost: 3 × 12 = 36 stone = 12× vanilla

**Mixed Input Recipe**
- **Given** Recipe: 2 wood (raw) + 1 iron_ingot (crafted) → 1 tool
- **When** Phase 1 runs
- **Then** Recipe becomes: 24 wood + 1 iron_ingot → 1 tool

## Notes
- Depends on S2605191005 (RecipeTierClassifier must exist first)
- No changes to Phase 3a, 3b, 4, or 5
- No changes to PlacementCostScaler, StencilPlacementSystem, PlaceBlockCostUtil
- Design doc: `docs/design-linear-scaling-fix.md` §7 (sequence diagram)
