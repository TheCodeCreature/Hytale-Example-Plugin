---
id: S2604221140
type: story
title: "PlacementCostScaler Mutual Exclusion"
status: backlog
priority: high
feature: F2604221045
epic: E2604221030
created: 2026-04-22
---

# PlacementCostScaler Mutual Exclusion

## User Story
As a **player**, I want **only one cost system to fire when I place a block** so that **I'm never double-charged for a single placement**.

## Acceptance Criteria

### Checklist
- [ ] `PlacementCostScaler` does NOT fire when a block is placed via the PlaceBlock tool
- [ ] `PlacementCostScaler` continues to fire normally for natural block placements
- [ ] The PlaceBlock tool does NOT fire when a natural block is placed normally
- [ ] The guard mechanism is reliable — no timing or ordering dependencies
- [ ] Edge case: if a natural block recipe exists at the Builders Bench, the correct system handles it

### Scenarios
**PlaceBlock tool placement**
- **Given** the player places a Cobble Wall via the armed placeholder
- **When** the PlaceBlockEvent fires
- **Then** PlacementCostScaler is skipped, PlaceBlock tool handles cost

**Normal natural block placement**
- **Given** the player places a Rock_Stone from inventory
- **When** the PlaceBlockEvent fires
- **Then** PlacementCostScaler fires normally, PlaceBlock tool is not involved

## Notes
- Contract #12 requires mutual exclusion.
- Likely implementation: check the item type in the player's hand — if it's a `Block_Placeholder`, skip `PlacementCostScaler`.
- The Architect should determine the best guard mechanism (event priority, item-type check, shared flag, etc.).
