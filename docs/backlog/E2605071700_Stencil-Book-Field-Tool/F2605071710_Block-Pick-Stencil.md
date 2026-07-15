---
id: F2605071710
type: feature
title: "Block Pick → Stencil (middle-click)"
status: backlog
priority: high
epic: E2605071700
created: 2026-05-07
---

# Block Pick → Stencil (middle-click)

## Description
When holding the Stencil Book, middle-clicking on a placed block resolves it to a crafting recipe and gives the player the corresponding stencil item — replacing the normal "pick block" creative-mode behavior.

## Acceptance Criteria

### Checklist
- [ ] Middle-click on a crafted block gives the stencil for that block's recipe
- [ ] Middle-click on a natural block (no recipe) does nothing
- [ ] Middle-click on an unencountered recipe does nothing (encounter gate)
- [ ] Stencil is placed in the active hotbar slot
- [ ] The produced stencil is identical to one obtained from the Stencil Crafting

### Scenarios
**Pick a crafted block**
- **Given** player holds Stencil Book, aims at a Hardwood Planks block, recipe is encountered
- **When** player middle-clicks
- **Then** a stencil tagged with the Hardwood Planks recipe appears in their hotbar

**Pick a natural block**
- **Given** player holds Stencil Book, aims at a Dirt block
- **When** player middle-clicks
- **Then** nothing happens (no stencil, no error)

**Pick unencountered recipe**
- **Given** player holds Stencil Book, aims at a block whose recipe they haven't encountered
- **When** player middle-clicks
- **Then** nothing happens (encounter gate blocks it)

## Notes
- Reuses existing `StencilRadialInputListener` packet intercept pattern
- Uses `BenchRecipeRegistry.getRecipeForBlock(blockTypeId)` for resolution
- Uses `StencilMetadata.createStencil(outputItemId, recipeId)` for stencil creation
