---
id: S2604201505
type: story
title: "Implement PlaceBlockInventoryMonitor"
status: backlog
priority: high
feature: F2604201425
epic: E2604201400
created: 2026-04-20
---

# Implement PlaceBlockInventoryMonitor

## User Story
As a **player**, I want **my PlaceBlock to update its color when I pick up or use resources** so that **the tool state is never stale**.

## Acceptance Criteria

### Checklist
- [ ] Registers container.registerChangeEvent() when player equips a PlaceBlock
- [ ] Unregisters listener when player switches away or disconnects
- [ ] On inventory change, calls PlaceBlockQualitySwapper.evaluateAndSwap()
- [ ] Handles multiple PlaceBlocks in different hotbar slots independently
- [ ] No performance impact when player has no PlaceBlock equipped

### Scenarios
**Resource pickup triggers green**
- **Given** a player holds PlaceBlock_NoResources with recipe "Oak_Planks"
- **When** they pick up enough Wood_Oak_Plank
- **Then** the PlaceBlock swaps to PlaceBlock_Armed (green)

## Notes
- Uses same change event pattern as PortableBenchWindow's Craftable tab
- Start with tick-based approach for reliability, optimize to event-based later if needed
