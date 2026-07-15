---
id: S2604271740
type: story
title: "Unify Bench ID Definitions"
status: backlog
priority: high
feature: F2604271700
epic: E2604201200
created: 2026-04-27
---

# Unify Bench ID Definitions

## User Story
As a **developer**, I want **bench IDs defined in exactly one place** so that **adding a new bench like "Blacksmith" requires a single code change**.

## Acceptance Criteria

### Checklist
- [ ] One `public static` field holds the canonical list of allowed bench IDs
- [ ] `StencilSelectionPage.ALLOWED_BENCHES` is removed — references the shared field
- [ ] `DropScaler.apply()` passes the shared list to initialization
- [ ] `BenchCategory` enum constants reference the shared list (or are derived from it)
- [ ] Adding a new bench ID to the shared list makes it available in both UI and drop scaling

### Scenarios
**Adding a bench**
- **Given** a developer adds `"Blacksmith"` to the shared bench ID list
- **When** both StencilSelectionPage and DropScaler initialize
- **Then** Blacksmith recipes appear in the UI and get drop scaling applied

## Notes
- The shared field should be mutable (`ArrayList`) for runtime configurability, matching the current `ALLOWED_BENCHES` pattern.
- Consider placing it on the `RecipeFilterRegistry` or a dedicated `BenchConfig` class.
