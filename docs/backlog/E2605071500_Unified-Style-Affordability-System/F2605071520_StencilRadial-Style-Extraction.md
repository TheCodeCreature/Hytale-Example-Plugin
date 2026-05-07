---
id: F2605071520
type: feature
title: "StencilRadial Style Extraction"
status: backlog
priority: high
epic: E2605071500
created: 2026-05-07
---

# StencilRadial Style Extraction

## Description
Replace all inline styles in StencilRadial .ui files with references to SharedStyles.ui (for universal tokens) and a new StencilRadialStyles.ui (for radial-specific tokens). Add Value.ref() constants to StencilRadialMenuPage.java for server-driven style swaps.

## Acceptance Criteria

### Checklist
- [ ] StencilRadialStyles.ui created with radial-specific tokens (@RadialSegmentButtonStyle, @CostQuantityOverlayStyle, @OverlayCostNameStyle)
- [ ] StencilRadialMenu.ui: CloseBtn, PrevBtn, NextBtn, PageLabel use $S or $Shared references instead of inline styles
- [ ] StencilRadialSegment.ui: SegBtn and SegLabelText use style references instead of inline definitions
- [ ] StencilRadialCostSlot.ui: CostQty and CostName use style references instead of inline definitions
- [ ] No visual regression in the radial menu appearance
- [ ] Server starts without UI parse errors

### Scenarios
**Radial menu looks identical after extraction**
- **Given** all inline styles have been replaced with $S references
- **When** a player opens the stencil radial menu
- **Then** segment buttons, labels, cost icons, close button, and nav buttons render identically to before

**New style tokens are reusable**
- **Given** @RadialSegmentButtonStyle is defined in StencilRadialStyles.ui
- **When** a future UI element needs the same hover/pressed behavior
- **Then** it can reference $S.@RadialSegmentButtonStyle without copying the inline definition

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605071521 | Create StencilRadialStyles.ui | backlog |
| S2605071522 | Replace Inline Styles in Radial UI Files | backlog |

## Notes
- 5 new tokens needed: @RadialSegmentButtonStyle, @DestructiveButtonStyle (shared), @NavButtonStyle (shared), @CostQuantityOverlayStyle, @OverlayCostNameStyle
- PrevBtn/NextBtn duplicate exact same style — single @NavButtonStyle eliminates this
