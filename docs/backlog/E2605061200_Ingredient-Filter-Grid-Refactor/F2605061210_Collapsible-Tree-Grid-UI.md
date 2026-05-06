---
id: F2605061210
type: feature
title: "Collapsible Tree Grid UI"
status: backlog
priority: high
epic: E2605061200
created: 2026-05-06
---

# Collapsible Tree Grid UI

## Description
A collapsible tree grid layout for the ingredient filter. Each All_* group is a clickable section header that expands/collapses its children. Children (ResourceType and ExactID entries) display in a wrap grid within each section. Reusable .ui templates for group headers and filter buttons.

## Acceptance Criteria

### Checklist
- [ ] Group headers are clickable to expand/collapse
- [ ] Children wrap within each group section
- [ ] Collapsed groups hide their children
- [ ] Group header shows group icon and label
- [ ] Child entries show item/resource type icons (36x36)
- [ ] Scrollable container for the entire tree
- [ ] Reusable GroupSectionHeader.ui template
- [ ] Reusable FilterIconButton.ui template (derived from GroupFilterButton.ui)

### Scenarios
**Player expands a group**
- **Given** the "All Wood" group is collapsed
- **When** the player clicks the "All Wood" header
- **Then** the header expands to show all wood ResourceType entries in a wrap grid

**Player collapses a group**
- **Given** the "All Rock" group is expanded showing its children
- **When** the player clicks the "All Rock" header
- **Then** the children are hidden and the header shows collapsed state

## Notes
- Current layout uses LeftCenterWrap for the flat icon grid
- Section headers need a different layout approach — likely a vertical stack of (header + wrap content) groups
- Icons: All_* uses existing Any_*.png icons, ResourceType uses ResourceType.png, ExactID uses item icons
- HTML mockup should be created to validate the layout before implementation
