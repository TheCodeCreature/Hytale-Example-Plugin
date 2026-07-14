---
id: F2605020300
type: feature
title: "Material Group Pre-Filter"
status: backlog
priority: high
epic: E2604221030
created: 2026-05-02
---

# Material Group Pre-Filter

## Description
A horizontal row of icon buttons above the set filter sidebar in the Stencil Crafting UI. Each button represents a material group (Wood, Rock, Furniture, etc.) auto-derived from set name prefixes. Acts as a pre-filter: selecting "Wood" hides non-Wood sets from the left sidebar column.

## Acceptance Criteria

### Checklist
- [ ] Material groups auto-derived from set prefixes (first segment before `_`)
- [ ] Horizontal icon bar appears above the set filter sidebar
- [ ] Each group button shows an ItemIcon of a representative item from that group
- [ ] "All" button at the start resets the pre-filter (shows all sets)
- [ ] Multi-select: player can toggle multiple groups simultaneously
- [ ] Selecting groups filters the set sidebar to only show matching sets
- [ ] Compatible set selections are preserved when groups change
- [ ] Incompatible set selections are removed when their group is deselected
- [ ] Selected material groups persist in player preferences between bench opens
- [ ] Recipe grid updates to reflect the combined group + set filter

### Scenarios
**Player selects a single material group**
- **Given** the bench is open with "All" groups active
- **When** the player clicks the "Wood" group icon
- **Then** only Wood-prefixed sets appear in the sidebar (e.g. "Hardwood", "Mahogany")
- **And** the recipe grid shows only recipes from those sets

**Player multi-selects material groups**
- **Given** the "Wood" group is active
- **When** the player also clicks the "Rock" group icon
- **Then** both Wood and Rock sets appear in the sidebar
- **And** the recipe grid shows recipes from both

**Player deselects a group with active set filter**
- **Given** "Wood" and "Rock" are active, and set "Hardwood" is selected
- **When** the player deselects the "Wood" group
- **Then** "Hardwood" is removed from active set filters (incompatible)
- **And** only Rock sets remain in the sidebar

**Player clicks "All"**
- **Given** "Wood" group is active with "Hardwood" set selected
- **When** the player clicks the "All" group button
- **Then** all groups are deselected, all sets appear in the sidebar
- **And** previously selected sets are preserved if still visible

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605020305 | Pipeline Material Group Extraction and Filtering | backlog |
| S2605020310 | Material Group UI Bar and Event Handling | backlog |
| S2605020315 | Persist Material Group Preferences | backlog |

## Notes
- Max ~15 group buttons due to horizontal space constraints (1060px width)
- Representative item per group: first alphabetical item from the group's sets
- Uses ItemIcon (proven approach) — native icon paths unreachable from plugin .ui files
- Risk: groups with very few items may feel unnecessary. Consider a minimum item threshold.
