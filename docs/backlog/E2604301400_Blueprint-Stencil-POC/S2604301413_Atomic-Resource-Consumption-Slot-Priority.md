---
id: S2604301413
type: story
title: "Atomic Resource Consumption with Slot Priority"
status: backlog
priority: high
feature: F2604301410
epic: E2604301400
created: 2026-04-30
---

# Atomic Resource Consumption with Slot Priority

## User Story
As a **player**, I want resource consumption during stencil placement to prioritize non-active-slot stacks so that my stencil item is never accidentally consumed when the placed block is also one of its own recipe ingredients.

## Acceptance Criteria

### Checklist
- [ ] When consuming resources for a stencil placement, the active hotbar slot is excluded from consumption candidates
- [ ] Resources are consumed from all other inventory slots (hotbar + storage) first
- [ ] Consumption is atomic — all materials are checked for availability before any are removed (all-or-nothing)
- [ ] If the recipe requires the same item type as the stencil block, those resources come from other stacks, not the stencil

### Scenarios
**Stencil block IS its own ingredient — other stacks consumed first**
- **Given** the player holds a `Rock_Stone_Cobble` stencil in slot 3, and has 48× `Rock_Stone_Cobble` in slot 5
- **When** the player places a cobble block (recipe: 12× `Rock_Stone_Cobble`)
- **Then** 12× `Rock_Stone_Cobble` is consumed from slot 5, the stencil in slot 3 remains untouched

**Multiple recipe ingredients**
- **Given** a recipe requires 24× `Wood_Oak_Planks` + 12× `Ingredient_Fibre`
- **When** the player places the block via stencil
- **Then** both materials are fully available before either is consumed, and both are consumed atomically

## Notes
- Reference the existing `PlaceBlockCostUtil` and `PlaceBlockToolInteraction` resource consumption patterns for the all-or-nothing approach.
- The "safe container" pattern from `design-safe-resource-consumption.md` solves the active-slot exclusion problem — consider reusing it.
