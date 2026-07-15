---
id: F2605291505
type: feature
title: "Particle Loop Task Queue Guard"
status: done
priority: critical
epic: E2605291500
created: 2026-05-29
---

# Particle Loop Task Queue Guard

## Description
Add a coalescing guard to StencilBookParticleLoop to prevent queuing new `world.execute()` lambdas when the previous one hasn't been processed yet. This uses the same `AtomicBoolean` pattern already proven in `AffordabilityCoalescer`.

## Acceptance Criteria

### Checklist
- [ ] StencilBookParticleLoop uses an AtomicBoolean guard so at most one world.execute() lambda is pending at any time
- [ ] The scheduled executor callback is a no-op when a previous lambda is still pending
- [ ] Entity spawn/remove only happens when the world thread actually processes the lambda
- [ ] Existing behavior (highlight entity at aimed block) is preserved
- [ ] Effect duration is adjusted to account for variable tick timing

### Scenarios
**Normal operation — player holds StencilBook**
- **Given** a player holds a StencilBook and aims at a block with a recipe
- **When** the scheduled executor fires every 100ms
- **Then** at most one world.execute() lambda is pending at any time; the highlight entity updates when the world thread processes it

**World thread overloaded**
- **Given** the world thread is processing a long tick
- **When** the scheduled executor fires multiple times during that tick
- **Then** only one lambda is queued; excess firings are no-ops

**Player disconnect during pending lambda**
- **Given** a pending lambda exists when the player disconnects
- **When** shutdown() is called
- **Then** the scheduled task is cancelled and the highlight entity is cleaned up

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605291520 | Add AtomicBoolean guard to particle loop | in-progress |
| S2605291525 | Fix shutdown race condition and entity leak | in-progress |

## Notes
Risk: Variable tick timing means the highlight entity may update at irregular intervals instead of every 100ms. This is acceptable — visual smoothness is less important than server stability.
