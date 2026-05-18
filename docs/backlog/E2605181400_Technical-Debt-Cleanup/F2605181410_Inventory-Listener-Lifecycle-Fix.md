---
id: F2605181410
type: feature
title: "Inventory Listener Lifecycle Fix"
status: backlog
priority: high
epic: E2605181400
created: 2026-05-18
---

# Inventory Listener Lifecycle Fix

## Description
Store `EventRegistration` handles returned by `ItemContainer.registerChangeEvent()` and call `unregister()` on player disconnect. This stops the stencil restoration and affordability-refresh lambdas from firing on every inventory change for the entire server session after they're no longer needed.

## Acceptance Criteria

### Checklist
- [ ] `registeredPlayers` map value type changed from `Boolean` to `EventRegistration[]`
- [ ] All 3 `registerChangeEvent()` return values captured and stored
- [ ] `unregister()` method calls `.unregister()` on each stored handle
- [ ] No functional regression — stencils still restore to qty 2 during session
- [ ] Map is empty after all players disconnect

### Scenarios
**Normal session lifecycle**
- **Given** a player connects and receives stencils
- **When** they place stencil blocks repeatedly
- **Then** stencil quantity is restored to 2 each time (existing behavior preserved)

**Disconnect cleanup**
- **Given** a player was connected with active stencil listeners
- **When** they disconnect
- **Then** further inventory mutations on their (about-to-be-GC'd) containers do not invoke restoreStencils or refreshAffordability

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605181411 | Store EventRegistration Handles | backlog |

## Notes
- Design doc: docs/design-deferred-fixes.md (Item #9)
- Research: docs/hytale/research-particle-highlight-and-container-events.md (Question 2)
- Pattern confirmed: Hytale's own `Inventory.java` uses identical `EventRegistration` store/unregister pattern
- Risk: If `registerChangeEvent()` doesn't return `EventRegistration` in this SDK version, fallback is a guard boolean inside the lambda
