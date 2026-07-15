---
id: S2604240900
type: story
title: "PlaceBlock Command for Testing"
status: in-progress
priority: critical
feature: F2604221040
epic: E2604221030
created: 2026-04-24
---

# PlaceBlock Command for Testing

## User Story
As a **player**, I want **a `/placeblock` command to assign, clear, list, and inspect recipes on my placeholder** so that **I can test the arming pipeline while the custom UI is being developed**.

## Acceptance Criteria

### Checklist
- [ ] `/placeblock assign <recipeId>` arms the held Block_Placeholder with the specified recipe
- [ ] `/placeblock clear` disarms the held Block_Placeholder (reverts to Blue quality)
- [ ] `/placeblock list` shows available placeable recipes (shadow recipe originals)
- [ ] `/placeblock info` shows the current armed state of the held placeholder
- [ ] Assigning swaps the placeholder from Blue → Green quality
- [ ] Clearing swaps the placeholder from Green/Red → Blue quality
- [ ] Only works when holding a Block_Placeholder item
- [ ] Non-block recipes are rejected with feedback
- [ ] Command registered in plugin setup

### Scenarios
**Assign a recipe**
- **Given** the player holds a Blue Block_Placeholder
- **When** they run `/placeblock assign Rock_Stone_Cobble_Wall`
- **Then** the placeholder becomes Green, metadata contains the recipe ID and block type

**Clear an armed placeholder**
- **Given** the player holds a Green Block_Placeholder armed with a recipe
- **When** they run `/placeblock clear`
- **Then** the placeholder becomes Blue, metadata is cleared

**Inspect armed state**
- **Given** the player holds an armed placeholder
- **When** they run `/placeblock info`
- **Then** they see the recipe ID and target block type in chat

**List available recipes**
- **Given** shadow recipes have been registered
- **When** the player runs `/placeblock list`
- **Then** they see a list of original recipe IDs that can be assigned

## Notes
- This is a temporary testing tool — will be superseded by the custom Stencil Crafting UI (Phase 2b)
- Uses existing `PlaceBlockMetadata` for all state operations
- Resolves original recipe IDs from shadow recipes via `StencilBookRecipeMutator.getOriginalRecipeId()`
