---
id: S2604301431
type: story
title: "Implement Stencil Test Command"
status: backlog
priority: high
feature: F2604301430
epic: E2604301400
created: 2026-04-30
---

# Implement Stencil Test Command

## User Story
As a **developer**, I want a `/stencil` command that gives me a blueprint stencil item so that I can test the stencil placement flow without needing the Stencil Crafting UI.

## Acceptance Criteria

### Checklist
- [ ] Command is registered in the plugin's command handler
- [ ] Accepts two arguments: block type key and recipe ID
- [ ] Creates the stencil via `StencilMetadata.createStencil()` and adds it to the player's inventory
- [ ] Validates that the block type key resolves to a valid block type
- [ ] Validates that the recipe ID resolves to a valid recipe
- [ ] Prints success or error feedback to the player

### Scenarios
**Valid stencil creation**
- **Given** the player runs `/stencil Wall_Cobble cobble_wall_recipe`
- **When** both arguments resolve to valid assets
- **Then** the player receives the stencil item and sees "Stencil armed: Wall_Cobble"

**Invalid block type**
- **Given** the player runs `/stencil NotARealBlock some_recipe`
- **When** the block type cannot be resolved
- **Then** the player sees an error message

## Notes
- Follow the existing command registration pattern in the plugin.
