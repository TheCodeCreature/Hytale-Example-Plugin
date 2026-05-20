---
id: S2605191117
type: story
title: "Update StencilVisualManager for Auto-Craft Affordability"
status: backlog
priority: high
feature: F2605191115
epic: E2605191100
created: 2026-05-19
---

# Update StencilVisualManager for Auto-Craft Affordability

## User Story
As a **player**, I want **the stencil glow to show green when I have enough raw materials to auto-craft** so that **I know I can place even without pre-crafted intermediates**.

## Acceptance Criteria

### Checklist
- [ ] `scanAndSend()` uses `isAffordableWithAutoCraft()` instead of `isAffordable()`
- [ ] Green glow when direct ingredients OR auto-craft raw materials are available
- [ ] Red glow only when neither path is affordable

### Scenarios
**Green with auto-craft**
- **Given** stencil armed with brick stairs, player has 0 bricks, 50 cobblestone
- **When** visual indicator refreshes
- **Then** glow is green

## Notes
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §8.3
