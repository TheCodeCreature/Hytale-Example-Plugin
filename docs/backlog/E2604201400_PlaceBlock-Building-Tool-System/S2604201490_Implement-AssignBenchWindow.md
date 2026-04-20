---
id: S2604201490
type: story
title: "Implement AssignBenchWindow"
status: backlog
priority: medium
feature: F2604201420
epic: E2604201400
created: 2026-04-20
---

# Implement AssignBenchWindow

## User Story
As a **player**, I want **to insert my PlaceBlock into the Assignment Bench, browse recipes, and assign one** so that **I can arm my building tool at a crafting station**.

## Acceptance Criteria

### Checklist
- [ ] Window type is StructuralCrafting (implements ItemContainerWindow + MaterialContainerWindow)
- [ ] Input slot (slot 0) only accepts PlaceBlock items via slot filter
- [ ] Options container populates with recipe outputs when PlaceBlock is inserted
- [ ] CraftRecipeAction is repurposed as "Assign" — writes metadata, no material consumption
- [ ] PlaceBlock quality variant is swapped in the input slot after assignment
- [ ] On close, PlaceBlock is returned to player's inventory
- [ ] Non-block recipes are excluded from the display

### Scenarios
**Insert PlaceBlock and assign**
- **Given** the window is open
- **When** a player inserts PlaceBlock_Default and selects "Oak Planks"
- **Then** the PlaceBlock in slot 0 becomes Armed/NoResources with recipe metadata

**Close returns PlaceBlock**
- **Given** a PlaceBlock is in the input slot
- **When** the player closes the window
- **Then** the PlaceBlock is returned to their hotbar or storage

## Notes
- Follows PortableStructuralWindow pattern closely
- Skeleton code exists at AssignBenchWindow.java with detailed TODOs
