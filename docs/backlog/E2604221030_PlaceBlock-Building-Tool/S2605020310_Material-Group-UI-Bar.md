---
id: S2605020310
type: story
title: "Material Group UI Bar and Event Handling"
status: backlog
priority: high
feature: F2605020300
epic: E2604221030
created: 2026-05-02
---

# Material Group UI Bar and Event Handling

## User Story
As a **player**, I want to see a row of material group icons above the set filters so that I can quickly pre-filter by material type with one click.

## Acceptance Criteria

### Checklist
- [ ] New `MaterialGroupButton.ui` component with ItemIcon and optional tooltip
- [ ] Horizontal group bar rendered above the set filter sidebar in BlueprintBookPage.ui
- [ ] "All" group button at position 0
- [ ] Each group button shows a representative item icon via ItemIcon
- [ ] Clicking a group button toggles it (multi-select)
- [ ] Clicking "All" clears all group selections
- [ ] Active group buttons use FILTER_ACTIVE style; inactive use FILTER_INACTIVE
- [ ] Set sidebar updates immediately when groups change
- [ ] Recipe grid updates immediately when groups change
- [ ] Incompatible set selections are pruned when a group is deselected

### Scenarios
**Toggle group on**
- **Given** no groups are selected ("All" is active)
- **When** player clicks the "Wood" group icon
- **Then** "Wood" becomes active, "All" becomes inactive
- **And** set sidebar shows only Wood sets

**Toggle group off**
- **Given** "Wood" is the only active group
- **When** player clicks "Wood" again
- **Then** "Wood" is deselected, behavior returns to "All"

## Notes
- Max 15 group buttons (UI horizontal space constraint)
- Tooltip on hover shows the group name
