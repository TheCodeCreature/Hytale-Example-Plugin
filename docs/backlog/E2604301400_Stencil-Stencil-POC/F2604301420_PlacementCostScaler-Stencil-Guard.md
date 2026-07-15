---
id: F2604301420
type: feature
title: "PlacementCostScaler Stencil Guard"
status: backlog
priority: high
epic: E2604301400
created: 2026-04-30
---

# PlacementCostScaler Stencil Guard

## Description
The existing `PlacementCostScaler` must skip stencil-tagged items to prevent double-charging. Since ECS event handler ordering is not guaranteed, the guard must be metadata-based (deterministic) rather than relying on event cancellation state.

## Acceptance Criteria

### Checklist
- [ ] `PlacementCostScaler` checks for stencil BSON metadata before processing
- [ ] If the held item has the stencil tag, `PlacementCostScaler` returns immediately without consuming any resources
- [ ] Non-tagged items continue to be processed by `PlacementCostScaler` as before (no behavioral change)

### Scenarios
**Stencil stencil — PlacementCostScaler skips**
- **Given** the player holds a `Rock_Stone_Cobble` stencil (BSON: `{stencil: true}`)
- **When** `PlaceBlockEvent` fires and `PlacementCostScaler.handle()` is invoked
- **Then** `PlacementCostScaler` detects the stencil tag and returns immediately — no extra items consumed

**Normal natural block — PlacementCostScaler fires normally**
- **Given** the player holds a regular `Rock_Stone_Cobble` (no BSON metadata)
- **When** `PlaceBlockEvent` fires
- **Then** `PlacementCostScaler` consumes 11 extra items as before

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604301421 | Add Stencil BSON Guard to PlacementCostScaler | backlog |

## Notes
- This is a one-line guard addition to the existing early-exit checks in `PlacementCostScaler.handle()`.
- Do NOT rely on `event.isCancelled()` — event handler execution order is not guaranteed.
