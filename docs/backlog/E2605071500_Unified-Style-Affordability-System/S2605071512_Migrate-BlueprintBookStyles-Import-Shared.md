---
id: S2605071512
type: story
title: "Migrate BlueprintBookStyles.ui to Import Shared"
status: backlog
priority: high
feature: F2605071510
epic: E2605071500
created: 2026-05-07
---

# Migrate BlueprintBookStyles.ui to Import Shared

## User Story
As a **plugin developer**, I want **BlueprintBookStyles.ui to re-export tokens from SharedStyles.ui** so that **existing Value.ref() paths continue to work while the styles are centralized**.

## Acceptance Criteria

### Checklist
- [ ] BlueprintBookStyles.ui imports SharedStyles.ui
- [ ] Universal tokens previously defined inline in BlueprintBookStyles.ui now re-export from SharedStyles.ui
- [ ] Bench-specific styles (@EntryStyle, @SelectedEntryStyle, @UnaffordableEntryStyle, @SetGroupLabelStyle, @EmptyStateStyle, @SelectedCellButtonStyle, @WrapSecondaryButtonStyle) remain in BlueprintBookStyles.ui
- [ ] All 8 Value.ref() paths in BlueprintSelectionPage.java resolve correctly at runtime
- [ ] All 9 .ui files that import $S = "BlueprintBookStyles.ui" continue to work
- [ ] No visual regression in BlueprintBook UI

### Scenarios
**Facade re-export preserves Value.ref() resolution**
- **Given** @FilterActiveStyle is now defined in SharedStyles.ui
- **And** BlueprintBookStyles.ui re-exports it via alias
- **When** BlueprintSelectionPage.java sets a button style via Value.ref("Pages/BlueprintBook/BlueprintBookStyles.ui", "@FilterActiveStyle")
- **Then** the style resolves correctly and the button renders with the active filter appearance

## Notes
- Test by opening the BlueprintBook and verifying: filter buttons toggle correctly, recipe cells dim/highlight, cost quantities show red/white, detail panel mutes correctly
- If Hytale UI doesn't support re-export via alias, keep duplicate definitions in BlueprintBookStyles.ui and add a comment noting the canonical source is SharedStyles.ui
