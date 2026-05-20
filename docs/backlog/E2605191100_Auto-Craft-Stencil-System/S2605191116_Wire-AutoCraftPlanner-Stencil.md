---
id: S2605191116
type: story
title: "Wire AutoCraftPlanner into StencilPlacementSystem"
status: backlog
priority: high
feature: F2605191115
epic: E2605191100
created: 2026-05-19
---

# Wire AutoCraftPlanner into StencilPlacementSystem

## User Story
As a **player**, I want **the stencil to automatically craft intermediates from my raw materials when I place a block** so that **I don't have to leave my build site to visit a bench**.

## Acceptance Criteria

### Checklist
- [ ] Steps 5-7 in `handle()` replaced with `AutoCraftPlanner.plan()` + execute
- [ ] Fast path (player has all intermediates) produces identical behavior to current
- [ ] Slow path consumes raw materials from inventory atomically
- [ ] Failed consumption still cancels event and sends error
- [ ] Success message includes auto-craft info when applicable
- [ ] Bump-and-let-through strategy preserved

### Scenarios
**Fast path unchanged**
- **Given** recipe needs 3 bricks, player has 5 bricks
- **When** player places via stencil
- **Then** 3 bricks consumed, block placed (same as current)

**Auto-craft placement**
- **Given** recipe needs 3 bricks, player has 0 bricks, 50 cobblestone
- **When** player places via stencil
- **Then** 36 cobblestone consumed, block placed

**Insufficient raw materials**
- **Given** recipe needs 3 bricks, player has 0 bricks, 10 cobblestone
- **When** player places via stencil
- **Then** event cancelled, "Not enough resources!" message, nothing consumed

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.1
