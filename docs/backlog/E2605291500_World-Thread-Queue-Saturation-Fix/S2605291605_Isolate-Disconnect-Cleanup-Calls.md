---
id: S2605291605
type: story
title: "Isolate Disconnect Cleanup Calls"
status: in-progress
priority: critical
feature: F2605291600
epic: E2605291500
created: 2026-05-29
---

# Isolate Disconnect Cleanup Calls

## User Story
As a **player**, I want **disconnect cleanup to be resilient** so that **one subsystem failure doesn't break my ability to rejoin**.

## Acceptance Criteria

### Checklist
- [ ] Each cleanup call in `onPlayerDisconnect` is wrapped in its own try-catch
- [ ] If `StencilSyncSystem.unregister()` throws, `StencilVisualManager.removePlayer()` and `BlueprintBookParticleLoop.remove()` still run
- [ ] Exceptions during cleanup are logged with SEVERE level

### Scenarios
**One cleanup fails, others succeed**
- **Given** a player disconnects and `StencilSyncSystem.unregister()` throws an exception
- **When** the disconnect handler continues
- **Then** `StencilVisualManager.removePlayer()` and `BlueprintBookParticleLoop.remove()` still execute

## Notes
Scope: `Plugin.onPlayerDisconnect()` only.
