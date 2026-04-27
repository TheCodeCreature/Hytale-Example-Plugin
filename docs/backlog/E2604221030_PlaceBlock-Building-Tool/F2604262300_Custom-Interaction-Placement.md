---
id: F2604262300
type: feature
title: "Custom Interaction Placement"
status: backlog
priority: critical
epic: E2604221030
created: 2026-04-26
---

# Custom Interaction Placement

## Description

Replace the `PlaceBlock` interaction on armed placeholder states with a custom
`SimpleBlockInteraction` subclass registered via the plugin's codec registry.
This eliminates the engine's item-consumption path entirely — the custom
interaction handles target resolution, material consumption, and `setBlock()`
directly, with no cancellation or slot-restoration workarounds.

The item's `BlockType` is retained on each state so the client still renders the
native ghost block preview. `BlockPreviewReskinManager` continues to reskin the
variant block types via `UpdateBlockTypes` packets — this is interaction-type
agnostic.

## Acceptance Criteria

### Checklist
- [ ] A custom interaction type is registered and resolves from JSON
- [ ] Armed Green states use the custom interaction for Secondary
- [ ] Right-click with an armed placeholder places the target block in the world
- [ ] Recipe materials are consumed from inventory on placement
- [ ] The placeholder item is NOT consumed or removed from the hotbar
- [ ] Ghost block preview renders the reskinned target block (unchanged)
- [ ] Affordability transitions (Green ↔ Red) still function
- [ ] `PlaceBlockPlacementSystem` is deleted (no longer needed)
- [ ] Suppress/resume workaround in `PlaceholderSyncSystem` is removed

### Scenarios
**Happy path: place a block**
- **Given** the player holds an armed Green placeholder in slot 3
- **When** the player right-clicks on a valid surface
- **Then** the target block is placed, materials are consumed, and the placeholder stays in hand

**Insufficient resources**
- **Given** the player holds an armed Green placeholder but cannot afford the recipe
- **When** the player right-clicks
- **Then** nothing is placed and a feedback message is shown

**Unarmed placeholder**
- **Given** the player holds an unarmed (Blue) placeholder
- **When** the player right-clicks
- **Then** nothing happens (no placement, no consumption)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604262305 | Register Custom PlaceBlock Interaction Type | backlog |
| S2604262310 | Implement Placement Logic in Custom Interaction | backlog |
| S2604262315 | Update Block_Placeholder JSON to Custom Interaction | backlog |
| S2604262320 | Delete PlaceBlockPlacementSystem and Suppress/Resume | backlog |

## Notes
- Trade-off: no client-side prediction (~1 tick latency). Acceptable for a building tool.
- Trade-off: no drag-to-place. Already our behavior.
- `PlacementCostScaler` mutual exclusion (Contract #12) becomes moot — no shared event.
