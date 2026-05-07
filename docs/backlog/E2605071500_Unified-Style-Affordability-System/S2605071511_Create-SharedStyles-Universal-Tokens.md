---
id: S2605071511
type: story
title: "Create SharedStyles.ui with Universal Tokens"
status: backlog
priority: high
feature: F2605071510
epic: E2605071500
created: 2026-05-07
---

# Create SharedStyles.ui with Universal Tokens

## User Story
As a **plugin developer**, I want **a single shared style file** so that **all UI subsystems use consistent visual tokens for buttons, labels, and affordability states**.

## Acceptance Criteria

### Checklist
- [ ] SharedStyles.ui created at `Common/UI/Custom/SharedStyles.ui`
- [ ] Contains universal button styles: @TertiaryButtonStyle, @DestructiveButtonStyle, @NavButtonStyle
- [ ] Contains universal label styles: @OverlayLabelStyle (bold + outline), @OverlayCostNameStyle, @SubtextStyle, @HeaderStyle, @DetailLabelStyle, @DetailLabelMutedStyle, @SectionLabelStyle
- [ ] Contains affordability color tokens: @CostQuantityStyle, @CostQuantityInsufficientStyle, @CostQuantityOverlayStyle
- [ ] Contains filter state styles: @FilterActiveStyle, @FilterInactiveStyle
- [ ] Contains utility styles: @TransparentButtonStyle
- [ ] File parses without errors on server startup (no HorizontalAlignment: Right, etc.)

### Scenarios
**File loads without parse error**
- **Given** SharedStyles.ui is placed in the Custom directory
- **When** the server starts
- **Then** no "Failed to load custom UI documents" error appears in logs

## Notes
- Reference BlueprintBenchStyles.ui for existing token definitions
- New overlay tokens need OutlineColor: #000000 and RenderBold: true
- @CostQuantityOverlayStyle is gold (#ffcc00) variant of @CostQuantityStyle for floating overlay context
