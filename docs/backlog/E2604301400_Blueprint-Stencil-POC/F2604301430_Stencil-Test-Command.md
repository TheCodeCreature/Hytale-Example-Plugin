---
id: F2604301430
type: feature
title: "Stencil Test Command"
status: backlog
priority: high
epic: E2604301400
created: 2026-04-30
---

# Stencil Test Command

## Description
A server command that gives the player a blueprint stencil item for a specified block type and recipe, enabling rapid manual testing of the stencil placement flow without requiring Stencil Crafting integration.

## Acceptance Criteria

### Checklist
- [ ] Command `/stencil <blockTypeKey> <recipeId>` gives the player a stencil item for the specified block and recipe
- [ ] The stencil item is the actual block type (e.g., `Wall_Cobble`) with BSON metadata `{blueprint: true, recipeId: "<recipeId>"}`
- [ ] Stack count is 1
- [ ] Item is placed in the player's active hotbar slot (or first available slot)
- [ ] Invalid block type or recipe ID prints an error message

### Scenarios
**Give a stencil via command**
- **Given** the player runs `/stencil Wall_Cobble cobble_wall_recipe`
- **When** the command executes
- **Then** the player receives a `Wall_Cobble` item with stencil BSON metadata, stack count 1

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604301431 | Implement Stencil Test Command | backlog |

## Notes
- Follow the pattern of the existing `/placeblock` test command.
- This is a developer-only testing tool, not player-facing.
