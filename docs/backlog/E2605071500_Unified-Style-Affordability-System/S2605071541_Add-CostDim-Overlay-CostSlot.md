---
id: S2605071541
type: story
title: "Add CostDim Overlay to StencilRadialCostSlot.ui"
status: backlog
priority: high
feature: F2605071540
epic: E2605071500
created: 2026-05-07
---

# Add CostDim Overlay to StencilRadialCostSlot.ui

## User Story
As a **player**, I want **unaffordable ingredients in the radial menu to be visually dimmed** so that **I can tell at a glance which materials I'm missing**.

## Acceptance Criteria

### Checklist
- [ ] StencilRadialCostSlot.ui has a #CostDim Group overlay (semi-transparent dark) inside the icon area
- [ ] #CostDim.Visible defaults to false
- [ ] Server can toggle #CostDim.Visible per cost slot
- [ ] CostQty label supports style swap between normal (gold) and insufficient (red) via Value.ref()

### Scenarios
**Dim overlay hidden by default**
- **Given** a cost slot is visible
- **When** no affordability data has been sent
- **Then** #CostDim is hidden and the icon displays normally

## Notes
- Use #000000(0.5) overlay matching the BlueprintBook CellDim pattern
- The dim Group should cover only the icon area, not the label
