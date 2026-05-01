---
id: F2604301410
type: feature
title: "Blueprint Stencil Placement Interception"
status: backlog
priority: high
epic: E2604301400
created: 2026-04-30
---

# Blueprint Stencil Placement Interception

## Description
When a player right-clicks to place a block from a BSON-tagged stencil item, the server intercepts the `PlaceBlockEvent`, cancels native behavior (preventing item consumption and engine-driven placement), checks resource affordability, and if affordable, manually places the block and atomically consumes recipe materials from the player's inventory.

## Acceptance Criteria

### Checklist
- [ ] PlaceBlockEvent handler detects blueprint-tagged items via BSON metadata
- [ ] Event is cancelled for tagged items (stack count stays at 1, no native placement)
- [ ] Block is placed at the correct position with correct rotation via `WorldChunk.placeBlock()`
- [ ] Recipe materials are consumed atomically from inventory (all-or-nothing)
- [ ] When the placed block IS one of its own recipe ingredients, consumption prioritizes non-active-slot stacks first
- [ ] If resources are insufficient, a toast/action bar message is shown and no block is placed
- [ ] Non-tagged block items pass through the handler untouched (vanilla behavior preserved)

### Scenarios
**Place an affordable stencil block**
- **Given** the player holds a `Wall_Cobble` stencil (BSON: `{blueprint: true, recipeId: "cobble_wall"}`) and has 48× `Rock_Stone_Cobble` in inventory
- **When** the player right-clicks on a valid surface
- **Then** a `Wall_Cobble` block is placed at the aimed position, 48× `Rock_Stone_Cobble` is consumed from inventory, and the stencil remains at stack count 1

**Place an unaffordable stencil block**
- **Given** the player holds a `Wall_Cobble` stencil and has only 10× `Rock_Stone_Cobble` in inventory
- **When** the player right-clicks on a valid surface
- **Then** no block is placed, no resources are consumed, and a message "Not enough resources!" appears in the action bar

**Place a normal (non-stencil) block**
- **Given** the player holds a regular `Wall_Cobble` (no BSON metadata) stack of 12
- **When** the player right-clicks on a valid surface
- **Then** vanilla behavior occurs — 1× `Wall_Cobble` is consumed from the stack, block is placed

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604301411 | Blueprint Stencil Metadata Utility | backlog |
| S2604301412 | PlaceBlockEvent Interception Handler | backlog |
| S2604301413 | Atomic Resource Consumption with Slot Priority | backlog |

## Notes
- The handler runs inside `world.execute()` context (engine wraps `PlaceBlockEvent` dispatch in this). Direct `worldChunk.setBlock()` from the handler is safe and avoids flicker.
- `PlaceBlockEvent.getItemInHand()` provides the held ItemStack directly — no need to read from the hotbar.
- `PlaceBlockEvent.getTargetBlock()` and `getRotation()` provide placement position and orientation.
