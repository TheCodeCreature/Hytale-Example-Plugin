---
id: S2604261655
type: story
title: "Remove SlotFilter.DENY Workaround"
status: backlog
priority: high
feature: F2604261630
epic: E2604221030
created: 2026-04-26
---

# Remove SlotFilter.DENY Workaround

## User Story
As a **developer**, I want the SlotFilter.DENY workaround removed from PlaceBlockPlacementSystem so that placeholder protection relies solely on `RemoveItemInHand: false` — the engine's intended mechanism.

## Acceptance Criteria

### Checklist
- [ ] `SlotFilter.DENY` try/finally block removed from `PlaceBlockPlacementSystem.handle()`
- [ ] `FilterActionType` and `SlotFilter` imports removed
- [ ] `hotbar` and `activeSlot` local variables removed (only `container` needed for consumption)
- [ ] Resource consumption via `removeMaterials()` continues to work correctly without slot protection
- [ ] Placeholder remains in hand after right-click (verified by `RemoveItemInHand: false` in JSON)

### Scenarios

**Placeholder survives placement**
- **Given** player holds Armed_Green placeholder with sufficient resources
- **When** player right-clicks to place a block
- **Then** recipe materials are consumed from inventory
- **Then** target block is placed in the world
- **Then** placeholder remains in hand (engine did not consume it)

**removeMaterials does not consume placeholder**
- **Given** a recipe requires Wood Planks (which the placeholder is NOT)
- **When** `removeMaterials()` scans the combined container
- **Then** it matches Wood Planks from storage/backpack/non-active hotbar
- **Then** the placeholder in the active slot is never matched (different item type entirely)

## Notes
- The SlotFilter.DENY was a workaround for engine-level consumption. With `RemoveItemInHand: false`, the engine never calls `removeItemInHand()`, so the workaround is dead code.
- The `removeMaterials()` call won't consume the placeholder regardless because the placeholder's item type doesn't match any recipe material. The SlotFilter was defense-in-depth — now unnecessary with the root cause fixed.
- This story depends on S2604261635 (JSON with `RemoveItemInHand: false`) being complete first.
