---
id: S2604201455
type: story
title: "Implement PlaceBlockPlacementSystem"
status: backlog
priority: high
feature: F2604201410
epic: E2604201400
created: 2026-04-20
---

# Implement PlaceBlockPlacementSystem

## User Story
As a **player**, I want **to right-click with an armed PlaceBlock to place blocks** so that **I can build continuously without returning to a bench**.

## Acceptance Criteria

### Checklist
- [ ] System intercepts PlaceBlockEvent when held item is any PlaceBlock variant
- [ ] Default event is always cancelled (prevents Debug_Block_Empty placement)
- [ ] Recipe's output block is placed at event.getTargetBlock() with event.getRotation()
- [ ] Recipe input materials are consumed from player inventory
- [ ] PlaceBlock tool remains in hotbar (not consumed)
- [ ] Quality variant is re-evaluated after each placement
- [ ] System is registered as EntityEventSystem in plugin setup()

### Scenarios
**Place a block**
- **Given** PlaceBlock_Armed with recipe "Builders_WoodPlanks_Oak", player has 48 Wood_Oak_Plank
- **When** player right-clicks on valid ground
- **Then** Oak Planks block appears at target, 48 Wood_Oak_Plank removed from inventory

**Block with insufficient resources**
- **Given** PlaceBlock_NoResources with recipe set, player has 0 matching resources
- **When** player right-clicks
- **Then** nothing is placed, no resources consumed

## Notes
- Uses WorldChunk.placeBlock(x, y, z, blockTypeKey, yaw, pitch, roll, settings)
- Get chunk via world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(x, z))
- Skeleton code exists with detailed TODO comments
