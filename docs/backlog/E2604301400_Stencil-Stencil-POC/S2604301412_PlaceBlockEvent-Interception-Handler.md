---
id: S2604301412
type: story
title: "PlaceBlockEvent Interception Handler"
status: backlog
priority: high
feature: F2604301410
epic: E2604301400
created: 2026-04-30
---

# PlaceBlockEvent Interception Handler

## User Story
As a **player**, I want to right-click with a stencil stencil block and have it place the block using my resources so that I can build without consuming the stencil item.

## Acceptance Criteria

### Checklist
- [ ] An `EntityEventSystem<EntityStore, PlaceBlockEvent>` handler is registered during plugin setup
- [ ] The handler reads the held item via `event.getItemInHand()` and checks for stencil BSON metadata
- [ ] If the item is a stencil: cancel the event, resolve the recipe, check affordability, place the block, consume resources
- [ ] If the item is NOT a stencil: return immediately (vanilla flow proceeds)
- [ ] Block placement uses `WorldChunk.placeBlock()` with `event.getTargetBlock()` position and `event.getRotation()` rotation
- [ ] Placement is synchronous within the event handler (no `world.execute()` deferral) to avoid flicker
- [ ] If affordability check fails, send an action bar message and do not place the block

### Scenarios
**Stencil placement succeeds**
- **Given** the player holds a stencil for `Wall_Cobble` with recipe requiring 48× `Rock_Stone_Cobble`
- **When** the player right-clicks on a valid surface and has sufficient resources
- **Then** the event is cancelled, `Wall_Cobble` is placed at the target position with correct rotation, resources are consumed, stencil stays at stack count 1

**Stencil placement fails — insufficient resources**
- **Given** the player holds a stencil for `Wall_Cobble` and lacks sufficient `Rock_Stone_Cobble`
- **When** the player right-clicks
- **Then** the event is cancelled, no block is placed, no resources are consumed, action bar shows "Not enough resources!"

**Non-stencil placement — passthrough**
- **Given** the player holds a normal `Wall_Cobble` stack (no BSON metadata)
- **When** the player right-clicks
- **Then** the handler does nothing — vanilla `PlaceBlockEvent` processing continues normally

## Notes
- The handler accesses the player entity from the event's entity context to read inventory for affordability checks.
- Recipe resolution: use the `recipeId` from BSON metadata to look up `MaterialQuantity[]` costs.
- The handler must NOT fire for manually placed blocks (`WorldChunk.setBlock()`) — confirmed: only client packets trigger `PlaceBlockEvent`.
