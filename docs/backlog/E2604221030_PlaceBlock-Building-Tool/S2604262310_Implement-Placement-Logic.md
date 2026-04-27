---
id: S2604262310
type: story
title: "Implement Placement Logic in Custom Interaction"
status: backlog
priority: critical
feature: F2604262300
epic: E2604221030
created: 2026-04-26
---

# Implement Placement Logic in Custom Interaction

## User Story
As a **player**, I want right-clicking with an armed placeholder to place the
target block and consume materials so that I can build without the item
disappearing from my hand.

## Acceptance Criteria

### Checklist
- [ ] `interactWithBlock()` reads recipe ID and target block type from ItemStack metadata
- [ ] Resolves materials via `CraftingManager.getInputMaterials()`
- [ ] Checks affordability via `canRemoveMaterials()`
- [ ] Consumes atomically via `removeMaterials(materials, true, true, true)`
- [ ] Places target block via `WorldChunk.setBlock()` at the provided target position
- [ ] Sends player feedback message on success or failure
- [ ] Item is never consumed — stays in the active slot unchanged
- [ ] Unarmed placeholders produce no action (early return)

### Scenarios
**Successful placement**
- **Given** the player has 12× Oak_Planks and holds an armed Green placeholder targeting Oak_Planks_Block
- **When** the player right-clicks a valid surface
- **Then** Oak_Planks_Block is placed, 12× Oak_Planks removed from inventory, placeholder stays

**Insufficient materials**
- **Given** the player has 5× Oak_Planks (needs 12×)
- **When** the player right-clicks
- **Then** no block placed, no materials consumed, red feedback message shown

## Notes
- Logic is largely moved from `PlaceBlockPlacementSystem.handle()` into `interactWithBlock()`
- No event cancellation needed — this IS the handler
- No slot restoration needed — interaction doesn't touch the slot
