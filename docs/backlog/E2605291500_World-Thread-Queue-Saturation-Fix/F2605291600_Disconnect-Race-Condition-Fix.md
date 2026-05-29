---
id: F2605291600
type: feature
title: "Disconnect Race Condition Fix"
status: done
priority: critical
epic: E2605291500
created: 2026-05-29
---

# Disconnect Race Condition Fix

## Description
Fix disconnect cleanup race conditions that cause indefinite loading screen hang when a player tries to rejoin a LAN server. The engine's `SetupPacketHandler` blocks on `removalFuture.join()` if the old player entity hasn't been cleaned up from `Universe.players` yet. Plugin-side `world.execute()` calls queued during disconnect delay the engine's entity removal on the world thread, prolonging the window where rejoin blocks.

## Acceptance Criteria

### Checklist
- [ ] Player can disconnect and rejoin a LAN server without hanging on the loading screen
- [ ] Each disconnect cleanup call is isolated — one failure doesn't prevent others from running
- [ ] StencilSyncSystem re-registration works correctly on rejoin even if previous cleanup was incomplete
- [ ] BlueprintBookParticleLoop shutdown does not queue unnecessary world thread work during disconnect
- [ ] No entity leaks or stale scheduled tasks after disconnect

### Scenarios
**Normal disconnect and rejoin**
- **Given** a player is connected to a LAN server with the plugin active
- **When** the player leaves the world and immediately rejoins
- **Then** the loading screen completes normally and all plugin features work

**Crash disconnect and rejoin**
- **Given** a player's client crashes while connected
- **When** the player restarts and rejoins
- **Then** stale state from the old session is cleaned up and the player loads in normally

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605291605 | Isolate Disconnect Cleanup Calls | in-progress |
| S2605291610 | Defensive StencilSyncSystem Re-registration | in-progress |
| S2605291615 | Minimize World Thread Work in Shutdown | in-progress |

## Notes
- Root cause: engine's `SetupPacketHandler` calls `removalFuture.join()` which blocks until old entity is fully removed from the world thread
- Plugin's `world.execute()` calls during disconnect add work ahead of the engine's entity removal in the world thread queue
- `onPlayerDisconnect` fires on the Netty thread, not the world thread
