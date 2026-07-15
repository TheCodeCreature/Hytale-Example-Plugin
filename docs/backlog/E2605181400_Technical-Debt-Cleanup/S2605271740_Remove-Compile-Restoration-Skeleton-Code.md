---
id: S2605271740
type: story
title: "Remove Compile Restoration Skeleton Code"
status: done
priority: medium
feature: F2605261200
epic: E2605181400
created: 2026-05-27
---

# Remove Compile Restoration Skeleton Code

## User Story
As a **developer**, I want **compile restoration scaffolding removed** so that **the codebase has no dead code from the migration handoff**.

## Acceptance Criteria

### Checklist
- [ ] All `compileRestorationSkeleton()` methods removed from 4 files
- [ ] All `COMPILE_RESTORATION_TODO_ID` constants removed
- [ ] All `TODO[S2605261205]` markers removed (9 total)
- [ ] Build compiles and tests pass after removal

### Affected Files
1. `StencilBookParticleLoop.java` — skeleton method + constant
2. `StencilBookPickStencilInteraction.java` — skeleton method + constant
3. `StencilInputListener.java` — skeleton method + constant
4. `BoundingBoxRayCast.java` — skeleton method + constant (verify)

## Notes
Per the design doc `design-s2605261205-compile-restoration-vector-types.md` §7: "What can be deleted after migration: Any temporary compatibility comments/TODO anchors introduced for this compile-restoration handoff."
