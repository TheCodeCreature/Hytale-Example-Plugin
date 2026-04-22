---
id: S2604221110
type: story
title: "Inventory & Chest Resource Scanner"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-04-22
---

# Inventory & Chest Resource Scanner

## User Story
As a **player**, I want the **bench to check my inventory and nearby chests for available resources** so that **I can see which recipes I can afford to build**.

## Acceptance Criteria

### Checklist
- [ ] Scanner aggregates item counts from player inventory
- [ ] Scanner discovers chests within the configurable bench radius
- [ ] Scanner aggregates item counts from discovered chests
- [ ] Scanner returns a combined availability map (itemId → totalCount)
- [ ] Scanner respects horizontal and vertical radius settings independently
- [ ] Scanner handles the case where no chests are nearby (inventory-only)

### Scenarios
**Inventory-only scan**
- **Given** no chests are within radius
- **When** the scanner runs
- **Then** it returns counts from player inventory only

**Inventory + chest scan**
- **Given** a chest with 20 cobblestone is within radius
- **When** the scanner runs
- **Then** cobblestone count includes both inventory and chest amounts

## Notes
- Reuse patterns from `PortableBenchWindow`'s inventory scanning.
- The Hytale Expert should be consulted on chest block entity APIs and area-scan utilities.
- This scanner is shared between recipe filtering (Feature F2604221040) and resource consumption (Feature F2604221055).
