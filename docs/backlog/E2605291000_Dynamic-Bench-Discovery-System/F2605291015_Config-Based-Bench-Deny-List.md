---
id: F2605291015
type: feature
title: "Config-Based Bench Deny List"
status: backlog
priority: medium
epic: E2605291000
created: 2026-05-29
---

# Config-Based Bench Deny List

## Description
Add a configuration mechanism that allows excluding specific bench IDs from auto-discovery. By default the deny list is empty (all benches are included). The deny list is read at plugin startup and passed to `BenchRegistry` during initialization.

## Acceptance Criteria

### Checklist
- [ ] A config file or section defines a deny list of bench IDs to exclude
- [ ] `BenchRegistry` respects the deny list during discovery — denied benches are never registered
- [ ] Recipes for denied benches are not indexed in `RecipeFilterRegistry`
- [ ] Denied benches do not appear as UI tabs
- [ ] The deny list is hot-readable (changes take effect on next server restart, no recompile needed)

### Scenarios
**Excluding Processing bench**
- **Given** the deny list contains `["Processing"]`
- **When** BenchRegistry scans recipes
- **Then** Processing recipes are skipped and no Processing tab appears in the UI

**Empty deny list**
- **Given** the deny list is empty `[]`
- **When** BenchRegistry scans recipes
- **Then** all discovered benches are registered

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605291060 | Add Bench Deny List to Plugin Config | backlog |
| S2605291065 | Wire Deny List into Discovery Pipeline | backlog |

## Notes
- Config format: JSON array in the plugin's existing config file or a new `bench-config.json`
- Future enhancement: could support a whitelist mode as well (only include listed benches)
