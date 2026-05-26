---
id: S2605261205
type: story
title: "Fix Missing Vector Types After Decompile Update"
status: backlog
priority: critical
feature: F2605261200
epic: E2605181400
created: 2026-05-26
---

# Fix Missing Vector Types After Decompile Update

## User Story
As a **server developer**, I want **vector type references updated to the current server API** so that **the plugin compiles and the server runs after a decompile update**.

## Acceptance Criteria

### Checklist
- [ ] All compile errors caused by unresolved `Vector3d`, `Vector3f`, and `Vector3i` symbols are resolved.
- [ ] A full `:compileJava` run succeeds on the updated API.
- [ ] Blueprint book targeting highlight and stencil target selection still point to the same block under the crosshair.

### Scenarios
**Blueprint targeting still works after compile fix**
- **Given** a player points at a valid target block with a blueprint-related item
- **When** targeting logic runs
- **Then** the same target block coordinates are resolved and used for highlight/selection as before the API update

## Notes
Scope is limited to compatibility fixes in affected files and minimal signature/nullability adjustments required to compile cleanly.
