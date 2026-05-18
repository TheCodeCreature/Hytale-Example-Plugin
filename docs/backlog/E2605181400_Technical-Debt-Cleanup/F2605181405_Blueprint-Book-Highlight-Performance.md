---
id: F2605181405
type: feature
title: "Blueprint Book Highlight Performance"
status: backlog
priority: high
epic: E2605181400
created: 2026-05-18
---

# Blueprint Book Highlight Performance

## Description
Reduce the BlueprintBookParticleLoop from a 100ms entity-respawn cycle to a 500ms stale-target-detection loop with infinite-duration effects. The block-model highlight entity persists without re-application, and only respawns when the target block or affordability state actually changes.

## Acceptance Criteria

### Checklist
- [ ] Loop interval is 500ms (named constant, adjustable without logic changes)
- [ ] Entity is NOT replaced when target block + affordability are unchanged
- [ ] Entity IS replaced when affordability flips (green ↔ red)
- [ ] Entity IS replaced when target block changes
- [ ] Effect does not expire visually (no flickering)
- [ ] Unequipping book or disconnecting removes entity within 500ms

### Scenarios
**Holding book, steady aim at affordable block**
- **Given** player holds Blueprint Book and aims at a block with recipe they can afford
- **When** 5 seconds elapse with no crosshair movement
- **Then** zero entity respawns occur after the initial spawn (entity persists with green glow)

**Affordability flip mid-aim**
- **Given** player aims at a block they can afford
- **When** they craft away their last unit of a required material
- **Then** exactly 1 entity respawn occurs (switches from green to red glow)

**Rapid crosshair movement**
- **Given** player sweeps crosshair across 5 different recipe blocks in 2 seconds
- **When** sweep completes
- **Then** highlight shows on the final block within 500ms of stopping

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605181406 | Infinite-Duration Effect + 500ms Loop | backlog |

## Notes
- Design doc: docs/design-deferred-fixes.md (Item #5)
- Research: docs/hytale/research-particle-highlight-and-container-events.md (Question 1)
- Fallback if `addInfiniteEffect()` unavailable: use `addEffect(10000ms)` — still eliminates flickering
- Product Owner approved 500ms as acceptable since highlight is passive feedback, not an action gate
