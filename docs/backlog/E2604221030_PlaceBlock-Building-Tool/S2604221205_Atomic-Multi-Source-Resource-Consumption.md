---
id: S2604221205
type: story
title: "Atomic Multi-Source Resource Consumption"
status: cancelled
priority: high
feature: F2604221055
epic: E2604221030
created: 2026-04-22
---

# Atomic Multi-Source Resource Consumption

## User Story
As a **system**, I need to **consume recipe inputs atomically from inventory and chests** so that **resources are never partially consumed on a failed placement**.

## Acceptance Criteria

### Checklist
- [ ] `consume(recipe, player, chests)` removes items atomically
- [ ] Inventory items are consumed first, then chest items for any shortfall
- [ ] If insufficient resources exist, nothing is consumed (all-or-nothing)
- [ ] Multi-input recipes consume all inputs in a single atomic operation
- [ ] Returns success/failure to the caller
- [ ] Safe under concurrent access (two players near the same chest)

### Scenarios
**Full consumption from inventory**
- **Given** recipe needs 48 cobblestone; player has 60
- **When** `consume()` runs
- **Then** 48 removed from inventory, chests untouched

**Split consumption**
- **Given** recipe needs 48 cobblestone; player has 30, chest has 30
- **When** `consume()` runs
- **Then** 30 from inventory + 18 from chest removed

**Failed consumption**
- **Given** recipe needs 48 cobblestone; total available is 25
- **When** `consume()` runs
- **Then** returns failure, no items removed anywhere

## Notes
- Atomicity approach: pre-check total availability, then consume. If the engine doesn't support transactional inventory operations, consume from inventory first and track consumed amounts for rollback if chest consumption fails.
- The Architect should determine if Hytale provides transactional inventory APIs or if manual rollback logic is needed.
