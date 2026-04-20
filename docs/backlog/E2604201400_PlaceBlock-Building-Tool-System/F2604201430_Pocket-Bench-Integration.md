---
id: F2604201430
type: feature
title: "Pocket Bench Integration"
status: backlog
priority: low
epic: E2604201400
created: 2026-04-20
---

# Pocket Bench Integration

## Description
Optional enhancement: when a player opens the existing Pocket Bench while a PlaceBlock is in their hotbar, the system can optionally open the PlaceBlockSelectorWindow instead of the standard PortableBenchWindow, allowing recipe assignment from the Pocket Bench without rewriting any existing code.

## Acceptance Criteria

### Checklist
- [ ] Pocket Bench detects PlaceBlock in hotbar when F is pressed
- [ ] If PlaceBlock found, opens PlaceBlockSelectorWindow instead of PortableBenchWindow
- [ ] If no PlaceBlock found, existing behavior is unchanged
- [ ] PortableBenchWindow.java is NOT modified
- [ ] Assignment via Pocket Bench produces identical results to F-key menu and Assignment Bench

### Scenarios
**Pocket Bench with PlaceBlock in hotbar**
- **Given** a player holds PortableBench_Builders and has PlaceBlock_Default in another hotbar slot
- **When** they press F
- **Then** a PlaceBlockSelectorWindow opens instead of the normal bench window

**Pocket Bench without PlaceBlock**
- **Given** a player holds PortableBench_Builders with no PlaceBlock in hotbar
- **When** they press F
- **Then** the normal PortableBenchWindow opens (existing behavior)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604201510 | Add PlaceBlock Detection to PortableBenchInteraction | backlog |

## Notes
- ~10 lines of code added to PortableBenchInteraction.tick0()
- Low priority — the F-key menu and Assignment Bench provide full functionality without this
- Future alternative: a wrapper window that supports both craft AND assign in one UI
