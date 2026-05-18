---
id: S2605181423
type: story
title: "Wire Controllers into BlueprintSelectionPage"
status: backlog
priority: medium
feature: F2605181420
epic: E2605181400
created: 2026-05-18
---

# Wire Controllers into BlueprintSelectionPage

## User Story
As a **developer**, I want **the page to be a thin event orchestrator** so that **adding new features to the bench doesn't require understanding 1000+ lines of mixed concerns**.

## Acceptance Criteria

### Checklist
- [ ] `BlueprintSelectionPage` instantiates both controllers in `build()`
- [ ] All call sites of removed methods replaced with delegation calls
- [ ] `handleDataEvent()` routes grid slot clicks through `gridController.resolveRecipeIndex()`
- [ ] Dead fields removed from page (moved to controllers)
- [ ] Page is below 800 lines
- [ ] Full build passes clean

### Scenarios
**End-to-end flow**
- **Given** bench UI is opened
- **When** player filters, selects a recipe, and creates a stencil
- **Then** the entire flow works identically to pre-extraction behavior

## Notes
- Wave 3 — depends on both S2605181421 and S2605181422
- This is the integration/cleanup step
- Product Owner condition: If full filter pipeline verification fails, revert all 3 waves
