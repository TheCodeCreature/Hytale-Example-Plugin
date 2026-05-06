---
id: S2605051505
type: story
title: "Replace Placeholder Section with Resource Type Grid UI"
status: backlog
priority: high
feature: F2605051500
epic: E2604221030
created: 2026-05-05
---

# Replace Placeholder Section with Resource Type Grid UI

## User Story
As a **player**, I want to see a grid of resource type icons in the bench details panel so that I can visually identify and select which resource types to filter by.

## Acceptance Criteria

### Checklist
- [ ] The #InputSlotLabel ("Placeholders") label is replaced with "Resource Types"
- [ ] The #NoPlaceholdersLabel and #PlaceholderList are replaced with a scrollable icon grid container (#ResourceTypeGrid)
- [ ] A new ResourceTypeFilterButton.ui template is created (36x36 icon button with active overlay, matching GroupFilterButton.ui pattern)
- [ ] The grid uses LeftCenterWrap layout mode inside a TopScrolling parent
- [ ] A clear button is added to reset all resource type selections

### Scenarios
**Empty grid renders correctly**
- **Given** the bench page loads
- **When** the resource type grid section is visible
- **Then** all resource type icons render in a wrapping grid within the right column (270px wide)

## Notes
- UI-only change — no Java logic in this story (build/bind/update is S2605051515)
- The grid container must support ~78 pre-allocated icon button slots
