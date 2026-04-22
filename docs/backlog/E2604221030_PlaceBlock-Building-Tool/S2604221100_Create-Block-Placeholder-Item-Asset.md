---
id: S2604221100
type: story
title: "Create Block_Placeholder Item Asset"
status: backlog
priority: high
feature: F2604221035
epic: E2604221030
created: 2026-04-22
---

# Create Block_Placeholder Item Asset

## User Story
As a **player**, I want a **Block_Placeholder item** so that **I can place it in the Builders Bench to access the PlaceBlock recipe browsing workflow**.

## Acceptance Criteria

### Checklist
- [ ] Item JSON asset `Block_Placeholder.json` exists in the asset pack
- [ ] Item has an appropriate icon distinguishable from other tools
- [ ] Item is categorized as `Tool.PlaceBlock` (or similar) for right-click interaction handling
- [ ] Item has `Quality: "Tool"` (blue highlight) by default
- [ ] Item has `MaxStack: 1` (tools don't stack)
- [ ] Item has a `Set` field for ResourceType integration if needed
- [ ] Item appears in creative mode / admin commands for testing

### Scenarios
**Player receives placeholder**
- **Given** the player uses an admin command to get the item
- **When** it appears in their inventory
- **Then** it shows with a blue "Tool" highlight and the placeholder icon

## Notes
- Reference existing tool items (`PortableBench_Builders.json`) for format patterns.
- The `Quality` field controls the highlight color. Starting value is `"Tool"` (blue).
- This item needs a custom interaction type registered for right-click behavior (separate story).
