---
id: S2605291610
type: story
title: "Defensive StencilSyncSystem Re-registration"
status: in-progress
priority: critical
feature: F2605291600
epic: E2605291500
created: 2026-05-29
---

# Defensive StencilSyncSystem Re-registration

## User Story
As a **player**, I want **stencil sync to work after rejoin** so that **stencil quantities are restored and affordability updates correctly**.

## Acceptance Criteria

### Checklist
- [ ] `StencilSyncSystem.register()` cleans up stale state before registering new handlers when a UUID already exists
- [ ] Old event handles are unregistered before new ones are registered
- [ ] Re-registration logs a warning about replacing stale state

### Scenarios
**Rejoin with stale state**
- **Given** a player's previous `unregister()` call failed (stale entry in `registeredPlayers`)
- **When** the player rejoins and `register()` is called
- **Then** the old handles are unregistered, new handles are registered, and stencil sync works

## Notes
Scope: `StencilSyncSystem.register()` — replace early-return guard with defensive overwrite.
