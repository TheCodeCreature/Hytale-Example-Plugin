---
id: E2605291500
type: epic
title: "World Thread Queue Saturation Fix"
status: in-progress
priority: critical
created: 2026-05-29
---

# World Thread Queue Saturation Fix

## Goal
Eliminate progressive server slowdown caused by unbounded world thread task queue flooding from the plugin's scheduled executors and deferred work patterns. The server should maintain consistent tick performance indefinitely regardless of play duration.

## Success Criteria
- [ ] Server tick time remains stable over extended play sessions (1+ hours)
- [ ] Block break and stencil pick responsiveness is consistent
- [ ] No entity leaks on player disconnect
- [ ] BlueprintBookParticleLoop does not queue work faster than it can be processed

## Features
| ID | Title | Status |
|----|-------|--------|
| F2605291505 | Particle Loop Task Queue Guard | done |
| F2605291510 | Entity Churn Reduction | backlog |
| F2605291515 | Disconnect Entity Leak Fix | backlog |

## Context
Investigation revealed that `World.execute()` adds to an unbounded `LinkedBlockingDeque` with zero backpressure. The `consumeTaskQueue()` method drains the full queue each tick. When `BlueprintBookParticleLoop.scheduleAtFixedRate(100ms)` continuously queues lambdas via `world.execute()`, and those lambdas include expensive operations (raycasts, entity spawn/remove), the world thread can fall behind — creating a positive feedback loop where each tick processes more queued tasks, takes longer, and allows even more tasks to accumulate.

This compounds with AffordabilityCoalescer's deferred refreshes (also via `world.execute()`), resulting in progressive world thread starvation that manifests as increasing unresponsiveness to player actions.
