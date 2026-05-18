---
id: S2605181411
type: story
title: "Store EventRegistration Handles"
status: backlog
priority: high
feature: F2605181410
epic: E2605181400
created: 2026-05-18
---

# Store EventRegistration Handles

## User Story
As a **server operator**, I want **inventory change listeners to be properly cleaned up on player disconnect** so that **the server doesn't waste CPU processing stale event handlers**.

## Acceptance Criteria

### Checklist
- [ ] Import `EventRegistration` (from Hytale API)
- [ ] `ConcurrentHashMap<UUID, Boolean>` → `ConcurrentHashMap<UUID, EventRegistration[]>`
- [ ] In `register()`: capture all 3 return values into `new EventRegistration[3]`
- [ ] In `unregister()`: retrieve array, loop and call `.unregister()` on each non-null handle, then remove from map
- [ ] Null-check each handle before calling unregister (defensive against edge cases)
- [ ] Build compiles clean

### Scenarios
**Registration**
- **Given** a player connects
- **When** `StencilSyncSystem.register()` is called
- **Then** 3 EventRegistration handles are stored in the map under the player's UUID

**Deregistration**
- **Given** a player was registered
- **When** `StencilSyncSystem.unregister()` is called with their UUID
- **Then** all 3 handles have `.unregister()` called, and the map entry is removed

## Notes
- File: `src/main/java/com/CodeCreature/stencil/StencilSyncSystem.java`
- ~20 lines changed in a single file
- Pattern reference: Hytale `Inventory.java` uses identical store/unregister approach
