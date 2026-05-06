---
id: F2605061215
type: feature
title: "Three-State Checkbox Selection"
status: backlog
priority: high
epic: E2605061200
created: 2026-05-06
---

# Three-State Checkbox Selection

## Description
Each group header has an inline three-state checkbox that reflects the selection state of its children. Clicking the checkbox toggles between select-all and deselect-all. Individually selecting/deselecting children triggers the intermediate "some selected" state. Selection cascades down — selecting a group selects all its children.

## Acceptance Criteria

### Checklist
- [ ] Three visual states: all selected (checked), some selected (indeterminate), none selected (unchecked)
- [ ] Clicking checkbox when none/some selected → selects all children
- [ ] Clicking checkbox when all selected → deselects all children
- [ ] Selecting an individual child updates parent checkbox to reflect partial state
- [ ] Deselecting all children updates parent checkbox to unchecked
- [ ] Cascade-down: selecting a group selects all ResourceType and ExactID children
- [ ] Multi-level cascade: selecting All_* selects its ResourceType children, which selects their ExactID children

### Scenarios
**Player selects a group via checkbox**
- **Given** "All Wood" has no children selected
- **When** the player clicks the "All Wood" checkbox
- **Then** all wood ResourceType entries and their ExactID children are selected, checkbox shows "all selected"

**Player deselects one child**
- **Given** "All Wood" has all children selected (checkbox checked)
- **When** the player deselects "Wood_Hardwood"
- **Then** the "All Wood" checkbox changes to "some selected" (indeterminate)

**Player clears via checkbox**
- **Given** "All Wood" has some children selected
- **When** the player clicks the "All Wood" checkbox
- **Then** all children are selected (toggles to all, since current state is partial)

## Notes
- The three-state checkbox is a reusable component — should be extractable for other filter grids
- State management needs to handle both top-down (group click) and bottom-up (child click) updates efficiently
