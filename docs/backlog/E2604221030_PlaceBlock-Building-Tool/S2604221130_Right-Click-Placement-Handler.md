---
id: S2604221130
type: story
title: "Right-Click Placement Handler"
status: backlog
priority: high
feature: F2604221045
epic: E2604221030
created: 2026-04-22
---

# Right-Click Placement Handler

## User Story
As a **player**, I want to **right-click to place the selected block** so that **I can build directly from my placeholder tool**.

## Acceptance Criteria

### Checklist
- [ ] Right-clicking with an armed placeholder attempts to place the selected block type
- [ ] The placed block is the actual crafted block type, NOT the placeholder block
- [ ] Placement respects the selected block's placement rules
- [ ] Placeholder is NOT consumed on placement — remains in hand for repeated use
- [ ] Right-clicking with an unarmed placeholder does nothing (or shows a message)
- [ ] The tool interaction is registered for right-click handling (Tool category)
- [ ] Placement triggers resource consumption (separate story S2604221135)

### Scenarios
**Successful placement**
- **Given** the player holds an armed placeholder with sufficient resources
- **When** they right-click a valid location
- **Then** the actual block is placed at that location

**Unarmed right-click**
- **Given** the player holds an unarmed placeholder
- **When** they right-click
- **Then** nothing happens (optionally: "No recipe selected" message)

**Placeholder persists**
- **Given** the player places a block
- **When** they check their hotbar
- **Then** the armed placeholder is still there, unchanged

## Notes
- The right-click handler must coordinate with the resource consumption system (S2604221135) and the PlacementCostScaler exclusion (S2604221140).
- The tool's interaction type (`PlaceBlock_Menu` codec) should handle the right-click event.
- Risk: The engine may handle right-click placement at the item level. Intercepting and replacing the block type requires understanding the placement pipeline.
