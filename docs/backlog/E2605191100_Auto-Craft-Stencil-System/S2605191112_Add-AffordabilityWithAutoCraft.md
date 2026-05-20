---
id: S2605191112
type: story
title: "Add isAffordableWithAutoCraft to RecipeAffordabilityResolver"
status: backlog
priority: high
feature: F2605191110
epic: E2605191100
created: 2026-05-19
---

# Add isAffordableWithAutoCraft to RecipeAffordabilityResolver

## User Story
As a **visual indicator system**, I want **a single method that checks recipe affordability including auto-craft** so that **the green/red glow correctly reflects what the player can actually place**.

## Acceptance Criteria

### Checklist
- [ ] New static method `isAffordableWithAutoCraft(CraftingRecipe, BenchCategory, CombinedItemContainer)`
- [ ] Delegates to `AutoCraftPlanner.plan()` and returns `plan.affordable()`
- [ ] Returns true when player can afford directly OR via auto-craft
- [ ] Returns false only when neither path works
- [ ] Existing `isAffordable()` method unchanged (backward compatible)

### Scenarios
**Affordable via auto-craft**
- **Given** recipe needs 3 bricks, player has 0 bricks, 50 cobblestone
- **When** `isAffordableWithAutoCraft()` is called
- **Then** returns true

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.2
