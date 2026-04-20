---
id: F2604201410
type: feature
title: "PlaceBlock Placement System"
status: backlog
priority: high
epic: E2604201400
created: 2026-04-20
---

# PlaceBlock Placement System

## Description
An ECS PlaceBlockEvent handler that intercepts right-click placement when the player holds a PlaceBlock item. Cancels the default Debug_Block_Empty placement, resolves the armed recipe, checks inventory resources, manually places the correct output block via WorldChunk.placeBlock(), and consumes recipe inputs.

## Acceptance Criteria

### Checklist
- [ ] PlaceBlockEvent is cancelled when holding any PlaceBlock variant
- [ ] No Debug_Block_Empty block is ever placed in the world
- [ ] The recipe's output block is placed at the correct position with correct rotation
- [ ] Recipe input materials are consumed from player inventory (scaled per economy contracts)
- [ ] The PlaceBlock tool is NOT consumed (stays in hotbar after placement)
- [ ] Placement fails gracefully when recipe is not assigned (blue state)
- [ ] Placement fails gracefully when resources are insufficient (red state)

### Scenarios
**Successful placement with resources**
- **Given** a player holds PlaceBlock_Armed with recipe "Oak_Planks" and has 48 Wood_Oak_Plank
- **When** the player right-clicks on valid ground
- **Then** an Oak Planks block is placed, 48 Wood_Oak_Plank is consumed, PlaceBlock remains in hand

**Placement blocked by insufficient resources**
- **Given** a player holds PlaceBlock_NoResources with recipe "Oak_Planks" and has 0 Wood_Oak_Plank
- **When** the player right-clicks
- **Then** no block is placed, no resources consumed, PlaceBlock remains in hand

**No recipe assigned**
- **Given** a player holds PlaceBlock_Default (no recipe)
- **When** the player right-clicks
- **Then** no block is placed, no state change

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604201455 | Implement PlaceBlockPlacementSystem | backlog |
| S2604201460 | Verify Placement Rotation Handling | backlog |

## Notes
- Depends on F2604201405 (metadata reading) and F2604201425 (quality swap after placement)
- WorldChunk.placeBlock() API: `placeBlock(x, y, z, blockTypeKey, yaw, pitch, roll, settings)`
- PlaceBlock items need `"blockId": "Debug_Block_Empty"` in their JSON for the engine to fire PlaceBlockEvent
