---
id: S2604221135
type: story
title: "Atomic Resource Consumption from Inventory & Chests"
status: backlog
priority: high
feature: F2604221045
epic: E2604221030
created: 2026-04-22
---

# Atomic Resource Consumption from Inventory & Chests

## User Story
As a **player**, I want **resources to be consumed reliably when I place a block** so that **I never lose items without a block being placed, or place a block without losing items**.

## Acceptance Criteria

### Checklist
- [ ] Consumption checks all recipe inputs before deducting anything
- [ ] If any input is insufficient (across inventory + chests), NO items are consumed
- [ ] Inventory items are consumed first before drawing from chests
- [ ] Consumption is atomic — either all inputs are fully consumed or none are
- [ ] Multi-input recipes consume all inputs in one operation
- [ ] Consumed quantities match the already-scaled recipe inputs (12× economy)
- [ ] System handles edge cases: items in hand, items in armor slots (excluded?)

### Scenarios
**Atomic success**
- **Given** recipe needs 24 planks + 12 nails, player has both
- **When** placement succeeds
- **Then** exactly 24 planks and 12 nails are removed

**Atomic failure**
- **Given** recipe needs 24 planks + 12 nails, player has planks but only 6 nails
- **When** placement is attempted
- **Then** NO planks and NO nails are removed

**Split across sources**
- **Given** recipe needs 48 cobblestone, player has 30, nearby chest has 30
- **When** placement succeeds
- **Then** 30 from inventory + 18 from chest are removed

## Notes
- This story depends on S2604221110 (Resource Scanner) for discovering available resources.
- The consumption order (inventory first, then nearest chest, etc.) should be deterministic.
- Risk: Concurrent access to chests by multiple players could cause race conditions. The Architect should determine if locking is needed.
