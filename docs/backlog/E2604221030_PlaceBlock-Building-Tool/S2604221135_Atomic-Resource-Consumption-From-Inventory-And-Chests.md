---
id: S2604221135
type: story
title: "Atomic Resource Consumption from Inventory"
status: backlog
priority: high
feature: F2604221045
epic: E2604221030
created: 2026-04-22
---

# Atomic Resource Consumption from Inventory

## User Story
As a **player**, I want **resources to be consumed reliably from my inventory when I place a block** so that **I never lose items without a block being placed, or place a block without losing items**.

## Acceptance Criteria

### Checklist
- [ ] Consumption checks all recipe inputs before deducting anything
- [ ] If any input is insufficient in the player's inventory, NO items are consumed and placement is cancelled
- [ ] Consumption is atomic — either all inputs are fully consumed or none are
- [ ] Multi-input recipes consume all inputs in one operation
- [ ] Consumed quantities match the already-scaled recipe inputs (12× economy)
- [ ] Items consumed from storage + hotbar + backpack (excluding armor, utility, tools)

### Scenarios
**Atomic success**
- **Given** recipe needs 24 planks + 12 nails, player has both in inventory
- **When** placement succeeds
- **Then** exactly 24 planks and 12 nails are removed

**Atomic failure**
- **Given** recipe needs 24 planks + 12 nails, player has planks but only 6 nails
- **When** placement is attempted
- **Then** NO planks and NO nails are removed, placement is cancelled with feedback message

## Notes
- No chest scanning — inventory only.
- Use `CombinedItemContainer` (backpack + storage + hotbar) for availability check and removal.
- The existing `canRemoveMaterials` / `removeMaterials` APIs on `ItemContainer` may provide atomicity natively.
