---
id: F2605071540
type: feature
title: "Radial Cost Arc Affordability Feedback"
status: backlog
priority: high
epic: E2605071500
created: 2026-05-07
---

# Radial Cost Arc Affordability Feedback

## Description
Add per-ingredient affordability visual feedback to the stencil radial menu's cost arc. When hovering a segment, cost icons for ingredients the player cannot afford should be visually dimmed or tinted, giving at-a-glance "can I afford this?" feedback consistent with the hotbar glow system.

## Acceptance Criteria

### Checklist
- [ ] Cost slots show dim overlay when the player has insufficient quantity of that ingredient
- [ ] Cost quantity text changes from gold to red for insufficient ingredients
- [ ] Affordable ingredients display normally (no dim, gold text)
- [ ] Affordability is checked using the shared RecipeAffordabilityResolver
- [ ] Players can still select (arm) stencils they cannot afford — dimming is informational, not blocking
- [ ] Visual treatment is adapted for the radial context (compact, at-a-glance) — not a clone of the BlueprintBook detail panel

### Scenarios
**Player can afford all ingredients**
- **Given** a recipe requiring 24x Wood_Planks and 12x Ingredient_Fibre
- **And** the player has 30x Wood_Planks and 20x Ingredient_Fibre
- **When** the player hovers the segment
- **Then** both cost slots display with normal appearance (no dim, gold quantity text)

**Player cannot afford one ingredient**
- **Given** a recipe requiring 24x Wood_Planks and 12x Ingredient_Fibre
- **And** the player has 30x Wood_Planks but only 4x Ingredient_Fibre
- **When** the player hovers the segment
- **Then** Wood_Planks cost slot displays normally; Ingredient_Fibre cost slot is dimmed with red quantity text

**Player cannot afford any ingredients**
- **Given** a recipe requiring 24x Wood_Planks and 12x Ingredient_Fibre
- **And** the player has 0 of both
- **When** the player hovers the segment
- **Then** both cost slots are dimmed with red quantity text

**Selection is not blocked by unaffordability**
- **Given** a stencil the player cannot afford
- **When** the player clicks the segment
- **Then** the stencil is armed on the hotbar (selection proceeds normally)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605071541 | Add CostDim Overlay to StencilRadialCostSlot.ui | backlog |
| S2605071542 | Wire Affordability Check into showCostArc | backlog |

## Notes
- PO recommendation: tint or dim the cost icon, don't replicate full BlueprintBook treatment. Keep it "at a glance."
- PO concern: affordability check must use same inventory scope as Contract #11 (storage + backpack + non-active hotbar)
- Depends on F2605071530 (RecipeAffordabilityResolver) for the shared affordability check
