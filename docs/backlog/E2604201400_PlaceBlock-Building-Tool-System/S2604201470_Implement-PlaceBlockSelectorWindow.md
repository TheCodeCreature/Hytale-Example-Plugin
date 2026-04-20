---
id: S2604201470
type: story
title: "Implement PlaceBlockSelectorWindow"
status: backlog
priority: high
feature: F2604201415
epic: E2604201400
created: 2026-04-20
---

# Implement PlaceBlockSelectorWindow

## User Story
As a **player**, I want **to browse recipes and assign one to my PlaceBlock** so that **I can arm the tool for building**.

## Acceptance Criteria

### Checklist
- [ ] Window shows recipes from all configured bench categories
- [ ] Only block-output recipes are shown (non-block recipes excluded per Contract #9)
- [ ] Clicking a recipe assigns it to the PlaceBlock (writes metadata, no material cost)
- [ ] "Clear Recipe" tab resets PlaceBlock to Default state
- [ ] PlaceBlock quality variant is swapped after assignment
- [ ] PlaceBlock in player's hotbar is replaced with the updated ItemStack

### Scenarios
**Assign a recipe**
- **Given** a PlaceBlockSelectorWindow is open
- **When** the player clicks "Oak Planks" recipe
- **Then** the held PlaceBlock gets recipe metadata and swaps to Armed or NoResources

**Clear recipe**
- **Given** PlaceBlock_Armed with recipe
- **When** the player clicks "Clear Recipe" tab
- **Then** PlaceBlock swaps to Default, metadata removed

## Notes
- handleAction intercepts CraftRecipeAction and repurposes it as "assign"
- Must filter out non-block recipes from the display (check Item.getBlockId() on output)
- Skeleton code exists at PlaceBlockSelectorWindow.java
