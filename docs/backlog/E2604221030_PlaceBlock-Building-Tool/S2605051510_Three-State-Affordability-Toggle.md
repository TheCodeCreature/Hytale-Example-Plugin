---
id: S2605051510
type: story
title: "Three-State Affordability Toggle"
status: backlog
priority: high
feature: F2605051500
epic: E2604221030
created: 2026-05-05
---

# Three-State Affordability Toggle

## User Story
As a **player**, I want to cycle the affordability toggle between three modes so that I can choose between seeing all recipes, inventory-based filtering, or resource-type-based filtering.

## Acceptance Criteria

### Checklist
- [ ] The #AffordableToggle button cycles through three states: "All" → "Inventory Driven" → "Resource Driven"
- [ ] The button text updates to reflect the current mode
- [ ] The default mode on bench open is "Inventory Driven"
- [ ] When "Resource Driven" mode is active, the resource type grid section is visually emphasized (active state)
- [ ] When other modes are active, the resource type grid is visually muted but still visible

### Scenarios
**Player cycles through modes**
- **Given** the bench is in "Inventory Driven" mode
- **When** the player clicks the affordability toggle
- **Then** the mode changes to "Resource Driven" and the toggle text updates

**Player cycles past Resource Driven**
- **Given** the bench is in "Resource Driven" mode
- **When** the player clicks the affordability toggle
- **Then** the mode changes to "All"

## Notes
- The existing `affordabilityEnabled` boolean becomes a tri-state enum or int
- Mode preference should persist via the existing savePrefs/loadPrefs mechanism
