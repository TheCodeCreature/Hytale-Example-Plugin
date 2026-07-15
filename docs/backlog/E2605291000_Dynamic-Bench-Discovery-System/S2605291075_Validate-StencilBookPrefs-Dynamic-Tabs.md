---
id: S2605291075
type: story
title: "Validate StencilBookPrefs for Dynamic Tabs"
status: backlog
priority: medium
feature: F2605291020
epic: E2605291000
created: 2026-05-29
---

# Validate StencilBookPrefs for Dynamic Tabs

## User Story
As a **player**, I want **my selected tab preference to persist across sessions** so that **I don't have to re-select my bench tab every time I open the UI**.

## Acceptance Criteria

### Checklist
- [ ] `StencilBookPrefs` stores the active tab as a string bench ID
- [ ] If a persisted tab ID no longer exists (bench was removed or denied), gracefully fall back to "All"
- [ ] New bench tabs can be persisted without config changes
- [ ] Preferences file is forward-compatible — adding new benches doesn't corrupt existing prefs

### Scenarios
**Graceful fallback**
- **Given** a player's prefs file has `activeTab: "Loom"`
- **When** the server restarts with "Loom" in the deny list
- **Then** the player's active tab defaults to "All" without errors

## Notes
- `StencilBookPrefs` currently stores `activeTab` as a string — this should already be compatible. Main concern is error handling for stale tab IDs.
