---
id: S2605191121
type: story
title: "Add Raw Cost to StencilRadialMenuPage"
status: backlog
priority: medium
feature: F2605191120
epic: E2605191100
created: 2026-05-19
---

# Add Raw Cost to StencilRadialMenuPage

## User Story
As a **player**, I want **the radial menu cost arc to show the actual materials that will be consumed** so that **I know exactly what I'm spending when auto-craft is involved**.

## Acceptance Criteria

### Checklist
- [ ] When auto-craft is needed, replace intermediate ingredient slot(s) in cost arc with raw materials
- [ ] Cost arc reflects the actual consumption plan (what will be taken from inventory)
- [ ] Fast path (player has all intermediates): cost arc shows original recipe ingredients unchanged
- [ ] Mixed inventory: intermediates the player has remain visible, only deficit shown as raw materials
- [ ] Data sourced from `AutoCraftPlanner.plan()` consumption list

### Scenarios
**Auto-craft replaces intermediates in cost arc**
- **Given** recipe needs 3 bricks, player has 0 bricks, 50 cobblestone
- **When** player hovers on radial menu segment
- **Then** cost arc shows "36× Cobblestone" (not 3× Brick)

**Mixed inventory partial replacement**
- **Given** recipe needs 3 bricks, player has 1 brick, 30 cobblestone
- **When** player hovers on radial menu segment
- **Then** cost arc shows "1× Brick + 24× Cobblestone"

**Fast path unchanged**
- **Given** recipe needs 3 bricks, player has 5 bricks
- **When** player hovers on radial menu segment
- **Then** cost arc shows "3× Brick" (original, no replacement)

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.4
