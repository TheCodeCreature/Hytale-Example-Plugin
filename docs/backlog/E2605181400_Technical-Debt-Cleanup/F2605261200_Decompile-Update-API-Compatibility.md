---
id: F2605261200
type: feature
title: "Decompile Update API Compatibility"
status: backlog
priority: critical
epic: E2605181400
created: 2026-05-26
---

# Decompile Update API Compatibility

## Description
Restore server startup after decompile-based API updates by adapting plugin code to updated engine types and signatures while preserving identical player-visible behavior.

## Acceptance Criteria

### Checklist
- [ ] The plugin compiles against the updated decompiled server API with no symbol resolution errors.
- [ ] Player-facing behavior for blueprint targeting, stencil selection, and placement preview remains unchanged.

### Scenarios
**Server update regression recovery**
- **Given** the server API has changed after running DecompileServer
- **When** the plugin is built and the server starts
- **Then** startup succeeds without compile-time symbol errors from deprecated or moved engine types

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605261205 | Fix Missing Vector Types After Decompile Update | backlog |

## Notes
This feature is a compatibility patch only. It should avoid broad refactors and prioritize mechanical API alignment changes first.
