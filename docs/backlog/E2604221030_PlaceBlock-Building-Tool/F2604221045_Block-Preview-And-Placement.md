---
id: F2604221045
type: feature
title: "Block Preview & Placement"
status: backlog
priority: high
epic: E2604221030
created: 2026-04-22
---

# Block Preview & Placement

## Description
When a player holds an armed `Block_Placeholder`, the engine's built-in block preview system shows a ghost of the selected block at valid placement positions. Right-clicking a valid location places the actual crafted block and atomically consumes scaled recipe inputs from inventory/nearby chests (Contract #11). The placeholder remains armed after placement — the player can keep placing without returning to the bench.

## Acceptance Criteria

### Checklist
- [ ] Armed placeholder integrates with the engine's built-in block preview system
- [ ] Block preview respects the selected asset's placement rules (hitbox, orientation, surface)
- [ ] Right-clicking a valid location places the actual crafted block
- [ ] Resource consumption is atomic — all-or-nothing (Contract #11)
- [ ] Resources are consumed from player inventory first, then nearby chests
- [ ] Placeholder is NOT consumed on placement — remains armed for repeated placement
- [ ] Placement denied if insufficient resources, with no partial consumption
- [ ] PlaceBlock tool and PlacementCostScaler are mutually exclusive on the same event (Contract #12)
- [ ] Player can adjust preview settings (rotation, etc.) as usual without breaking the system
- [ ] Placed block is indistinguishable from a normally-crafted block (Contract #14)

### Scenarios
**Player places a block with sufficient resources**
- **Given** the player holds a placeholder armed with "Cobble Wall" and has 48 cobblestone
- **When** they right-click a valid location
- **Then** 48 cobblestone is consumed, a Cobble Wall block is placed, and the placeholder stays armed

**Player attempts placement with insufficient resources**
- **Given** the player holds a placeholder armed with "Cobble Wall" and has only 20 cobblestone
- **When** they right-click a valid location
- **Then** nothing happens — no block placed, no resources consumed

**Continuous placement**
- **Given** the player has 96 cobblestone and an armed placeholder
- **When** they right-click twice at two valid locations
- **Then** two Cobble Wall blocks are placed, 96 cobblestone consumed total

**Preview respects placement rules**
- **Given** the player holds a placeholder armed with a fence recipe
- **When** they look at an invalid location (e.g., midair with no support)
- **Then** no preview ghost is shown

**PlacementCostScaler does not fire**
- **Given** the player places a block via the PlaceBlock tool
- **When** the PlaceBlockEvent fires
- **Then** PlacementCostScaler does NOT consume additional items (Contract #12)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221125 | Block Preview Integration with Armed Placeholder | backlog |
| S2604221130 | Right-Click Placement Handler | backlog |
| S2604221135 | Atomic Resource Consumption from Inventory & Chests | backlog |
| S2604221140 | PlacementCostScaler Mutual Exclusion | backlog |

## Notes
- The existing `PreviewBlockManager` handles ghost blocks via `ServerSetBlock` packets. It may need to be adapted or complemented by the engine's native preview system.
- The engine's `PlaceBlockEvent` is already used by `PlacementCostScaler`. Contract #12 requires a guard to prevent both systems from firing. Likely solution: check if the placed item is an armed placeholder vs. a natural block.
- Risk: The engine may enforce that the held item's block type matches the placed block. The placeholder-to-actual-block translation may need to intercept the placement pipeline.
