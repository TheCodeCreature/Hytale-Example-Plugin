---
id: S2604301421
type: story
title: "Add Stencil BSON Guard to PlacementCostScaler"
status: backlog
priority: high
feature: F2604301420
epic: E2604301400
created: 2026-04-30
---

# Add Stencil BSON Guard to PlacementCostScaler

## User Story
As a **player**, I want the natural block placement cost scaler to ignore my stencil stencils so that I am not double-charged when placing a natural block via stencil.

## Acceptance Criteria

### Checklist
- [ ] `PlacementCostScaler.handle()` reads BSON metadata from `event.getItemInHand()`
- [ ] If the metadata contains the stencil tag, the handler returns immediately
- [ ] This check occurs before any resource consumption logic
- [ ] Existing `PlacementCostScaler` behavior for non-tagged natural blocks is unchanged

### Scenarios
**Natural block stencil — no double charge**
- **Given** the player holds a `Rock_Stone` stencil (BSON: `{stencil: true}`) and `Rock_Stone` is registered as a natural block
- **When** both `PlacementCostScaler` and the stencil handler process the event
- **Then** `PlacementCostScaler` skips processing — only the stencil handler consumes resources

## Notes
- Add the guard after the existing `isNaturalBlock()` check or as an early-exit before it — either ordering works since the BSON check is fast and deterministic.
