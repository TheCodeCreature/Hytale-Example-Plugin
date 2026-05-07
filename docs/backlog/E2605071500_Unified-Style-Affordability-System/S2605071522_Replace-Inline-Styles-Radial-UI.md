---
id: S2605071522
type: story
title: "Replace Inline Styles in Radial UI Files"
status: backlog
priority: high
feature: F2605071520
epic: E2605071500
created: 2026-05-07
---

# Replace Inline Styles in Radial UI Files

## User Story
As a **plugin developer**, I want **all inline styles in StencilRadial .ui files replaced with $S references** so that **style changes are made in one place, not scattered across 3 files**.

## Acceptance Criteria

### Checklist
- [ ] StencilRadialMenu.ui: CloseBtn uses $Shared.@DestructiveButtonStyle
- [ ] StencilRadialMenu.ui: PrevBtn and NextBtn both use $Shared.@NavButtonStyle (eliminating duplication)
- [ ] StencilRadialMenu.ui: PageLabel uses $Shared.@SubtextStyle or appropriate shared label style
- [ ] StencilRadialSegment.ui: SegBtn uses $S.@RadialSegmentButtonStyle
- [ ] StencilRadialSegment.ui: SegLabelText uses $Shared.@OverlayLabelStyle
- [ ] StencilRadialCostSlot.ui: CostQty uses $Shared.@CostQuantityOverlayStyle
- [ ] StencilRadialCostSlot.ui: CostName uses $Shared.@OverlayCostNameStyle
- [ ] Each .ui file adds appropriate $S and/or $Shared import lines
- [ ] No visual change in the rendered radial menu

### Scenarios
**PrevBtn/NextBtn deduplication**
- **Given** PrevBtn and NextBtn previously had identical inline TextButtonStyle blocks
- **When** both now reference $Shared.@NavButtonStyle
- **Then** they render identically and future style changes apply to both automatically

## Notes
- 3 files modified: StencilRadialMenu.ui, StencilRadialSegment.ui, StencilRadialCostSlot.ui
- Verify by opening radial menu and checking all visual states (default, hover, pressed) on every interactive element
