---
id: S2604221200
type: story
title: "Inventory Resource Availability Check"
status: backlog
priority: high
feature: F2604221055
epic: E2604221030
created: 2026-04-22
---

# Inventory Resource Availability Check

## User Story
As a **system**, I need to **check if a player's inventory has sufficient resources for a recipe** so that **I can determine if a recipe is affordable**.

## Acceptance Criteria

### Checklist
- [ ] `canAfford(recipe, player)` returns true/false for a given recipe
- [ ] Checks item counts across player inventory (storage + hotbar + backpack)
- [ ] Multi-input recipes require ALL inputs to be available simultaneously
- [ ] Uses already-scaled recipe input quantities (12× economy values)
- [ ] Returns quickly — no unnecessary iteration

### Scenarios
**Sufficient**
- **Given** recipe needs 48 cobblestone; player has 50
- **When** `canAfford()` is called
- **Then** returns true

**Insufficient**
- **Given** recipe needs 48 cobblestone; player has 20
- **When** `canAfford()` is called
- **Then** returns false

**Multi-input**
- **Given** recipe needs 24 planks + 12 nails; player has 30 planks, 15 nails
- **When** `canAfford()` is called
- **Then** returns true (both satisfied)

## Notes
- Inventory only — no chest scanning.
- Use `CombinedItemContainer` with `canRemoveMaterials()` — this may already provide exactly what we need.
- Used by recipe affordability display in StencilSelectionPage (already done) and by PlaceBlockPlacementSystem for placement gating.
