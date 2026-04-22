---
id: F2604221055
type: feature
title: "Resource Consumption & Chest Scanning"
status: backlog
priority: high
epic: E2604221030
created: 2026-04-22
---

# Resource Consumption & Chest Scanning

## Description
A shared resource scanning and consumption system that checks player inventory and nearby chests within a configurable radius for recipe ingredients. Used by both the recipe filtering (to determine what the player can afford) and the placement handler (to atomically consume resources). The bench radius is configurable per-asset.

## Acceptance Criteria

### Checklist
- [ ] System scans player inventory for recipe ingredients
- [ ] System scans nearby chests within a configurable horizontal and vertical radius
- [ ] Chest scanning radius is configurable (reuses/extends the bench radius asset settings)
- [ ] `canAfford(recipe)` check aggregates across inventory and all nearby chests
- [ ] `consume(recipe)` deducts atomically from inventory first, then chests for any shortfall
- [ ] No partial consumption ever occurs — if total available < required, nothing is consumed
- [ ] System handles concurrent access safely (two players near the same chest)
- [ ] Chest scanning works with the standard Hytale chest block type

### Scenarios
**All resources in inventory**
- **Given** the recipe requires 48 cobblestone and the player has 60 in inventory
- **When** the system consumes resources
- **Then** 48 cobblestone is removed from inventory, chests are untouched

**Split across inventory and chests**
- **Given** the recipe requires 48 cobblestone, player has 30, nearby chest has 20
- **When** the system checks affordability
- **Then** it reports sufficient (30 + 20 = 50 ≥ 48)

**Insufficient total**
- **Given** the recipe requires 48 cobblestone, player has 10, nearby chests have 15
- **When** the system checks affordability
- **Then** it reports insufficient (25 < 48)

**Multi-input recipe**
- **Given** the recipe requires 24 planks AND 12 nails
- **When** the system checks affordability
- **Then** it verifies BOTH inputs are available across inventory/chests

**Chest outside radius**
- **Given** a chest exists 20 blocks away and the radius is 10
- **When** the system scans for resources
- **Then** that chest's contents are NOT included

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221155 | Nearby Chest Discovery by Radius | backlog |
| S2604221200 | Aggregate Resource Availability Check | backlog |
| S2604221205 | Atomic Multi-Source Resource Consumption | backlog |

## Notes
- The `PortableStructuralWindow` already has `chestHorizontalRadius: 0` and `chestVerticalRadius: 0` fields — these should be extended/made configurable.
- The portable bench's `PortableBenchWindow` already scans inventory for the "Craftable" tab. This system generalizes that to include chests.
- Risk: No chest change events may exist — real-time monitoring of chest contents may require polling or be limited to check-on-demand.
- Risk: Concurrent chest access (two players consuming from the same chest) needs careful handling to avoid double-spending.
- The Architect should investigate whether Hytale provides block entity APIs for reading chest inventories and whether area-scan utilities exist.
