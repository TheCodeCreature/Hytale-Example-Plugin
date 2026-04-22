---
id: S2604221200
type: story
title: "Aggregate Resource Availability Check"
status: backlog
priority: high
feature: F2604221055
epic: E2604221030
created: 2026-04-22
---

# Aggregate Resource Availability Check

## User Story
As a **system**, I need to **aggregate item counts across inventory and nearby chests** so that **I can determine if a recipe is affordable**.

## Acceptance Criteria

### Checklist
- [ ] `canAfford(recipe, player, chests)` returns true/false for a given recipe
- [ ] Aggregation sums item counts across player inventory and all discovered chests
- [ ] Multi-input recipes require ALL inputs to be available simultaneously
- [ ] Uses already-scaled recipe input quantities (12× economy values)
- [ ] Returns quickly — no unnecessary iteration

### Scenarios
**Sufficient across sources**
- **Given** recipe needs 48 cobblestone; player has 30, chest has 20
- **When** `canAfford()` is called
- **Then** returns true (50 ≥ 48)

**Insufficient**
- **Given** recipe needs 48 cobblestone; player has 10, chest has 10
- **When** `canAfford()` is called
- **Then** returns false (20 < 48)

**Multi-input**
- **Given** recipe needs 24 planks + 12 nails; player has 30 planks, 15 nails
- **When** `canAfford()` is called
- **Then** returns true (both satisfied)

## Notes
- This is a pure query — no side effects, no item manipulation.
- Depends on S2604221155 (Chest Discovery) for the chest list.
- Used by recipe filtering (S2604221115) and rarity indicators (S2604221145).
