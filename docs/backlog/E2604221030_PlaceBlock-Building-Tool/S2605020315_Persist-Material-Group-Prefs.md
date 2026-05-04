---
id: S2605020315
type: story
title: "Persist Material Group Preferences"
status: backlog
priority: medium
feature: F2605020300
epic: E2604221030
created: 2026-05-02
---

# Persist Material Group Preferences

## User Story
As a **player**, I want my material group selections to be remembered between bench opens so that I don't have to re-select them each time.

## Acceptance Criteria

### Checklist
- [ ] `BlueprintBenchPrefs` includes `activeMaterialGroups` field (List<String>)
- [ ] Groups are saved when the bench is dismissed
- [ ] Groups are restored when the bench is opened
- [ ] Invalid/stale group names are silently ignored on load

### Scenarios
**Prefs round-trip**
- **Given** player has "Wood" and "Rock" groups active
- **When** they close and reopen the bench
- **Then** "Wood" and "Rock" are still active

## Notes
- Same persistence mechanism as existing set filters (BlueprintBenchPrefsStore)
