---
id: F2605191120
type: feature
title: "UI Raw Cost Display"
status: backlog
priority: medium
epic: E2605191100
created: 2026-05-19
---

# UI Raw Cost Display

## Description
Display raw material cost breakdowns alongside direct recipe costs in the stencil radial menu and stencil bench selection page. Players can see what auto-craft would consume before placing.

## Acceptance Criteria

### Checklist
- [ ] Radial menu shows raw material cost for armed stencil recipe
- [ ] Stencil bench selection page shows raw material cost per recipe
- [ ] Raw cost only shown for recipes with crafted intermediate inputs
- [ ] Raw cost not shown for recipes with only raw inputs (no noise)

### Scenarios
**Radial menu raw cost display**
- **Given** player opens radial menu, stencil armed with brick stairs (3 bricks = 36 cobblestone)
- **When** radial menu renders
- **Then** shows "3× Brick" (direct) and "36× Cobblestone" (raw cost)

**Simple recipe — no raw cost line**
- **Given** recipe is stone slab (only raw cobblestone input)
- **When** displayed in radial menu
- **Then** only shows "6× Cobblestone" — no raw cost line (it would be identical)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605191121 | Add Raw Cost to StencilRadialMenuPage | backlog |
| S2605191122 | Add Raw Cost to StencilSelectionPage | backlog |

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.4, §8.5
Open question: exact layout for raw cost display in radial menu — may need design iteration
