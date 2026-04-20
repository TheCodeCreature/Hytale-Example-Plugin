---
id: F2604201425
type: feature
title: "Quality State Management"
status: backlog
priority: high
epic: E2604201400
created: 2026-04-20
---

# Quality State Management

## Description
The system that swaps PlaceBlock items between three quality variants (Default/Armed/NoResources) based on recipe assignment and resource availability. Includes the quality swapper utility and the inventory monitor that triggers re-evaluation when inventory contents change.

## Acceptance Criteria

### Checklist
- [ ] PlaceBlockQualitySwapper correctly swaps between all three variants
- [ ] Metadata (recipe ID, recipe name) is preserved across variant swaps
- [ ] Quality re-evaluates after every block placement
- [ ] Quality re-evaluates when inventory contents change (pickup, drop, trade, craft)
- [ ] Inventory monitor registers per-player change listeners when PlaceBlock is equipped
- [ ] Inventory monitor unregisters listeners when PlaceBlock is unequipped or player disconnects
- [ ] No stale quality states — green always means resources available, red always means insufficient

### Scenarios
**Resources depleted during placement**
- **Given** a player has PlaceBlock_Armed and exactly 48 Wood_Oak_Plank (enough for 1 placement)
- **When** they place one Oak Planks block
- **Then** the PlaceBlock swaps to PlaceBlock_NoResources (red) immediately after placement

**Resources restored by pickup**
- **Given** a player has PlaceBlock_NoResources with recipe "Oak_Planks"
- **When** they pick up 48 Wood_Oak_Plank
- **Then** the PlaceBlock swaps to PlaceBlock_Armed (green)

**Recipe cleared**
- **Given** a player has PlaceBlock_Armed
- **When** the recipe is cleared via F-key menu
- **Then** the PlaceBlock swaps to PlaceBlock_Default (blue) and metadata is removed

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604201500 | Implement PlaceBlockQualitySwapper | backlog |
| S2604201505 | Implement PlaceBlockInventoryMonitor | backlog |

## Notes
- Quality swapper skeleton exists at `PlaceBlockQualitySwapper.java`
- Monitor uses `container.registerChangeEvent()` pattern from PortableBenchWindow
- Must handle edge case: multiple PlaceBlocks in hotbar with different recipes
