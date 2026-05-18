---
id: S2605181406
type: story
title: "Infinite-Duration Effect + 500ms Loop"
status: backlog
priority: high
feature: F2605181405
epic: E2605181400
created: 2026-05-18
---

# Infinite-Duration Effect + 500ms Loop

## User Story
As a **player holding the Blueprint Book**, I want **the block highlight to appear instantly and persist without flickering** so that **I can see clearly which block I'm aiming at and whether I can afford it**.

## Acceptance Criteria

### Checklist
- [ ] `UPDATE_INTERVAL_MILLIS` changed from 100 to 500
- [ ] New `lastAffordable` boolean field tracks previous affordability state
- [ ] `addEffect(ref, effect, UPDATE_INTERVAL_MILLIS, OverlapBehavior.OVERWRITE, store)` replaced with infinite/long-duration variant
- [ ] After confirming target unchanged + entity valid, affordability is recomputed and compared to `lastAffordable` — early return if same
- [ ] `lastAffordable` updated on every entity spawn
- [ ] Build compiles clean

### Scenarios
**Stable aim — no respawn**
- **Given** highlight entity exists at target block
- **When** loop fires and target + affordability haven't changed
- **Then** entity is not touched, loop returns early

**Affordability change — respawn with new effect**
- **Given** highlight entity shows green glow (affordable)
- **When** player's inventory changes making recipe unaffordable
- **Then** old entity is removed, new entity spawned with red effect

## Notes
- File: `src/main/java/com/CodeCreature/ui/blueprintbook/BlueprintBookParticleLoop.java`
- ~15 lines changed in a single file
- Risk: `addInfiniteEffect()` signature — fallback to `addEffect(ref, effect, 10000, OverlapBehavior.OVERWRITE, store)`
