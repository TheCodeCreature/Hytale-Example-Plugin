---
id: S2605071542
type: story
title: "Wire Affordability Check into showCostArc"
status: backlog
priority: high
feature: F2605071540
epic: E2605071500
created: 2026-05-07
---

# Wire Affordability Check into showCostArc

## User Story
As a **player**, I want **the radial menu cost arc to show whether I can afford each ingredient** so that **I know before selecting a stencil if I have enough materials**.

## Acceptance Criteria

### Checklist
- [ ] showCostArc() uses RecipeAffordabilityResolver.resolveIngredientCosts() to get per-ingredient affordability
- [ ] For each cost slot: cmd.set("#CostSlots[j] #CostDim.Visible", !sufficient)
- [ ] For each cost slot: cmd.set("#CostSlots[j] #CostQty.Style", sufficient ? COST_QTY_OVERLAY : COST_QTY_INSUFFICIENT)
- [ ] Value.ref() constants added to StencilRadialMenuPage.java for affordable/unaffordable cost quantity styles
- [ ] Player inventory is accessed via the same scope as Contract #11 (storage + backpack + non-active hotbar)

### Scenarios
**Mixed affordability display**
- **Given** a recipe with 3 ingredients, player can afford 2 of them
- **When** the player hovers the segment
- **Then** 2 cost slots show normal appearance, 1 shows dimmed with red quantity text

## Notes
- Depends on S2605071531 (RecipeAffordabilityResolver exists)
- Depends on S2605071541 (#CostDim overlay exists in the .ui template)
- Need to pass player's container to showCostArc — currently the method has no access to inventory
