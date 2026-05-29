---
id: S2605291615
type: story
title: "Minimize World Thread Work in Shutdown"
status: in-progress
priority: critical
feature: F2605291600
epic: E2605291500
created: 2026-05-29
---

# Minimize World Thread Work in Shutdown

## User Story
As a **player**, I want **disconnect to be fast** so that **I can rejoin immediately without the loading screen hanging**.

## Acceptance Criteria

### Checklist
- [ ] `BlueprintBookParticleLoop.shutdown()` does not call `world.execute()` to queue entity cleanup
- [ ] The highlight entity is cleaned up by the next `executeTick()` which detects `active=false` and removes it
- [ ] If the scheduled task is cancelled and no more ticks fire, the entity self-expires (non-serialized, effect duration = 500ms)
- [ ] No world thread work is added during the disconnect event handler

### Scenarios
**Fast disconnect-rejoin**
- **Given** a player disconnects from a LAN server
- **When** they immediately rejoin
- **Then** the loading screen completes without delay because no plugin work was queued on the world thread during disconnect

## Notes
The highlight entity is non-serialized and has a 500ms effect duration. Even if it's not explicitly removed, it will vanish within one tick when the store is next processed. The key insight is: we don't need to queue cleanup work — just stop the scheduled task and let the entity expire naturally.
