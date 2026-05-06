---
id: S2605051515
type: story
title: "Resource Type Filter Build/Bind/Update Logic"
status: backlog
priority: high
feature: F2605051500
epic: E2604221030
created: 2026-05-05
---

# Resource Type Filter Build/Bind/Update Logic

## User Story
As a **player**, I want to toggle resource type icons on and off so that I can control which resource types are included in recipe filtering.

## Acceptance Criteria

### Checklist
- [ ] ResourceTypeFilterButton.ui instances are appended to #ResourceTypeGrid during build()
- [ ] Click events are bound on each button using indexed selectors (#ResourceTypeGrid[i] #GroupBtn)
- [ ] handleDataEvent() processes "ResourceType:idx:N" actions to toggle resource types on/off
- [ ] updateResourceTypes(cmd) iterates all slots, sets icon paths from Icons/ResourceTypes/, and toggles active overlay
- [ ] A clear button ("ResourceType:All") clears all active resource type selections
- [ ] Icons are loaded from Common/UI/Custom/Common/Icons/ResourceTypes/ using the icon filename as the resource type identifier
- [ ] All ~78 resource types are pre-allocated as button slots during build()

### Scenarios
**Player toggles a resource type on**
- **Given** the bench is in Resource Driven mode with no resource types selected
- **When** the player clicks the "Wood" resource type icon
- **Then** the Wood icon shows an active overlay and the recipe grid updates

**Player toggles a resource type off**
- **Given** the "Wood" resource type is active
- **When** the player clicks the "Wood" icon again
- **Then** the active overlay is removed and the recipe grid updates

**Player clears all selections**
- **Given** multiple resource types are selected
- **When** the player clicks the clear button
- **Then** all resource type active overlays are removed and the recipe grid updates

## Notes
- Follows the exact same pattern as buildMaterialGroupBindings/updateMaterialGroups
- Resource type list is a static ordered list derived from icon filenames
- Icon path format: "Common/Icons/ResourceTypes/{filename}.png"
