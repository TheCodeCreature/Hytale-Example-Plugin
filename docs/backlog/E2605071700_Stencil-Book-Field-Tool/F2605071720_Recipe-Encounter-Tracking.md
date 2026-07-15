---
id: F2605071720
type: feature
title: "Recipe Encounter Tracking"
status: backlog
priority: high
epic: E2605071700
created: 2026-05-07
---

# Recipe Encounter Tracking

## Description
Per-player tracking of which crafting recipes have been "encountered" (discovered at a bench or successfully resolved). Gates access to the Stencil Book's Quick Select and Block Pick features.

## Acceptance Criteria

### Checklist
- [ ] Opening any bench UI marks all that bench's recipes as encountered
- [ ] Successfully picking a block marks that recipe as encountered
- [ ] Encounter set persists across server restarts
- [ ] Encounter set is per-player (not per-item)
- [ ] Cache eviction on player disconnect to prevent memory leaks

### Scenarios
**First bench visit populates encounters**
- **Given** player has never used a bench
- **When** player opens the Builders bench
- **Then** all Builders bench recipes are marked as encountered

**Persistence across reconnect**
- **Given** player has 15 encountered recipes, disconnects
- **When** player reconnects
- **Then** all 15 recipes are still accessible via the Stencil Book

## Notes
- Stored as BSON files in `encounters/<uuid>.json` under plugin data directory
- In-memory cache with lazy loading
- Thread-safe via ConcurrentHashMap
