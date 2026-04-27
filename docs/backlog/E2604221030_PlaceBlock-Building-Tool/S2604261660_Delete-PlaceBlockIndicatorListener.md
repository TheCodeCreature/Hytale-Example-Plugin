---
id: S2604261660
type: story
title: "Delete PlaceBlockIndicatorListener Stub"
status: backlog
priority: medium
feature: F2604261630
epic: E2604221030
created: 2026-04-26
---

# Delete PlaceBlockIndicatorListener Stub

## User Story
As a **developer**, I want the PlaceBlockIndicatorListener stub deleted so that affordability is handled solely by PlaceholderSyncSystem's state transitions, removing dead code.

## Acceptance Criteria

### Checklist
- [ ] `PlaceBlockIndicatorListener.java` deleted
- [ ] Any registration of PlaceBlockIndicatorListener in plugin setup removed (if present)
- [ ] No remaining references to PlaceBlockIndicatorListener in the codebase

### Scenarios

**No regression**
- **Given** PlaceBlockIndicatorListener is deleted
- **When** the server starts and a player arms a placeholder
- **Then** affordability indicators still work via PlaceholderSyncSystem + state transitions

## Notes
- PlaceBlockIndicatorListener was a stub with all methods as `// TODO`. Its intended responsibility (affordability-driven quality switching) is now handled by S2604261650 (Affordability State Transitions in PlaceholderSyncSystem).
- Verify there are no references in the plugin setup class before deleting.
