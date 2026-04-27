---
id: F2604261630
type: feature
title: "Placeholder State Consolidation"
status: backlog
priority: critical
epic: E2604221030
created: 2026-04-26
---

# Placeholder State Consolidation

## Description

Refactor the PlaceBlock placeholder from 12 separate JSON files (1 Blue, 1 Green base, 9 Green slot variants, 1 Red) to a single `Block_Placeholder.json` with 11 states using Hytale's Item State system. Armed states use `RemoveItemInHand: false` to prevent engine-level item consumption, eliminating the `SlotFilter.DENY` workaround.

## Acceptance Criteria

### Checklist
- [ ] Single `Block_Placeholder.json` file replaces all 12 separate JSON files
- [ ] 11 states defined: 1 base (Blue), 9 Green slot variants (`Armed_Green_0`–`Armed_Green_8`), 1 Red (`Armed_Red`)
- [ ] `RemoveItemInHand: false` on all armed states prevents engine from consuming the placeholder
- [ ] Each Green state has a unique BlockType (unique block type index for independent per-slot previews)
- [ ] `PlaceBlockMetadata` API uses `withState()` for all state transitions
- [ ] `BlockPreviewReskinManager` uses state-derived block type IDs for reskinning
- [ ] `PlaceholderSyncSystem` handles affordability transitions (Green ↔ Red)
- [ ] `PlaceBlockPlacementSystem` no longer needs `SlotFilter.DENY` workaround
- [ ] All arm/disarm callers updated (AssignSubCommand, ClearSubCommand, BlueprintSelectionPage)
- [ ] `PlaceBlockIndicatorListener` stub deleted (affordability handled by PlaceholderSyncSystem)

### Scenarios

**Player arms placeholder at slot 3 with Cobble Wall recipe**
- **Given** player holds unarmed placeholder (Blue) in hotbar slot 3
- **When** player selects Cobble Wall recipe via command or UI
- **Then** placeholder transitions to `*Block_Placeholder_State_Armed_Green_3` with recipe metadata
- **Then** block preview shows Cobble Wall ghost block (via UpdateBlockTypes targeting Armed_Green_3's block type index)

**Player right-clicks with armed placeholder**
- **Given** player holds Armed_Green placeholder with sufficient resources
- **When** player right-clicks
- **Then** engine fires PlaceBlockEvent but does NOT consume the placeholder (RemoveItemInHand: false)
- **Then** PlaceBlockPlacementSystem cancels event, consumes materials atomically, places target block

**Player runs out of resources**
- **Given** player holds Armed_Green_3 placeholder with Cobble Wall recipe
- **When** inventory changes and player can no longer afford the recipe
- **Then** placeholder transitions to `*Block_Placeholder_State_Armed_Red` (preserving recipe metadata)
- **Then** rarity indicator changes from Green to Red

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604261635 | Unified Block_Placeholder JSON with 11 States | backlog |
| S2604261640 | State-Based Arming and Disarming API | backlog |
| S2604261645 | State-Based Block Preview Reskinning | backlog |
| S2604261650 | Affordability State Transitions (Green ↔ Red) | backlog |
| S2604261655 | Remove SlotFilter.DENY Workaround | backlog |
| S2604261660 | Delete PlaceBlockIndicatorListener Stub | backlog |

## Notes

- The 9 Green slot states exist because `UpdateBlockTypes` reskins by block type INDEX globally — two hotbar slots with different recipes need different block type IDs for independent previews.
- Blue and Red are single states because they don't need independent per-slot previews — Blue is always the same debug cube, Red is always the same red cube.
- This feature supersedes the per-recipe state generation approach (Expert assessment: Icon can't be overridden by states, 200-400 states would bloat the asset map).
- Risk: `withState()` behavior needs verification — does it preserve BSON metadata across state transitions? Bucket pattern suggests yes.
