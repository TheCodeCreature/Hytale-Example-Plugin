---
id: S2604201440
type: story
title: "Create PlaceBlock Item JSON Assets"
status: backlog
priority: high
feature: F2604201405
epic: E2604201400
created: 2026-04-20
---

# Create PlaceBlock Item JSON Assets

## User Story
As a **player**, I want **the PlaceBlock tool to exist as three distinct item variants** so that **I can see at a glance whether my tool is idle, ready, or out of resources**.

## Acceptance Criteria

### Checklist
- [ ] PlaceBlock_Default.json exists with Quality "Tool" (blue highlight)
- [ ] PlaceBlock_Armed.json exists with Quality "Uncommon" (green highlight)
- [ ] PlaceBlock_NoResources.json exists with Quality "Developer" (red highlight)
- [ ] All three share the same icon asset
- [ ] All three have MaxStack 1
- [ ] All three have "Use": "PlaceBlock_Menu" interaction
- [ ] All three have blockId referencing "Debug_Block_Empty" to enable PlaceBlockEvent firing
- [ ] Items load without engine errors or warnings

### Scenarios
**Default variant appears blue**
- **Given** a player receives a PlaceBlock_Default
- **When** they view it in their hotbar
- **Then** the slot highlight is blue (Tool quality)

## Notes
- Scaffolded JSON files already exist at `src/main/resources/Server/Item/Items/Tool/PlaceBlock_*.json`
- Need to add `"blockId": "Debug_Block_Empty"` to all three variants
- Verify that "Uncommon" and "Developer" quality IDs resolve correctly in the engine
