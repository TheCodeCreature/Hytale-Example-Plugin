---
id: S2604201510
type: story
title: "Add PlaceBlock Detection to PortableBenchInteraction"
status: backlog
priority: low
feature: F2604201430
epic: E2604201400
created: 2026-04-20
---

# Add PlaceBlock Detection to PortableBenchInteraction

## User Story
As a **player**, I want **the Pocket Bench to detect my PlaceBlock and open the recipe selector** so that **I can assign recipes without opening a separate menu**.

## Acceptance Criteria

### Checklist
- [ ] PortableBenchInteraction.tick0() scans hotbar for PlaceBlock items
- [ ] If PlaceBlock found, opens PlaceBlockSelectorWindow
- [ ] If no PlaceBlock found, opens PortableBenchWindow (unchanged behavior)
- [ ] PortableBenchWindow.java is NOT modified

### Scenarios
**Pocket Bench with PlaceBlock**
- **Given** player holds PortableBench_Builders, PlaceBlock_Default in slot 3
- **When** they press F
- **Then** PlaceBlockSelectorWindow opens instead of PortableBenchWindow

## Notes
- ~10 lines of code in PortableBenchInteraction.tick0()
- Low priority — F-key menu and Assignment Bench already provide full assignment functionality
