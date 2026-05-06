---
id: S2605051410
type: story
title: "Per-Ingredient Cost Cell Affordability"
status: backlog
priority: high
feature: F2605051400
epic: E2604221030
created: 2026-05-05
---

# Per-Ingredient Cost Cell Affordability

## User Story
As a **player**, I want **each ingredient in the cost grid to show whether I have enough of it** so that **I can see exactly which materials I'm missing**.

## Acceptance Criteria

### Checklist
- [ ] A `#CostDim` overlay group is added to CostCell.ui (similar to RecipeIconCell's `#CellDim`)
- [ ] A new `@CostQuantityUnaffordableStyle` LabelStyle is defined with red text color
- [ ] When the player has enough of an ingredient, quantity text is white and no dim overlay is shown
- [ ] When the player does NOT have enough, quantity text turns red and a dim overlay appears
- [ ] Per-ingredient checks use the same inventory container as the global affordability check
- [ ] The cost cell state updates when a recipe is selected or filters change

### Scenarios
**Player has all ingredients**
- **Given** the player has 12 Red Dye and 12 Wool in inventory
- **When** they select "Cloth Block Wool Red" which costs x12 Red Dye + x12 Wool
- **Then** both cost cells show white "x12" text with no dim overlay

**Player is missing one ingredient**
- **Given** the player has 12 Wool but only 3 Red Dye
- **When** they select "Cloth Block Wool Red"
- **Then** the Red Dye cost cell shows red "x12" text with a dim overlay, and the Wool cell shows white "x12" with no dim

## Notes
- The per-ingredient check compares `container.getItemCount(itemId)` against the required quantity
- This is more granular than the existing `isAffordable()` which only checks if the entire recipe can be crafted
