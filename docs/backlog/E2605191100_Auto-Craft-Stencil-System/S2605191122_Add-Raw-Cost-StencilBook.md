---
id: S2605191122
type: story
title: "Add Raw Cost to StencilSelectionPage"
status: backlog
priority: medium
feature: F2605191120
epic: E2605191100
created: 2026-05-19
---

# Add Raw Cost to StencilSelectionPage

## User Story
As a **player**, I want **the stencil bench recipe list to show raw material costs** so that **I can compare recipes by their true cost**.

## Acceptance Criteria

### Checklist
- [ ] When player can't afford direct intermediates, replace unaffordable intermediate slots with raw materials
- [ ] Grid reflects the actual consumption plan (what will be taken from inventory)
- [ ] Fast path (player has all intermediates): grid shows original recipe ingredients unchanged
- [ ] Data sourced from `AutoCraftPlanner.plan()` consumption list

### Scenarios
**Auto-craft replaces intermediates**
- **Given** brick stairs recipe needs 3 bricks + 2 cobblestone, player has 0 bricks, 50 cobblestone
- **When** player views the recipe
- **Then** grid shows "38× Cobblestone" (36 auto-craft + 2 direct), NOT 3× Brick + 2× Cobblestone

**Fast path unchanged**
- **Given** recipe needs 3 bricks, player has 5 bricks
- **When** player views the recipe
- **Then** grid shows "3× Brick" (original recipe, no replacement)

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.5
