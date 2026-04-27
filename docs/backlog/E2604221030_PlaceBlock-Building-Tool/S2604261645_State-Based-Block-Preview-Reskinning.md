---
id: S2604261645
type: story
title: "State-Based Block Preview Reskinning"
status: backlog
priority: critical
feature: F2604261630
epic: E2604221030
created: 2026-04-26
---

# State-Based Block Preview Reskinning

## User Story
As a **player**, I want each hotbar slot to independently show its armed recipe's block preview so that I can distinguish between multiple armed placeholders at a glance.

## Acceptance Criteria

### Checklist
- [ ] `BlockPreviewReskinManager` uses state-based variant IDs (`*Block_Placeholder_State_Armed_Green_0` through `_8`)
- [ ] `captureOriginalPackets()` captures the 9 Green state block type packets + 9 Green state item packets
- [ ] `reskinVariant()` sends `UpdateBlockTypes` targeting the state-derived block type index
- [ ] `sendItemUpdate()` sends `UpdateItems` with the target block's icon and model for the state variant item ID
- [ ] Per-slot independence maintained: Armed_Green_0 and Armed_Green_3 can show different block previews simultaneously
- [ ] Restore logic correctly reverts each Green state to its original block type
- [ ] Storage scanning updated: revert any Green state variants in storage back to base item

### Scenarios

**Two slots with different recipes show different previews**
- **Given** slot 0 armed with Cobble Wall, slot 3 armed with Oak Planks
- **When** player looks at their hotbar
- **Then** slot 0 shows Cobble Wall ghost preview, slot 3 shows Oak Planks ghost preview
- **Then** both icons in the hotbar match their respective blocks

**Disarmed slot reverts preview**
- **Given** slot 0 was armed with Cobble Wall (reskinned)
- **When** player clears the recipe from slot 0
- **Then** slot 0 block type is restored to original Armed_Green_0 appearance
- **Then** item icon reverts to default placeholder icon

## Notes
- The key change from current code: variant IDs change from `Block_Placeholder_Green_0` to `*Block_Placeholder_State_Armed_Green_0`. The `*` prefix and `_State_` infix are engine-generated from the parent item ID + state name.
- The reskin manager also needs to handle `Armed_Red` reskinning if we want the red state to show the recipe's block preview (just tinted red). Open question: should the red state show the recipe preview or just a generic red cube?
