---
id: F2605071510
type: feature
title: "Plugin-Wide Shared Style Tokens"
status: backlog
priority: high
epic: E2605071500
created: 2026-05-07
---

# Plugin-Wide Shared Style Tokens

## Description
Create a SharedStyles.ui file containing universal style tokens that apply across all UI subsystems. BlueprintBookStyles.ui imports it and re-exports shared tokens so existing Value.ref() paths remain valid. This establishes the style hierarchy: SharedStyles.ui (universal) → per-subsystem files (specific).

## Acceptance Criteria

### Checklist
- [ ] SharedStyles.ui exists at `Common/UI/Custom/SharedStyles.ui` with universal tokens
- [ ] Tokens include: button base styles (tertiary, destructive, navigation), label foundations (overlay, subtext, header), affordability colors (normal quantity, insufficient quantity, affordable, unaffordable)
- [ ] BlueprintBookStyles.ui imports SharedStyles.ui and re-exports shared tokens via alias
- [ ] All 8 Value.ref() paths in BlueprintSelectionPage.java continue to resolve correctly (no silent breakage)
- [ ] No visual regression in BlueprintBook UI

### Scenarios
**Existing BlueprintBook styles still work**
- **Given** BlueprintBookStyles.ui now imports SharedStyles.ui
- **When** a player opens the Blueprint Bench
- **Then** all filter buttons, recipe entries, cost cells, and detail panel display identically to before

**SharedStyles.ui is importable by new subsystems**
- **Given** a new .ui file imports SharedStyles.ui via `$Shared = "../../SharedStyles.ui"`
- **When** the UI engine loads the file
- **Then** all shared style tokens resolve correctly

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605071511 | Create SharedStyles.ui with Universal Tokens | backlog |
| S2605071512 | Migrate BlueprintBookStyles.ui to Import Shared | backlog |

## Notes
- Shared tokens identified by Code Reviewer: @CostQuantityStyle, @CostQuantityInsufficientStyle, @FilterActiveStyle, @FilterInactiveStyle, @TransparentButtonStyle, @DetailLabelStyle, @DetailLabelMutedStyle, @HeaderStyle, @SubtextStyle, @SectionLabelStyle
- New universal tokens needed: @DestructiveButtonStyle, @NavButtonStyle, @OverlayLabelStyle
- Risk: Value.ref() paths are string-based runtime lookups with no compile-time check
