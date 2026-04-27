---
id: S2604261635
type: story
title: "Unified Block_Placeholder JSON with 11 States"
status: backlog
priority: critical
feature: F2604261630
epic: E2604221030
created: 2026-04-26
---

# Unified Block_Placeholder JSON with 11 States

## User Story
As a **developer**, I want a single Block_Placeholder.json defining all 11 states so that the 12 separate JSON files are eliminated and the state system manages variant identity.

## Acceptance Criteria

### Checklist
- [ ] Single `Block_Placeholder.json` created with base (Blue) + 9 Green slot states + 1 Red state
- [ ] Base state: Quality "Tool", blue tint, no PlaceBlock interaction, Use: "PlaceBlock_Menu"
- [ ] Each Green state (`Armed_Green_0`–`Armed_Green_8`): Quality "Uncommon", green tint, `RemoveItemInHand: false`, `BlockPreviewVisibility: "Default"`
- [ ] Red state (`Armed_Red`): Quality "Developer", red tint, `RemoveItemInHand: false`, `BlockPreviewVisibility: "Default"`
- [ ] All 12 old JSON files deleted (`Block_Placeholder_Blue.json`, `Block_Placeholder_Green.json`, `Block_Placeholder_Green_0.json`–`_8.json`, `Block_Placeholder_Red.json`)
- [ ] Server starts successfully with the new unified JSON

### Scenarios

**Server loads unified placeholder**
- **Given** `Block_Placeholder.json` is in the assets directory
- **When** the server starts
- **Then** 12 items are registered: `Block_Placeholder` (base) + `*Block_Placeholder_State_Armed_Green_0` through `_8` + `*Block_Placeholder_State_Armed_Red`
- **Then** 12 block types are registered with unique indices

## Notes
- Each Green state must have its own inline BlockType so each gets a unique block type index — required for per-slot independent `UpdateBlockTypes` reskinning.
- Quality must be explicit on each state (uses `.append()` not `.appendInherited()` — null default, not inherited).
- The `Interactions` block on each armed state must include both `Use: "PlaceBlock_Menu"` (left-click opens menu) and `Secondary.Interactions[0].Type: "PlaceBlock"` with `RemoveItemInHand: false` (right-click triggers placement without consuming).
