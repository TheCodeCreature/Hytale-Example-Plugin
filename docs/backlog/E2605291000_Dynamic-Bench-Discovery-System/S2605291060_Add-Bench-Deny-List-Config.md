---
id: S2605291060
type: story
title: "Add Bench Deny List to Plugin Config"
status: backlog
priority: medium
feature: F2605291015
epic: E2605291000
created: 2026-05-29
---

# Add Bench Deny List to Plugin Config

## User Story
As a **server operator**, I want **a config file where I can list bench IDs to exclude** so that **I can control which benches the plugin manages without recompiling**.

## Acceptance Criteria

### Checklist
- [ ] A JSON config file (e.g., `bench-config.json` or a section in existing config) has a `denyList` array
- [ ] Default value is an empty array `[]` (all benches included)
- [ ] The file is loaded at plugin startup
- [ ] Invalid bench IDs in the deny list are logged as warnings but don't crash
- [ ] The config file is auto-created with defaults if it doesn't exist

### Scenarios
**Default config**
- **Given** no `bench-config.json` exists
- **When** the plugin starts
- **Then** an empty-deny-list config is created and all benches are discovered

**Excluding benches**
- **Given** `bench-config.json` contains `{"denyList": ["Processing", "Salvage_Bench"]}`
- **When** the plugin starts
- **Then** Processing and Salvage_Bench recipes are excluded from all systems

## Notes
- Consider using the existing plugin config loading mechanism if one exists.
- Future enhancement: add a `mode` field with values `"deny"` or `"allow"` to switch between deny-list and allow-list behavior.
