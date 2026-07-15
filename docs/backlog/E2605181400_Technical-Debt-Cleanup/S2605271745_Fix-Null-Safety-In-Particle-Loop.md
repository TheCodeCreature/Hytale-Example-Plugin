---
id: S2605271745
type: story
title: "Fix Null Safety in StencilBookParticleLoop"
status: done
priority: high
feature: F2605261200
epic: E2605181400
created: 2026-05-27
---

# Fix Null Safety in StencilBookParticleLoop

## User Story
As a **player**, I want **the stencil book highlight effect to not crash** so that **the game doesn't disconnect me when using the Stencil Book**.

## Acceptance Criteria

### Checklist
- [ ] `effectCtrl` null-checked before calling `addEffect()` in `spawnHighlightEntity()`
- [ ] `effect` (from `EntityEffect.getAssetMap().getAsset()`) null-checked before use
- [ ] `entityRef` from `store.addEntity()` null-checked (it's `@Nullable`)
- [ ] `getHotbar()` null-checked on line 133 before `getItemStack()`

### Scenarios
**Missing effect asset**
- **Given** the `Drop_Rare` effect ID doesn't exist in the asset map
- **When** the particle loop tries to spawn a highlight entity
- **Then** the entity spawns without an effect (log a warning) instead of NPE

**Null hotbar**
- **Given** the player's inventory returns null from `getHotbar()`
- **When** the particle loop checks the held item
- **Then** the highlight entity is removed gracefully instead of NPE

## Notes
Code Reviewer finding #1, #2, #3 — severity: blocked/must-fix.
These are runtime NPE risks that will crash the particle loop thread.
